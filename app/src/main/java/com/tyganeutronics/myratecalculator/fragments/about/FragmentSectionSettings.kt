package com.tyganeutronics.myratecalculator.fragments.about

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.tyganeutronics.myratecalculator.BuildConfig
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.utils.BrowserUtils
import com.tyganeutronics.myratecalculator.work.RatesRefreshScheduler
import de.psdev.licensesdialog.LicensesDialog


class FragmentSectionSettings : PreferenceFragmentCompat(),
    Preference.OnPreferenceClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        // One drawable per icon, tinted to the theme here, in place of the hand darkened twin
        // each of these used to carry for night. Only these four: the author and launcher icons
        // further down the screen are full colour artwork and a tint would flatten them.
        val tint = MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            0
        )
        listOf("update_interval", "auto_update", "check_update", "analytics").forEach { key ->
            findPreference<Preference>(key)?.apply {
                icon = icon?.mutate()?.also { DrawableCompat.setTint(it, tint) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        // Both keys shape the background refresh schedule.
        if (key == "check_update" || key == "update_interval") {
            RatesRefreshScheduler.sync(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // This list is one card on a page that scrolls as a whole, so it has to lay itself out at
        // full height and leave scrolling to the page. Left as it was, it would keep its own
        // scrollbar inside a card sized by whatever room the cards above it happened to leave.
        listView.isNestedScrollingEnabled = false
        listView.layoutParams = listView.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        findPreference<Preference>(getString(R.string.pref_app_version))?.title = getString(
            R.string.pref_app_version,
            BuildConfig.VERSION_NAME
        )

        findPreference<Preference>(getString(R.string.license))?.onPreferenceClickListener = this

        listOf(
            getString(R.string.pref_dev_name),
            getString(R.string.pref_dev_url),
            getString(R.string.rates_source),
            getString(R.string.privacy_policy_url),
        ).forEach { key ->
            findPreference<Preference>(key)?.onPreferenceClickListener = this
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val devUrl = getString(R.string.pref_dev_url)
        val ratesUrl = getString(R.string.rates_source)
        val privacyUrl = getString(R.string.privacy_policy_url)

        return when (preference.key) {
            getString(R.string.license) -> {
                LicensesDialog.Builder(activity)
                    .setNotices(R.raw.licenses)
                    .setIncludeOwnLicense(true)
                    .build()
                    .show()
                true
            }
            getString(R.string.pref_dev_name), devUrl -> {
                BrowserUtils.openUrl(requireContext(), devUrl)
                true
            }
            ratesUrl -> {
                BrowserUtils.openUrl(requireContext(), ratesUrl)
                true
            }
            privacyUrl -> {
                BrowserUtils.openUrl(requireContext(), privacyUrl)
                true
            }
            else -> false
        }
    }
}
