package com.ethran.notable.i18n

import android.content.Context
import android.content.SharedPreferences

/**
 * Where the two language choices live. They are read before the database is open (the activity needs the app
 * language to build its resources), so they sit in SharedPreferences, not in AppSettings. Defaults: English / English.
 */
object LanguagePrefs {
    private const val FILE = "language"
    private const val KEY_APP = "app_language"
    private const val KEY_WRITING = "writing_language"
    private const val KEY_WRITING_PENDING = "writing_language_pending"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun app(context: Context): AppLanguage = AppLanguage.fromTag(prefs(context).getString(KEY_APP, null))

    fun setApp(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_APP, language.tag).commit()   // synchronous: the app may be restarted right after
    }

    fun writing(context: Context): WritingLanguage =
        WritingLanguage.fromCode(prefs(context).getString(KEY_WRITING, null))

    fun setWriting(context: Context, language: WritingLanguage) {
        prefs(context).edit().putString(KEY_WRITING, language.code).commit()
    }

    /** A language the user picked that is not on the tablet yet; it becomes the writing language once it is installed. */
    fun pendingWriting(context: Context): WritingLanguage? =
        prefs(context).getString(KEY_WRITING_PENDING, null)?.let { code -> WritingLanguage.all.firstOrNull { it.code == code } }

    fun setPendingWriting(context: Context, language: WritingLanguage?) {
        prefs(context).edit().apply {
            if (language == null) remove(KEY_WRITING_PENDING) else putString(KEY_WRITING_PENDING, language.code)
        }.commit()
    }
}
