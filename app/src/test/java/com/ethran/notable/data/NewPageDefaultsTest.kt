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
    fun `a new page of a notebook made with the default settings is ruled`() {
        val book = Notebook(defaultBackground = NewPageDefaults.templateForNewNotebook(AppSettings(version = 1).defaultNativeTemplate))
        assertEquals("lined", book.newPage().background)
        assertEquals("native", book.newPage().backgroundType)
    }

    @Test
    fun `a notebook whose template is the blank sheet keeps giving blank pages`() {
        // NotebookConfig's background selector lets a person choose the plain sheet: that choice is honoured.
        assertEquals("blank", Notebook(defaultBackground = "blank", defaultBackgroundType = "native").newPage().background)
    }

    @Test
    fun `a new page of a notebook with another template keeps it`() {
        assertEquals("squared", Notebook(defaultBackground = "squared").newPage().background)
    }
}
