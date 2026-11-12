package com.ethran.notable.planner

import java.time.LocalDate

/**
 * The date the user is "looking at" while zooming between planner pages (Year, Quarter, Month, Week, Day).
 * It lives in memory only, like HomeSectionMemory: a stale value is harmless because [targetDate] ignores a
 * focus date that lies outside the page it is zooming from.
 */
object PlannerFocus {
    @Volatile var date: LocalDate? = null

    /** The remembered focus date, or [today] when nothing has been remembered yet. */
    fun orToday(today: LocalDate): LocalDate = date ?: today
}
