package com.ethran.notable.reading.badges

import com.ethran.notable.R

/**
 * The badges this app can award on its own, taken from BookOrbit's achievement catalogue (its server seed, see
 * docs/reading-badges.md): same keys, names, descriptions, icons, rarities, thresholds and order. BookOrbit has 87;
 * the 56 here are the ones NeoReader's reading log can decide. The rest (ratings, genres, languages, publication
 * years, reading devices, abandoned books, notes) need data this app does not have.
 */
object BadgeCatalogue {
    val ALL: List<BadgeDef> = listOf(
        // ---- Reading
        BadgeDef("books_finished_1", "books_finished", 1, BadgeCategory.Reading, R.string.badge_books_finished_1_name, R.string.badge_books_finished_1_desc, "book-open", Rarity.Common, 1, false, 1),
        BadgeDef("books_finished_2", "books_finished", 2, BadgeCategory.Reading, R.string.badge_books_finished_2_name, R.string.badge_books_finished_2_desc, "book-open", Rarity.Rare, 10, false, 2),
        BadgeDef("books_finished_3", "books_finished", 3, BadgeCategory.Reading, R.string.badge_books_finished_3_name, R.string.badge_books_finished_3_desc, "book-open", Rarity.Epic, 35, false, 3),
        BadgeDef("books_finished_4", "books_finished", 4, BadgeCategory.Reading, R.string.badge_books_finished_4_name, R.string.badge_books_finished_4_desc, "book-open", Rarity.Legendary, 100, false, 4),
        BadgeDef("pages_read_1", "pages_read", 1, BadgeCategory.Reading, R.string.badge_pages_read_1_name, R.string.badge_pages_read_1_desc, "file-text", Rarity.Common, 500, false, 5),
        BadgeDef("pages_read_2", "pages_read", 2, BadgeCategory.Reading, R.string.badge_pages_read_2_name, R.string.badge_pages_read_2_desc, "file-text", Rarity.Rare, 5000, false, 6),
        BadgeDef("pages_read_3", "pages_read", 3, BadgeCategory.Reading, R.string.badge_pages_read_3_name, R.string.badge_pages_read_3_desc, "file-text", Rarity.Epic, 25000, false, 7),
        BadgeDef("pages_read_4", "pages_read", 4, BadgeCategory.Reading, R.string.badge_pages_read_4_name, R.string.badge_pages_read_4_desc, "file-text", Rarity.Legendary, 100000, false, 8),
        BadgeDef("hours_read_1", "hours_read", 1, BadgeCategory.Reading, R.string.badge_hours_read_1_name, R.string.badge_hours_read_1_desc, "clock", Rarity.Common, 10, false, 9),
        BadgeDef("hours_read_2", "hours_read", 2, BadgeCategory.Reading, R.string.badge_hours_read_2_name, R.string.badge_hours_read_2_desc, "clock", Rarity.Rare, 100, false, 10),
        BadgeDef("hours_read_3", "hours_read", 3, BadgeCategory.Reading, R.string.badge_hours_read_3_name, R.string.badge_hours_read_3_desc, "clock", Rarity.Epic, 500, false, 11),
        BadgeDef("hours_read_4", "hours_read", 4, BadgeCategory.Reading, R.string.badge_hours_read_4_name, R.string.badge_hours_read_4_desc, "clock", Rarity.Legendary, 1000, false, 12),
        BadgeDef("marathoner", null, null, BadgeCategory.Reading, R.string.badge_marathoner_name, R.string.badge_marathoner_desc, "timer", Rarity.Rare, 90, false, 13),
        BadgeDef("all_nighter", null, null, BadgeCategory.Reading, R.string.badge_all_nighter_name, R.string.badge_all_nighter_desc, "moon", Rarity.Epic, null, true, 14),
        BadgeDef("early_bird", null, null, BadgeCategory.Reading, R.string.badge_early_bird_name, R.string.badge_early_bird_desc, "sunrise", Rarity.Rare, null, true, 15),
        BadgeDef("long_book_1", "long_book", 1, BadgeCategory.Reading, R.string.badge_long_book_1_name, R.string.badge_long_book_1_desc, "scroll-text", Rarity.Common, 300, false, 16),
        BadgeDef("page_turner", "long_book", 2, BadgeCategory.Reading, R.string.badge_page_turner_name, R.string.badge_page_turner_desc, "scroll-text", Rarity.Rare, 500, false, 17),
        BadgeDef("long_book_3", "long_book", 3, BadgeCategory.Reading, R.string.badge_long_book_3_name, R.string.badge_long_book_3_desc, "book-marked", Rarity.Epic, 750, false, 18),
        BadgeDef("tome_tamer", "long_book", 4, BadgeCategory.Reading, R.string.badge_tome_tamer_name, R.string.badge_tome_tamer_desc, "mountain", Rarity.Legendary, 1000, false, 19),
        BadgeDef("sprint", null, null, BadgeCategory.Reading, R.string.badge_sprint_name, R.string.badge_sprint_desc, "zap", Rarity.Epic, null, false, 20),
        BadgeDef("binge_reader", null, null, BadgeCategory.Reading, R.string.badge_binge_reader_name, R.string.badge_binge_reader_desc, "fast-forward", Rarity.Epic, 3, false, 21),
        BadgeDef("monthly_reader_2", null, null, BadgeCategory.Reading, R.string.badge_monthly_reader_2_name, R.string.badge_monthly_reader_2_desc, "calendar-check", Rarity.Legendary, 10, false, 22),
        BadgeDef("slow_burn", null, null, BadgeCategory.Reading, R.string.badge_slow_burn_name, R.string.badge_slow_burn_desc, "hourglass", Rarity.Rare, null, false, 23),
        BadgeDef("power_hour", null, null, BadgeCategory.Reading, R.string.badge_power_hour_name, R.string.badge_power_hour_desc, "gauge", Rarity.Rare, 75, false, 25),
        BadgeDef("speed_reader", null, null, BadgeCategory.Reading, R.string.badge_speed_reader_name, R.string.badge_speed_reader_desc, "rabbit", Rarity.Epic, 100, false, 26),
        // ---- Library
        BadgeDef("library_builder_1", "library_builder", 1, BadgeCategory.Library, R.string.badge_library_builder_1_name, R.string.badge_library_builder_1_desc, "library", Rarity.Common, 50, false, 1),
        BadgeDef("library_builder_2", "library_builder", 2, BadgeCategory.Library, R.string.badge_library_builder_2_name, R.string.badge_library_builder_2_desc, "library", Rarity.Rare, 250, false, 2),
        BadgeDef("library_builder_3", "library_builder", 3, BadgeCategory.Library, R.string.badge_library_builder_3_name, R.string.badge_library_builder_3_desc, "library", Rarity.Epic, 1000, false, 3),
        BadgeDef("library_builder_4", "library_builder", 4, BadgeCategory.Library, R.string.badge_library_builder_4_name, R.string.badge_library_builder_4_desc, "library", Rarity.Legendary, 5000, false, 4),
        BadgeDef("annotator_1", "annotator", 1, BadgeCategory.Library, R.string.badge_annotator_1_name, R.string.badge_annotator_1_desc, "highlighter", Rarity.Common, 10, false, 5),
        BadgeDef("annotator_2", "annotator", 2, BadgeCategory.Library, R.string.badge_annotator_2_name, R.string.badge_annotator_2_desc, "highlighter", Rarity.Rare, 50, false, 6),
        BadgeDef("annotator_3", "annotator", 3, BadgeCategory.Library, R.string.badge_annotator_3_name, R.string.badge_annotator_3_desc, "highlighter", Rarity.Epic, 200, false, 7),
        BadgeDef("annotator_4", "annotator", 4, BadgeCategory.Library, R.string.badge_annotator_4_name, R.string.badge_annotator_4_desc, "highlighter", Rarity.Legendary, 500, false, 8),
        BadgeDef("multi_format", null, null, BadgeCategory.Library, R.string.badge_multi_format_name, R.string.badge_multi_format_desc, "files", Rarity.Rare, 3, false, 9),
        BadgeDef("curator", null, null, BadgeCategory.Library, R.string.badge_curator_name, R.string.badge_curator_desc, "folder-open", Rarity.Rare, 10, false, 10),
        BadgeDef("deep_dive_session", null, null, BadgeCategory.Library, R.string.badge_deep_dive_session_name, R.string.badge_deep_dive_session_desc, "highlighter", Rarity.Rare, 10, false, 12),
        BadgeDef("bookmarked", null, null, BadgeCategory.Library, R.string.badge_bookmarked_name, R.string.badge_bookmarked_desc, "bookmark-check", Rarity.Common, null, false, 13),
        // ---- Exploration
        BadgeDef("author_deep_dive", null, null, BadgeCategory.Exploration, R.string.badge_author_deep_dive_name, R.string.badge_author_deep_dive_desc, "feather", Rarity.Rare, 5, false, 13),
        BadgeDef("short_story_fan", null, null, BadgeCategory.Exploration, R.string.badge_short_story_fan_name, R.string.badge_short_story_fan_desc, "minimize-2", Rarity.Rare, 5, false, 15),
        BadgeDef("thick_and_thin", null, null, BadgeCategory.Exploration, R.string.badge_thick_and_thin_name, R.string.badge_thick_and_thin_desc, "arrow-up-down", Rarity.Rare, null, false, 16),
        BadgeDef("trilogy_master", null, null, BadgeCategory.Exploration, R.string.badge_trilogy_master_name, R.string.badge_trilogy_master_desc, "list-ordered", Rarity.Rare, null, false, 17),
        BadgeDef("first_series", null, null, BadgeCategory.Exploration, R.string.badge_first_series_name, R.string.badge_first_series_desc, "play", Rarity.Common, null, false, 19),
        // ---- Dedication
        BadgeDef("streak_1", "streak", 1, BadgeCategory.Dedication, R.string.badge_streak_1_name, R.string.badge_streak_1_desc, "flame", Rarity.Common, 7, false, 1),
        BadgeDef("streak_2", "streak", 2, BadgeCategory.Dedication, R.string.badge_streak_2_name, R.string.badge_streak_2_desc, "flame", Rarity.Rare, 30, false, 2),
        BadgeDef("streak_3", "streak", 3, BadgeCategory.Dedication, R.string.badge_streak_3_name, R.string.badge_streak_3_desc, "flame", Rarity.Epic, 100, false, 3),
        BadgeDef("streak_4", "streak", 4, BadgeCategory.Dedication, R.string.badge_streak_4_name, R.string.badge_streak_4_desc, "flame", Rarity.Legendary, 365, false, 4),
        BadgeDef("year_round_reader", null, null, BadgeCategory.Dedication, R.string.badge_year_round_reader_name, R.string.badge_year_round_reader_desc, "calendar", Rarity.Epic, 12, false, 5),
        BadgeDef("comeback_kid", null, null, BadgeCategory.Dedication, R.string.badge_comeback_kid_name, R.string.badge_comeback_kid_desc, "corner-up-right", Rarity.Rare, 30, false, 7),
        BadgeDef("seasonal_reader", null, null, BadgeCategory.Dedication, R.string.badge_seasonal_reader_name, R.string.badge_seasonal_reader_desc, "sun", Rarity.Epic, 4, false, 9),
        BadgeDef("perfect_month", null, null, BadgeCategory.Dedication, R.string.badge_perfect_month_name, R.string.badge_perfect_month_desc, "calendar-check", Rarity.Legendary, null, false, 10),
        BadgeDef("consistent_reader", null, null, BadgeCategory.Dedication, R.string.badge_consistent_reader_name, R.string.badge_consistent_reader_desc, "activity", Rarity.Rare, 14, false, 11),
        BadgeDef("weekend_rhythm", null, null, BadgeCategory.Dedication, R.string.badge_weekend_rhythm_name, R.string.badge_weekend_rhythm_desc, "repeat", Rarity.Epic, 8, false, 12),
        BadgeDef("weekend_warrior", null, null, BadgeCategory.Dedication, R.string.badge_weekend_warrior_name, R.string.badge_weekend_warrior_desc, "swords", Rarity.Rare, 5, false, 13),
        BadgeDef("yearly_finisher_12", null, null, BadgeCategory.Dedication, R.string.badge_yearly_finisher_12_name, R.string.badge_yearly_finisher_12_desc, "award", Rarity.Epic, 12, false, 14),
        BadgeDef("new_year_reader", null, null, BadgeCategory.Dedication, R.string.badge_new_year_reader_name, R.string.badge_new_year_reader_desc, "party-popper", Rarity.Epic, null, true, 15),
        BadgeDef("category_sweeper", null, null, BadgeCategory.Dedication, R.string.badge_category_sweeper_name, R.string.badge_category_sweeper_desc, "trophy", Rarity.Legendary, 4, false, 16)
    )

    private val byKey = ALL.associateBy { it.key }
    fun get(key: String): BadgeDef? = byKey[key]

    /** The catalogue in BookOrbit's list order: category by category, then each category's own sort order. */
    val ORDERED: List<BadgeDef> = ALL.sortedWith(compareBy({ it.category.ordinal }, { it.sortOrder }))
}
