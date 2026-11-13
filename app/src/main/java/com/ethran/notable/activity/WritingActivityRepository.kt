package com.ethran.notable.activity

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ethran.notable.data.db.Kv
import com.ethran.notable.data.db.KvDao
import com.ethran.notable.data.db.StrokeDao
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A cheap fingerprint of the stroke table. */
data class StrokeSignature(val maxCreated: Long, val total: Int)

/** Where the strokes are counted. The Room-backed one runs SQL; tests use a fake. */
interface ActivitySource {
    suspend fun signature(): StrokeSignature
    /** "yyyy-MM-dd" (local day) to number of strokes written that day, for strokes created at or after [sinceMs]. */
    suspend fun dayCounts(sinceMs: Long): Map<String, Int>
}

/** Where the daily-max snapshot is kept (the Kv table in the app). */
interface ActivityStore {
    suspend fun read(): String?
    suspend fun write(json: String)
}

/** The period the user picked, remembered across app starts. */
interface ActivityPrefs {
    fun period(): ActivityPeriod?
    fun savePeriod(period: ActivityPeriod)
}

@Singleton
class RoomActivitySource @Inject constructor(private val dao: StrokeDao) : ActivitySource {
    override suspend fun signature(): StrokeSignature =
        dao.strokeSignature().let { StrokeSignature(it.maxCreated ?: 0L, it.total) }

    override suspend fun dayCounts(sinceMs: Long): Map<String, Int> =
        dao.strokesPerDay(sinceMs).mapNotNull { row -> row.day?.let { it to row.n } }.toMap()
}

@Singleton
class KvActivityStore @Inject constructor(private val dao: KvDao) : ActivityStore {
    override suspend fun read(): String? = dao.get(WritingActivityRepository.KEY)?.value
    override suspend fun write(json: String) = dao.set(Kv(WritingActivityRepository.KEY, json))
}

@Singleton
class SharedActivityPrefs @Inject constructor(@ApplicationContext context: Context) : ActivityPrefs {
    private val prefs: SharedPreferences = context.getSharedPreferences("writing_activity", Context.MODE_PRIVATE)
    override fun period(): ActivityPeriod? = ActivityPeriod.parse(prefs.getString("period", null))
    override fun savePeriod(period: ActivityPeriod) { prefs.edit().putString("period", period.key).apply() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WritingActivityModule {
    @Binds abstract fun bindSource(impl: RoomActivitySource): ActivitySource
    @Binds abstract fun bindStore(impl: KvActivityStore): ActivityStore
    @Binds abstract fun bindPrefs(impl: SharedActivityPrefs): ActivityPrefs
}

/**
 * Strokes per local day, for the whole history. Counts come from the Stroke table, merged (max per day) with a
 * snapshot kept in the Kv table, so deleting notes or erasing strokes never shrinks the history. The table is only
 * counted again when its signature (newest stroke, number of strokes) changed; otherwise the memory copy answers.
 * Every answer is also published to [ActivityWritingDays], the in-memory snapshot of the planner dots.
 * Everything runs on the IO dispatcher.
 */
@Singleton
class WritingActivityRepository internal constructor(
    private val source: ActivitySource,
    private val store: ActivityStore,
    private val io: CoroutineDispatcher,
    private val publish: (Map<LocalDate, Int>) -> Unit = { ActivityWritingDays.snapshot.update(it) }
) {
    @Inject constructor(source: ActivitySource, store: ActivityStore) : this(source, store, Dispatchers.IO)

    private val lock = Mutex()
    private var cache: Map<LocalDate, Int>? = null
    private var seen: StrokeSignature? = null
    private var snapshot: Map<String, Int>? = null

    suspend fun dayCounts(): Map<LocalDate, Int> = withContext(io) {
        lock.withLock {
            val signature = source.signature()
            cache?.let { if (signature == seen) return@withLock it.also(publish) }
            val saved = snapshot ?: decode(store.read()).also { snapshot = it }
            val merged = WritingActivityStats.mergeMax(saved, source.dayCounts(0L))
            if (merged != saved) {
                runCatching { store.write(encode(merged)) }
                    .onFailure { Log.w(TAG, "snapshot not saved: ${it.javaClass.simpleName}: ${it.message}") }
                snapshot = merged
            }
            WritingActivityStats.parseDays(merged).also { cache = it; seen = signature; publish(it) }
        }
    }

    internal companion object {
        const val KEY = "writing-activity:v1"
        private const val TAG = "WritingActivity"
        private val serializer = MapSerializer(String.serializer(), Int.serializer())

        fun encode(days: Map<String, Int>): String = Json.encodeToString(serializer, days)

        /** A missing or damaged snapshot is no snapshot: the table is counted from scratch. */
        fun decode(json: String?): Map<String, Int> =
            if (json.isNullOrBlank()) emptyMap()
            else runCatching { Json.decodeFromString(serializer, json) }.getOrDefault(emptyMap())
    }
}
