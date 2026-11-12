package com.ethran.notable.editor.rail

import com.ethran.notable.data.datastore.AppSettings
import com.ethran.notable.data.datastore.ToolbarSide
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings saved before the tool rail still load; the old Top/Bottom position becomes Left. */
class ToolbarSettingsTest {
    private fun decode(json: String) = Json.decodeFromString(AppSettings.serializer(), json)

    @Test fun oldTopBecomesLeft() {
        val s = decode("""{"version":1,"toolbarPosition":"Top"}""")
        assertEquals(ToolbarSide.Left, s.toolbarSide)
    }

    @Test fun oldBottomBecomesLeft() {
        val s = decode("""{"version":1,"toolbarPosition":"Bottom","smoothScroll":false}""")
        assertEquals(ToolbarSide.Left, s.toolbarSide)
        assertEquals(false, s.smoothScroll) // everything else is kept
    }

    @Test fun settingsWithoutAnyToolbarFieldGetTheDefaults() {
        val s = decode("""{"version":1}""")
        assertEquals(ToolbarSide.Left, s.toolbarSide)
        assertTrue(s.toolbarAutoHide)
    }

    @Test fun theRightSideAndAutoHideOffSurviveASaveAndLoad() {
        val saved = AppSettings(version = 1, toolbarSide = ToolbarSide.Right, toolbarAutoHide = false)
        val back = decode(Json.encodeToString(AppSettings.serializer(), saved))
        assertEquals(ToolbarSide.Right, back.toolbarSide)
        assertEquals(false, back.toolbarAutoHide)
    }
}
