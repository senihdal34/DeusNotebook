// SPDX-License-Identifier: AGPL-3.0-only
// Reading board design derived from BookOrbit (https://github.com/bookorbit/bookorbit), AGPL-3.0-only with additional terms, see licenses/bookorbit/
package com.ethran.notable.reading

import android.content.Context
import android.util.Log
import com.ethran.notable.reading.badges.BadgeStore
import com.ethran.notable.reading.badges.PrefsBadgeStore
import com.ethran.notable.reading.chapters.ChapterRepository
import com.ethran.notable.reading.chapters.ChapterResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the reading board keeps on the device: the yearly goal and the badge records (both in the board's own preferences file). */
interface ReadingPrefs {
    /** The saved goal, already clamped; [ReadingGoal.DEFAULT] when none is saved. */
    fun goal(): Int
    fun saveGoal(goal: Int)
    /** Optional first name for the greeting; empty (the default) greets without a name. */
    fun readerName(): String
    /** Where the first time each badge was earned is kept. */
    fun badgeStore(): BadgeStore
}

/** Where the board gets its data: NeoReader's library, reading log and highlights, the book files and the cover folders. All calls block: use them off the main thread. */
interface ReadingLibrary {
    fun books(): List<NrBook>
    fun events(): List<NrEvent>
    fun highlights(): List<NrHighlight>

    /** Copies covers made by the KOReader board into the board's own folder. Best effort: never throws. */
    fun syncCovers(books: List<NrBook>)

    /** The folder the board reads covers from, or null when neither exists. */
    fun coverDir(): File?

    /** The chapters of the book at [file], cached on disk by path, size and modification time. */
    fun chapters(file: File): ChapterResult
}

@Singleton
class SharedReadingPrefs @Inject constructor(@ApplicationContext context: Context) : ReadingPrefs {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun goal(): Int = ReadingGoal.clamp(prefs.getInt(KEY_GOAL, ReadingGoal.DEFAULT))
    override fun saveGoal(goal: Int) { prefs.edit().putInt(KEY_GOAL, goal).apply() }
    override fun readerName(): String = prefs.getString(KEY_NAME, "").orEmpty()
    override fun badgeStore(): BadgeStore = PrefsBadgeStore(prefs)

    private companion object {
        const val PREFS = "reading_board"
        const val KEY_GOAL = "goal_books"
        const val KEY_NAME = "reader_name"
    }
}

@Singleton
class NeoReaderLibrary @Inject constructor(@ApplicationContext private val context: Context) : ReadingLibrary {
    private val chapterRepo = ChapterRepository(File(context.filesDir, "reading-chapters"))

    override fun books(): List<NrBook> = NeoReaderSource.library(context)
    override fun events(): List<NrEvent> = NeoReaderSource.events(context)
    override fun highlights(): List<NrHighlight> = NeoReaderSource.highlights(context)

    /**
     * Covers made by the KOReader board are copied once into the board's own folder, so it does not depend on
     * KOReader's. Best effort: a missing folder, a full disk or a refused permission only means fewer covers.
     */
    override fun syncCovers(books: List<NrBook>) {
        try {
            val src = File(SOURCE_COVERS)
            if (!src.isDirectory) return
            val dest = File(OWN_COVERS)
            val names = ReadingCovers.toCopy(books.map { it.path }, dest.list()?.toSet().orEmpty(), src.list()?.toSet().orEmpty())
            val n = ReadingCovers.copy(src, dest, names)
            if (n > 0) Log.i(TAG, "copied $n covers into $OWN_COVERS")
        } catch (e: Exception) {
            Log.w(TAG, "could not copy covers: ${e.javaClass.simpleName}")
        }
    }

    override fun coverDir(): File? = COVER_DIRS.map(::File).firstOrNull { it.isDirectory }

    override fun chapters(file: File): ChapterResult = chapterRepo.load(file)

    private companion object {
        const val TAG = "NeoReaderLibrary"
        const val OWN_COVERS = "/sdcard/Documents/ReadingCovers"
        const val SOURCE_COVERS = "/sdcard/koreader/localboard-covers"
        /** The board's own folder first; the KOReader one is the fallback when the copy could not be made. */
        val COVER_DIRS = listOf(OWN_COVERS, SOURCE_COVERS)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReadingModule {
    @Binds
    abstract fun bindReadingPrefs(impl: SharedReadingPrefs): ReadingPrefs

    @Binds
    abstract fun bindReadingLibrary(impl: NeoReaderLibrary): ReadingLibrary
}
