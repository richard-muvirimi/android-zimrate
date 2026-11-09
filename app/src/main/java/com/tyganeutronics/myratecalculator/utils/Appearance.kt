package com.tyganeutronics.myratecalculator.utils

import androidx.appcompat.app.AppCompatDelegate

/**
 * The settings' theme: system, light or dark (lists.xml's theme_values). Android 9 and older
 * have no system dark mode, so "system" is light there.
 */
object Appearance {

    const val KEY = "theme"

    fun apply(theme: String?) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
