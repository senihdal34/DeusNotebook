package com.ethran.notable.data

/**
 * Which background a freshly created page gets. A quick note is an inbox page (ruled lines); a new notebook gets the
 * lined template unless the settings name another one. A notebook page always follows its notebook's stored template,
 * including a plain "blank" sheet chosen in the notebook's settings: that choice is never rewritten.
 */
object NewPageDefaults {
    /** Quick notes (Home's "+ New note") are inbox pages, drawn as ruled lines. */
    const val QUICK_NOTE_BACKGROUND = "inbox"

    /** The template of a new notebook when the settings name none. */
    const val NOTEBOOK_TEMPLATE = "lined"

    /** Template written into a new notebook: the one in the settings, lined when there is none. */
    fun templateForNewNotebook(configured: String?): String =
        configured?.takeIf { it.isNotBlank() } ?: NOTEBOOK_TEMPLATE
}
