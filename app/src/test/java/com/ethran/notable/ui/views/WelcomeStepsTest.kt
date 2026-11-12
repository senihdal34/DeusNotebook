package com.ethran.notable.ui.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeStepsTest {
    private val none = WelcomeProgress(fileAccessGranted = false, refreshModeRecommended = false)
    private val access = none.copy(fileAccessGranted = true)
    private val all = WelcomeProgress(fileAccessGranted = true, refreshModeRecommended = true)

    @Test fun stepsAreInTheShownOrder() {
        assertEquals(
            listOf(
                WelcomeStep.LANGUAGE, WelcomeStep.FILE_ACCESS, WelcomeStep.REFRESH_MODE,
                WelcomeStep.UNFREEZE, WelcomeStep.SYNC, WelcomeStep.GUIDE
            ),
            WelcomeStep.entries
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6), WelcomeStep.entries.map { it.number })
    }

    @Test fun onlyFileAccessIsRequired() {
        assertEquals(listOf(WelcomeStep.FILE_ACCESS), WelcomeStep.entries.filter { it.required })
    }

    @Test fun fileAccessAndRefreshModeFollowTheSystem() {
        assertEquals(StepStatus.TODO, stepStatus(WelcomeStep.FILE_ACCESS, none))
        assertEquals(StepStatus.DONE, stepStatus(WelcomeStep.FILE_ACCESS, access))
        assertEquals(StepStatus.TODO, stepStatus(WelcomeStep.REFRESH_MODE, access))
        assertEquals(StepStatus.DONE, stepStatus(WelcomeStep.REFRESH_MODE, all))
    }

    @Test fun stepsTheAppCannotCheckAreInformational() {
        for (step in listOf(WelcomeStep.LANGUAGE, WelcomeStep.UNFREEZE, WelcomeStep.SYNC, WelcomeStep.GUIDE)) {
            assertEquals(step.name, StepStatus.INFO, stepStatus(step, none))
            assertEquals(step.name, StepStatus.INFO, stepStatus(step, all))
        }
    }

    @Test fun continueNeedsFileAccessOnly() {
        assertFalse(canContinue(none))
        assertEquals(listOf(WelcomeStep.FILE_ACCESS), remainingRequired(none))
        assertTrue(canContinue(access))
        assertTrue(remainingRequired(access).isEmpty())
        assertTrue(canContinue(all))
        // The refresh mode alone does not unlock Continue.
        assertFalse(canContinue(none.copy(refreshModeRecommended = true)))
    }

    @Test fun syncWaitsForFileAccess() {
        assertFalse(canSetUpSync(none))
        assertTrue(canSetUpSync(access))
    }

    @Test fun appOpensOnWelcomeFirstRunWithoutAccessOrAfterLanguageChange() {
        assertTrue(startsOnWelcome(showWelcome = true, hasFilePermission = true, resumeWelcome = false))
        assertTrue(startsOnWelcome(showWelcome = false, hasFilePermission = false, resumeWelcome = false))
        assertTrue(startsOnWelcome(showWelcome = false, hasFilePermission = true, resumeWelcome = true))
        assertFalse(startsOnWelcome(showWelcome = false, hasFilePermission = true, resumeWelcome = false))
    }
}
