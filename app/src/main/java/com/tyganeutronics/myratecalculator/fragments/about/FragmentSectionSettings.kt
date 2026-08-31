package com.tyganeutronics.myratecalculator.fragments.about

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
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

        findPreference<Preference>(getString(R.string.pref_app_version))?.title = getString(
            R.string.pref_app_version,
            BuildConfig.VERSION_NAME
        )

        findPreference<Preference>(getString(R.string.license))?.onPreferenceClickListener = this

        listOf(
            getString(R.string.pref_dev_name),
            getString(R.string.pref_dev_url),
            getString(R.string.rates_source),
        ).forEach { key ->
            findPreference<Preference>(key)?.onPreferenceClickListener = this
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val devUrl = getString(R.string.pref_dev_url)
        val ratesUrl = getString(R.string.rates_source)

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
            else -> false
        }
    }
}
