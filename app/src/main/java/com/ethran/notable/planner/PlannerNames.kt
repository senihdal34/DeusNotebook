package com.ethran.notable.planner

import com.ethran.notable.R
import com.ethran.notable.i18n.AppLanguage
import com.ethran.notable.i18n.AppStrings
import java.time.LocalDate

/**
 * Names the planner gives its notebook and pages. They follow the app language, so changing the language renames
 * them; pages and the notebook are found by their `background` key and by a name in any language, never by the
 * current name alone.
 */
object PlannerNames {
    fun notebookTitle(language: AppLanguage = AppStrings.language) = AppStrings.getIn(language, R.string.planner_notebook)

    fun weekTitle(monday: LocalDate, language: AppLanguage = AppStrings.language) =
        AppStrings.getIn(language, R.string.planner_week_title, PlannerWeeks.weekOfYear(monday))

    fun dayTitle(day: LocalDate, language: AppLanguage = AppStrings.language): String {
        val locale = language.locale
        return "${PlannerWeeks.dayFull(day.dayOfWeek.value - 1, locale)} ${PlannerWeeks.shortDate(day, locale)}"
    }

    /** The notebook's name in every language (and the name before the app was translated). */
    fun allNotebookTitles(): List<String> = AppLanguage.entries.map { notebookTitle(it) }.distinct() + LEGACY_NOTEBOOK

    /** What [title] of a planner page should read now, or null when it is already right or was typed by the user. */
    fun renamed(background: String, title: String, language: AppLanguage = AppStrings.language): String? {
        val wanted: String
        val known: Set<String>
        val week = PlannerWeeks.parseWeek(background)
        val day = PlannerWeeks.parseDay(background)
        when {
            week != null -> {
                wanted = weekTitle(week, language)
                known = AppLanguage.entries.map { weekTitle(week, it) }.toSet()
            }
            day != null -> {
                wanted = dayTitle(day, language)
                known = AppLanguage.entries.map { dayTitle(day, it) }.toSet()
            }
            else -> return null
        }
        return decide(title, wanted, known, legacyWeek = week != null)
    }

    /**
     * The rename rule: a title the app generated (in any language, a blank one, or the first Turkish version's
     * "Hafta 44") becomes [wanted]; a title that is already right, or that the user typed, stays.
     */
    internal fun decide(title: String, wanted: String, known: Set<String>, legacyWeek: Boolean): String? {
        if (title == wanted) return null
        val generated = title in known || title.isBlank() || (legacyWeek && LEGACY_WEEK.matches(title))
        return if (generated) wanted else null
    }

    private const val LEGACY_NOTEBOOK = "Planlayıcı"
    private val LEGACY_WEEK = Regex("""Hafta \d+(/\d+)?""")
}
