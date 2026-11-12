package com.ethran.notable.reading

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

/** One book as NeoReader's library knows it. */
data class NrBook(
    val path: String,
    val title: String,
    val authors: List<String>,
    val series: String?,
    val progressPage: Int,
    val progressTotal: Int,
    val lastAccess: Long,
    val addedAt: Long,
    val finished: Boolean,
    val hash: String?
)

/** One row of NeoReader's reading log. type 1 = a page read (durationMs on that page), type 5 = progress snapshot. */
data class NrEvent(
    val type: Int, val time: Long, val docId: String?, val md5: String?, val durationMs: Long, val page: Int,
    val progressPct: Double, val sid: String?, val path: String?
)

/** A highlight the reader made. */
data class NrHighlight(val quote: String, val time: Long, val md5: String?, val docId: String?)

/**
 * Reads NeoReader's own library, reading log and highlights. Every call returns what it could read: a missing provider
 * or a changed column gives an empty list and a log line, never a crash.
 */
object NeoReaderSource {
    private const val TAG = "NeoReaderSource"
    private val LIBRARY = Uri.parse("content://com.onyx.content.database.ContentProvider/Metadata")
    private val ANNOTATION = Uri.parse("content://com.onyx.content.database.ContentProvider/Annotation")
    private val STATS = Uri.parse("content://com.onyx.kreader.statistics.provider/OnyxStatisticsModel")

    private fun Cursor.str(name: String): String? = getColumnIndex(name).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }
    private fun Cursor.lng(name: String): Long = str(name)?.toLongOrNull() ?: 0L

    /** The columns each mapping below reads: every refresh crosses Binder, so the rest of each table stays behind. */
    private val LIBRARY_COLUMNS = arrayOf(
        "nativeAbsolutePath", "progress", "title", "name", "authors", "series", "lastAccess", "createdAt", "readingStatus", "hashTag"
    )
    private val STATS_COLUMNS = arrayOf(
        "action", "type", "eventTime", "docId", "md5", "durationTime", "currPage", "readingProgress", "sid", "path"
    )
    private val ANNOTATION_COLUMNS = arrayOf("quote", "createdAt", "md5", "docId")

    private fun <T> query(context: Context, uri: Uri, columns: Array<String>, map: (Cursor) -> T?): List<T> {
        fun read(projection: Array<String>?): List<T> = context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) map(c)?.let(::add) }
        } ?: emptyList()
        return try {
            read(columns)
        } catch (e: Exception) {
            // A newer NeoReader may have dropped or renamed a column: read every column, missing ones read as empty.
            Log.w(TAG, "query $uri with columns failed: ${e.javaClass.simpleName}: ${e.message}")
            try {
                read(null)
            } catch (e: Exception) {
                Log.w(TAG, "query $uri failed: ${e.javaClass.simpleName}: ${e.message}")
                emptyList()
            }
        }
    }

    fun library(context: Context): List<NrBook> = query(context, LIBRARY, LIBRARY_COLUMNS) { c ->
        val path = c.str("nativeAbsolutePath") ?: return@query null
        val progress = c.str("progress")?.split("/")
        val page = progress?.getOrNull(0)?.toIntOrNull() ?: 0
        val total = progress?.getOrNull(1)?.toIntOrNull() ?: 0
        NrBook(
            path = path,
            title = c.str("title")?.takeIf { it.isNotBlank() } ?: c.str("name").orEmpty().substringBeforeLast('.'),
            authors = c.str("authors")?.split(",", ";", "\n")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            series = c.str("series")?.takeIf { it.isNotBlank() },
            progressPage = page, progressTotal = total,
            lastAccess = c.lng("lastAccess"), addedAt = c.lng("createdAt"),
            finished = c.str("readingStatus") == "2" || (total > 0 && page >= total),
            hash = c.str("hashTag")
        )
    }

    fun events(context: Context): List<NrEvent> = query(context, STATS, STATS_COLUMNS) { c ->
        if (c.str("action") == "delete") return@query null
        NrEvent(
            type = c.str("type")?.toIntOrNull() ?: return@query null,
            time = c.lng("eventTime"), docId = c.str("docId"), md5 = c.str("md5"),
            durationMs = c.lng("durationTime"), page = c.str("currPage")?.toIntOrNull() ?: 0,
            progressPct = c.str("readingProgress")?.toDoubleOrNull() ?: 0.0, sid = c.str("sid"), path = c.str("path")
        )
    }

    fun highlights(context: Context): List<NrHighlight> = query(context, ANNOTATION, ANNOTATION_COLUMNS) { c ->
        val q = c.str("quote")?.trim()?.takeIf { it.length > 20 } ?: return@query null
        NrHighlight(q, c.lng("createdAt"), c.str("md5"), c.str("docId"))
    }

    /** Spike: logs what could be read, so a run on the tablet shows whether another app may use these providers. */
    fun probe(context: Context) {
        val b = library(context); val e = events(context); val h = highlights(context)
        Log.i(TAG, "probe: library=${b.size} events=${e.size} (pages=${e.count { it.type == 1 }}) highlights=${h.size}")
    }
}
