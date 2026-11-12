package com.ethran.notable.planner

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class PlannerNamesTest {
    private val known = setOf("Week 44/53", "Hafta 44/53", "Semana 44/53")

    @Test fun `a title in another language is renamed`() {
        assertEquals("Week 44/53", PlannerNames.decide("Hafta 44/53", "Week 44/53", known, legacyWeek = true))
        assertEquals("Hafta 44/53", PlannerNames.decide("Semana 44/53", "Hafta 44/53", known, legacyWeek = true))
    }

    @Test fun `a title that is already right stays`() {
        assertNull(PlannerNames.decide("Week 44/53", "Week 44/53", known, legacyWeek = true))
    }

    @Test fun `a title the user typed is never overwritten`() {
        assertNull(PlannerNames.decide("Launch week", "Week 44/53", known, legacyWeek = true))
        assertNull(PlannerNames.decide("Hafta planım", "Week 44/53", known, legacyWeek = true))
    }

    @Test fun `the first version's week title without the total is renamed`() {
        assertEquals("Week 44/53", PlannerNames.decide("Hafta 44", "Week 44/53", known, legacyWeek = true))
        assertNull(PlannerNames.decide("Hafta 44", "Pazartesi 26 Eki", known, legacyWeek = false))
    }

    @Test fun `a blank title is filled`() {
        assertEquals("Week 44/53", PlannerNames.decide("", "Week 44/53", known, legacyWeek = true))
    }
}
