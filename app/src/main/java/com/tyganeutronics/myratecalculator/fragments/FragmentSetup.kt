package com.tyganeutronics.myratecalculator.fragments

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.migration.LegacyDatabase
import com.tyganeutronics.myratecalculator.migration.SetupState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The first-launch gate, on the rare occasions it is seen at all.
 *
 * The splash in MainActivity covers anything that resolves quickly, so this appears only on a
 * slow connection or none. Once it has appeared it stays for [MIN_VISIBLE_MS] even if setup
 * finishes immediately afterwards — a panel that flashes up and vanishes reads as a glitch and
 * leaves people wondering what they missed, which would simply move the problem rather than
 * solve it.
 */
class FragmentSetup : DialogFragment() {

    private var shownAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_NoActionBar)
        // There is nothing useful behind this until setup completes.
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_setup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shownAt = SystemClock.uptimeMillis()

        view.findViewById<MaterialButton>(R.id.btn_setup_retry).setOnClickListener {
            SetupState.retry(requireContext())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            SetupState.status.collect { render(it) }
        }
    }

    private fun render(status: SetupState.Status) {
        val view = view ?: return

        val message = view.findViewById<AppCompatTextView>(R.id.txt_setup_message)
        val loading = view.findViewById<ContentLoadingProgressBar>(R.id.setup_loading)
        val retry = view.findViewById<MaterialButton>(R.id.btn_setup_retry)

        when (status) {
            SetupState.Status.Working -> {
                // A fresh install has no coins to update, and saying otherwise raises exactly
                // the question this screen exists to avoid.
                message.setText(
                    if (LegacyDatabase.exists(requireContext())) R.string.setup_migrating
                    else R.string.setup_working
                )
                loading.show()
                retry.visibility = View.GONE
            }

            SetupState.Status.NeedsConnection -> {
                message.setText(R.string.setup_offline)
                loading.hide()
                retry.visibility = View.VISIBLE
            }

            SetupState.Status.Ready -> dismissWhenSettled()
        }
    }

    private fun dismissWhenSettled() {
        viewLifecycleOwner.lifecycleScope.launch {
            val visibleFor = SystemClock.uptimeMillis() - shownAt
            if (visibleFor < MIN_VISIBLE_MS) delay(MIN_VISIBLE_MS - visibleFor)
            if (isAdded) dismissAllowingStateLoss()
        }
    }

    companion object {
        const val TAG = "FragmentSetup"

        /** Long enough to read, short enough not to be a wait. */
        private const val MIN_VISIBLE_MS = 1_000L
    }
}
