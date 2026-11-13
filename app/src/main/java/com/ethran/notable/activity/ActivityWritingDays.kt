package com.ethran.notable.activity

import android.content.Context
import com.ethran.notable.planner.PlannerWritingDays
import com.ethran.notable.planner.WritingDays
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The days the user wrote on (at least [WritingActivityStats.MIN_STROKES] strokes, the rule of the activity card), as an
 * in-memory snapshot for the planner pages. [hasInk] is a set lookup: draw code never waits and never touches the database.
 * [update] swaps in a new snapshot and reports whether anything changed, so a page redraws only when it has to.
 */
class WritingDaysSnapshot(private val today: () -> LocalDate = { LocalDate.now() }) : WritingDays {
    @Volatile private var days: Set<LocalDate> = emptySet()
    private val _version = MutableStateFlow(0)

    /** Grows by one with every change of the snapshot. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** A day that has not come yet has no dot, whatever the counts say (a changed device clock, a restored backup). */
    override fun hasInk(day: LocalDate): Boolean = day in days && !day.isAfter(today())

    /** [counts] are strokes per day. Returns true when the set of written days changed. */
    fun update(counts: Map<LocalDate, Int>): Boolean {
        val next = counts.filterValues(WritingActivityStats::isWritten).keys
        if (next == days) return false
        days = next
        _version.value += 1
        return true
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ActivityRepositoryEntryPoint {
    fun repository(): WritingActivityRepository
}

/** The one snapshot of the app. [WritingActivityRepository] fills it whenever it counts; the planner pages read it. */
object ActivityWritingDays {
    val snapshot = WritingDaysSnapshot()

    /** Makes the planner pages draw their dots from [snapshot]. Safe to call again; true when it switched them over. */
    fun install(): Boolean {
        if (PlannerWritingDays.current === snapshot) return false
        PlannerWritingDays.current = snapshot
        return true
    }

    /**
     * Counts again when the strokes changed (the repository answers from memory otherwise) and returns true when the
     * dots changed, so the open page has to be redrawn. Runs on the IO dispatcher; a failure leaves the old dots.
     * The first call also counts as a change: the page may have been drawn from the empty default before, while the
     * snapshot was already filled by another reader (the activity card on Home).
     */
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        val installed = install()
        val before = snapshot.version.value
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, ActivityRepositoryEntryPoint::class.java).repository().dayCounts()
        }
        installed || snapshot.version.value != before
    }
}
