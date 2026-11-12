package com.ethran.notable.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

/** Builds a Context whose resources and default Locale follow the app language. */
object Localization {
    /** [asDefault] also makes it the JVM default Locale (date and number formatting); leave it off for a peek into another language. */
    fun wrap(base: Context, language: AppLanguage = LanguagePrefs.app(base), asDefault: Boolean = true): Context {
        val locale = language.locale
        if (asDefault) Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** The locale for dates and numbers, i.e. the app language. */
    fun locale(context: Context): Locale = LanguagePrefs.app(context).locale
}

/**
 * Restarts the whole app process (not just the activity), so nothing built in the old language survives: caches,
 * singletons, remembered texts. Call it after the new language has been saved.
 */
fun restartApp(context: Context) {
    val app = context.applicationContext
    val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
    launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    // A helper activity in another process ends this process and starts the app again (see RestartActivity).
    val helper = android.content.Intent(app, RestartActivity::class.java)
        .putExtra(RestartActivity.EXTRA_LAUNCH, launch)
        .putExtra(RestartActivity.EXTRA_MAIN_PID, android.os.Process.myPid())
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    app.startActivity(helper)
}

/** Strings for code that has no composition (view models, data builders). Follows the app language. */
object AppStrings {
    private var app: Context? = null
    private var cachedTag: String? = null
    private var cached: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    @Synchronized
    private fun localized(): Context? {
        val base = app ?: return null
        val language = LanguagePrefs.app(base)
        if (cachedTag != language.tag) {
            cached = Localization.wrap(base, language)
            cachedTag = language.tag
        }
        return cached
    }

    val locale: Locale get() = app?.let { Localization.locale(it) } ?: Locale.ENGLISH

    val language: AppLanguage get() = app?.let { LanguagePrefs.app(it) } ?: AppLanguage.Default

    fun get(@StringRes id: Int, vararg args: Any): String {
        val ctx = localized() ?: return ""
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

    fun plural(id: Int, count: Int, vararg args: Any): String {
        val ctx = localized() ?: return ""
        return ctx.resources.getQuantityString(id, count, *args)
    }

    /** The string in one specific language, whatever the app language is (used to recognize names made earlier). */
    fun getIn(language: AppLanguage, @StringRes id: Int, vararg args: Any): String {
        val base = app ?: return ""
        val ctx = Localization.wrap(base, language, asDefault = false)
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }
}
