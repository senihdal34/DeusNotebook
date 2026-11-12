package com.ethran.notable.reading

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import android.util.Log
import android.widget.Toast
import com.ethran.notable.R
import java.io.File

/** Opens a book in NeoReader, the reader the data comes from, at the position it saved. */
object ReadingLauncher {
    private const val TAG = "ReadingLauncher"
    private const val NEOREADER = "com.onyx.kreader"

    fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "epub" -> "application/epub+zip"
        "mobi", "azw3" -> "application/x-mobipocket-ebook"
        "txt" -> "text/plain"
        else -> "*/*"
    }

    /**
     * Opens the book by its real file path. NeoReader finds the saved position of a book by that path, so a
     * content:// link (a copy under another name) would open it at page one: that was the bug. A file:// link is not
     * allowed by default, so the check is switched off for this one call only.
     */
    fun open(context: Context, path: String): Boolean {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, RS.get(R.string.reading_book_missing), Toast.LENGTH_SHORT).show()
            return false
        }
        val previous = StrictMode.getVmPolicy()
        return try {
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(file), mimeOf(path)).setPackage(NEOREADER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Only the outcome: the message of a failed start names the file.
            Log.w(TAG, "could not open the book: ${e.javaClass.simpleName}")
            Toast.makeText(context, RS.get(R.string.reading_open_failed), Toast.LENGTH_SHORT).show()
            false
        } finally {
            StrictMode.setVmPolicy(previous)
        }
    }
}
