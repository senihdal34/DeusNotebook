package com.ethran.notable.planner

import java.time.LocalDate

// Placeholders so this branch compiles without the Month/Quarter/Year pages. Kotlin member functions win over
// extensions, so once feat/planner-month-quarter-year adds the real members to PlannerService these are ignored.
// DELETE THIS FILE after that branch is merged.

suspend fun PlannerService.ensureMonth(date: LocalDate): PlannerService.Target = error("provided by feat/planner-month-quarter-year")

suspend fun PlannerService.ensureQuarter(date: LocalDate): PlannerService.Target = error("provided by feat/planner-month-quarter-year")

suspend fun PlannerService.ensureYear(date: LocalDate): PlannerService.Target = error("provided by feat/planner-month-quarter-year")
