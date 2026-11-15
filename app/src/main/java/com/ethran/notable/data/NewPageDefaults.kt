package com.ethran.notable.data

import com.ethran.notable.data.model.BackgroundType

/**
 * Which background a freshly created page gets. Every new note is a ruled page: a quick note is an inbox page, a notebook
 * page follows the notebook's template and that template is lined unless somebody chose another one.
 */
object NewPageDefaults {
    /** Quick notes (Home's "+ New note") are inbox pages, drawn as ruled lines. */
    const val QUICK_NOTE_BACKGROUND = "inbox"

    /** The template of a new notebook, and of any notebook whose stored template is the plain white sheet. */
    const val NOTEBOOK_TEMPLATE = "lined"

    private const val BLANK = "blank"

    /** Template written into a new notebook: the one in the settings, lined when there is none. */
    fun templateForNewNotebook(configured: String?): String =
        configured?.takeIf { it.isNotBlank() } ?: NOTEBOOK_TEMPLATE

    /**
     * Background of a new page in a notebook whose template is [template] of type [type]. Notebooks made before the default
     * was lined carry "blank" without anyone having chosen it (no setting or screen offered the choice), so a native
     * "blank" template gives a lined page too. Pictures and PDFs keep their own template, and a page that wants a plain
     * white sheet picks it in the page background dialog.
     */
    fun backgroundForNotebookPage(template: String, type: String): String =
        if (type == BackgroundType.Native.key && template == BLANK) NOTEBOOK_TEMPLATE else template
}
