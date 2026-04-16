package com.eyeguard.app.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Утилита переключения языка всего приложения.
 * AppCompatDelegate.setApplicationLocales() автоматически
 * пересоздаёт все Activity с нужной локалью.
 */
object LocaleHelper {

    fun applyLocale(languageTag: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun getCurrentTag(): String {
        val current = AppCompatDelegate.getApplicationLocales()
        return if (current.isEmpty) "en-US"
        else current[0]?.toLanguageTag() ?: "en-US"
    }
}
