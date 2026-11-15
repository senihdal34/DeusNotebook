package com.ethran.notable.data

import com.ethran.notable.data.datastore.AppSettings
import com.ethran.notable.data.db.Notebook
import com.ethran.notable.data.db.newPage
import org.junit.Assert.assertEquals
import org.junit.Test

class NewPageDefaultsTest {
    @Test
    fun `a quick note is an inbox page, drawn as ruled lines`() {
        assertEquals("inbox", NewPageDefaults.QUICK_NOTE_BACKGROUND)
    }

    @Test
    fun `settings default to the lined template`() {
        assertEquals("lined", AppSettings(version = 1).defaultNativeTemplate)
    }

    @Test
    fun `a new notebook uses the configured template, lined when there is none`() {
        assertEquals("lined", NewPageDefaults.templateForNewNotebook(null))
        assertEquals("lined", NewPageDefaults.templateForNewNotebook(""))
        assertEquals("lined", NewPageDefaults.templateForNewNotebook("  "))
        assertEquals("squared", NewPageDefaults.templateForNewNotebook("squared"))
        assertEquals("dotted", NewPageDefaults.templateForNewNotebook("dotted"))
    }

    @Test
    fun `a stored blank native template gives a lined page, other templates are kept`() {
        assertEquals("lined", NewPageDefaults.backgroundForNotebookPage("blank", "native"))
        assertEquals("lined", NewPageDefaults.backgroundForNotebookPage("lined", "native"))
        assertEquals("dotted", NewPageDefaults.backgroundForNotebookPage("dotted", "native"))
        assertEquals("squared", NewPageDefaults.backgroundForNotebookPage("squared", "native"))
        assertEquals("hexed", NewPageDefaults.backgroundForNotebookPage("hexed", "native"))
    }

    @Test
    fun `pictures and pdfs keep their own template`() {
        assertEquals("blank", NewPageDefaults.backgroundForNotebookPage("blank", "image"))
        assertEquals("/sdcard/a.pdf", NewPageDefaults.backgroundForNotebookPage("/sdcard/a.pdf", "pdf"))
    }

    @Test
    fun `a new page of a default notebook is ruled`() {
        assertEquals("lined", Notebook().newPage().background)
        assertEquals("native", Notebook().newPage().backgroundType)
    }

    @Test
    fun `a new page of an old notebook that stored blank is ruled`() {
        assertEquals("lined", Notebook(defaultBackground = "blank", defaultBackgroundType = "native").newPage().background)
    }

    @Test
    fun `a new page of a notebook with another template keeps it`() {
        assertEquals("squared", Notebook(defaultBackground = "squared").newPage().background)
    }
}
