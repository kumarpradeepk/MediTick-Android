package com.kabi.pillpal.meditick

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Applies the in-app language choice without pulling in AppCompat.
 *
 * On Android 13+ the framework's own per-app language store owns the choice, so
 * it survives restarts and shows up in system Settings. Below 13 there is no
 * such store, so the tag is kept in [com.kabi.pillpal.meditick.data.SettingsStore]
 * and re-applied to the base context every time an Activity is created.
 */
object LocaleSupport {

    const val SYSTEM = "system"

    /** Records the choice with the system where possible. */
    fun apply(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            manager.applicationLocales =
                if (tag == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            // Pre-13 the change lands when the Activity is recreated below.
            Locale.setDefault(localeFor(tag) ?: Locale.getDefault())
        }
    }

    /**
     * Wraps a base context in the chosen locale. A no-op on 13+, where the
     * framework has already resolved the configuration for us.
     */
    fun wrap(base: Context, tag: String): Context {
        if (Build.VERSION.SDK_INT >= 33 || tag == SYSTEM) return base
        val locale = localeFor(tag) ?: return base
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    private fun localeFor(tag: String): Locale? =
        if (tag == SYSTEM) null else Locale.forLanguageTag(tag).takeIf { it.language.isNotEmpty() }
}
