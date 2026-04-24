package com.tyganeutronics.myratecalculator.utils

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object BrowserUtils {
    fun openUrl(context: Context, url: String) {
        if (url.isBlank()) return
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, Uri.parse(url))
    }
}
