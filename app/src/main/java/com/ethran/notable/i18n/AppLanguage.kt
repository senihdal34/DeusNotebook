package com.ethran.notable.i18n

import java.util.Locale

/** Languages the app's own text (menus, labels, dates) is shipped in. English is the default. */
enum class AppLanguage(val tag: String, val nativeName: String) {
    English("en", "English"),
    Turkish("tr", "Türkçe"),
    Polish("pl", "Polski"),
    Spanish("es", "Español"),
    Chinese("zh", "中文（简体）");

    val locale: Locale
        get() = when (this) {
            Chinese -> Locale.SIMPLIFIED_CHINESE
            else -> Locale(tag)
        }

    companion object {
        val Default = English
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: Default
    }
}

/**
 * A handwriting recognition language of the Onyx (MyScript) recognizer. [code] is what the engine's `lang`
 * argument takes. The recognizer falls back to English when it does not know a code.
 */
data class WritingLanguage(val code: String, val nativeName: String) {
    companion object {
        const val DEFAULT_CODE = "en_US"

        val all: List<WritingLanguage> = listOf(
            WritingLanguage("en_US", "English (US)"),
            WritingLanguage("en_GB", "English (UK)"),
            WritingLanguage("tr_TR", "Türkçe"),
            WritingLanguage("es_ES", "Español"),
            WritingLanguage("zh_CN", "中文（简体）"),
            WritingLanguage("zh_TW", "中文（繁體）"),
            WritingLanguage("pl_PL", "Polski"),
            WritingLanguage("de_DE", "Deutsch"),
            WritingLanguage("fr_FR", "Français"),
            WritingLanguage("it_IT", "Italiano"),
            WritingLanguage("pt_BR", "Português (Brasil)"),
            WritingLanguage("pt_PT", "Português (Portugal)"),
            WritingLanguage("nl_NL", "Nederlands"),
            WritingLanguage("sv_SE", "Svenska"),
            WritingLanguage("da_DK", "Dansk"),
            WritingLanguage("nb_NO", "Norsk"),
            WritingLanguage("fi_FI", "Suomi"),
            WritingLanguage("cs_CZ", "Čeština"),
            WritingLanguage("sk_SK", "Slovenčina"),
            WritingLanguage("hu_HU", "Magyar"),
            WritingLanguage("ro_RO", "Română"),
            WritingLanguage("bg_BG", "Български"),
            WritingLanguage("hr_HR", "Hrvatski"),
            WritingLanguage("sl_SI", "Slovenščina"),
            WritingLanguage("el_GR", "Ελληνικά"),
            WritingLanguage("ru_RU", "Русский"),
            WritingLanguage("uk_UA", "Українська"),
            WritingLanguage("he_IL", "עברית"),
            WritingLanguage("ar_AE", "العربية"),
            WritingLanguage("hi_IN", "हिन्दी"),
            WritingLanguage("th_TH", "ไทย"),
            WritingLanguage("vi_VN", "Tiếng Việt"),
            WritingLanguage("id_ID", "Bahasa Indonesia"),
            WritingLanguage("ms_MY", "Bahasa Melayu"),
            WritingLanguage("ja_JP", "日本語"),
            WritingLanguage("ko_KR", "한국어")
        )

        fun fromCode(code: String?): WritingLanguage = all.firstOrNull { it.code == code } ?: all.first()
    }
}
