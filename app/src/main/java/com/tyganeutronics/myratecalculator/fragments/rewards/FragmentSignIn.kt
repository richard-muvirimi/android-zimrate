package com.tyganeutronics.myratecalculator.fragments.rewards

import android.content.Context
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.ContentLoadingProgressBar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.auth.AuthManager
import com.tyganeutronics.myratecalculator.auth.GoogleCredential
import com.tyganeutronics.myratecalculator.auth.LinkError
import com.tyganeutronics.myratecalculator.auth.LinkOutcome
import com.tyganeutronics.myratecalculator.ui.base.BaseFragment
import com.tyganeutronics.myratecalculator.utils.traits.requireViewById
import kotlinx.coroutines.launch

/**
 * Attaches a real credential to the anonymous account the wallet already sits under.
 *
 * Both routes call link rather than sign-in, which is the whole point: linking keeps the uid, so
 * coins earned before signing in are still there afterwards without anything being moved. The
 * one case that cannot work that way is an address that already has an account — two accounts
 * cannot be merged — and [LinkOutcome.SwitchedAccount] is where the user is told so plainly
 * rather than quietly losing what was on this phone.
 */
class FragmentSignIn : BaseFragment(), View.OnClickListener {

    /**
     * Held so an outcome arriving after the sheet closes still has a context to report with.
     *
     * These run on [lifecycleScope] rather than the view's, deliberately: an authentication in
     * flight has to finish whether or not the user is still looking at it. Which means every
     * `requireContext()` after one of those suspension points was a crash waiting for somebody
     * to close the sheet while their sign in was on the wire. Same reason FragmentPurchase keeps
     * one.
     */
    private var appContext: Context? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appContext = requireContext().applicationContext
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sign_in, container, false)
    }

    override fun bindViews() {
        super.bindViews()

        requireViewById<MaterialButton>(R.id.btn_account_google).setOnClickListener(this)
        requireViewById<MaterialButton>(R.id.btn_account_submit).setOnClickListener(this)
        requireViewById<MaterialButton>(R.id.btn_account_forgot).setOnClickListener(this)
    }

    override fun syncViews() {
        super.syncViews()

        requireViewById<Toolbar>(R.id.toolbar).apply {
            title = getString(R.string.account_title)
            setNavigationIcon(R.drawable.ic_close)
            setNavigationOnClickListener {
                if (isVisible) dismiss()
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_account_google -> signInWithGoogle()
            R.id.btn_account_submit -> signInWithEmail()
            R.id.btn_account_forgot -> resetPassword()
        }
    }

    private fun signInWithGoogle() {
        val activity = activity ?: return

        busy(true)
        lifecycleScope.launch {
            when (val result = GoogleCredential.requestIdToken(activity)) {
                is GoogleCredential.Result.Token -> report(AuthManager.linkGoogle(result.idToken))

                // Backing out of the picker needs no telling off.
                GoogleCredential.Result.Cancelled -> busy(false)

                // Anything else has to say so. This is where a missing SHA-1 fingerprint lands,
                // and it happens after the picker, so silence looks like the button is broken.
                GoogleCredential.Result.Failed -> {
                    busy(false)
                    appContext?.let { toast(it.getString(R.string.account_error_google)) }
                }
            }
        }
    }

    private fun signInWithEmail() {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = getString(R.string.account_error_email)
            return
        }

        // Firebase rejects anything shorter, and saying so here beats a round trip to find out.
        if (password.length < 6) {
            passwordLayout.error = getString(R.string.account_error_password_short)
            return
        }

        emailLayout.error = null
        passwordLayout.error = null

        busy(true)
        lifecycleScope.launch {
            report(AuthManager.linkEmail(email, password))
        }
    }

    private fun resetPassword() {
        val email = emailInput.text?.toString()?.trim().orEmpty()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = getString(R.string.account_error_email)
            return
        }

        busy(true)
        lifecycleScope.launch {
            val sent = AuthManager.sendPasswordReset(email)
            busy(false)

            val message = if (sent) R.string.account_reset_sent else R.string.account_reset_failed
            appContext?.let { toast(it.getString(message, email)) }
        }
    }

    private fun report(outcome: LinkOutcome) {
        val context = appContext ?: return

        busy(false)

        when (outcome) {
            is LinkOutcome.Linked -> {
                toast(context.getString(R.string.account_linked, label(outcome.user.email)))
                if (isAdded) dismiss()
            }

            is LinkOutcome.SwitchedAccount -> {
                toast(context.getString(R.string.account_switched, label(outcome.user.email)))
                if (isAdded) dismiss()
            }

            is LinkOutcome.Failed -> when (outcome.error) {
                LinkError.WEAK_PASSWORD -> showPasswordError(R.string.account_error_password_short)

                LinkError.WRONG_PASSWORD -> showPasswordError(R.string.account_error_wrong_password)

                LinkError.GENERIC -> toast(context.getString(R.string.account_error_generic))
            }
        }
    }

    /** Silently does nothing once the field is gone — there is nowhere to show an error. */
    private fun showPasswordError(@StringRes message: Int) {
        val context = appContext ?: return
        if (view == null) return

        passwordLayout.error = context.getString(message)
    }

    private fun label(email: String?) =
        email.orEmpty().ifEmpty { appContext?.getString(R.string.app_name).orEmpty() }

    private fun busy(busy: Boolean) {
        // `view`, not isAdded: isAdded stays true between onDestroyView and onDetach, and every
        // line below goes through requireViewById.
        if (view == null) return

        val loading = requireViewById<ContentLoadingProgressBar>(R.id.account_loading)
        if (busy) loading.show() else loading.hide()

        requireViewById<MaterialButton>(R.id.btn_account_google).isEnabled = !busy
        requireViewById<MaterialButton>(R.id.btn_account_submit).isEnabled = !busy
        requireViewById<MaterialButton>(R.id.btn_account_forgot).isEnabled = !busy
    }

    private fun toast(message: String) {
        val context = appContext ?: return

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private val emailLayout: TextInputLayout
        get() = requireViewById(R.id.input_account_email)

    private val passwordLayout: TextInputLayout
        get() = requireViewById(R.id.input_account_password)

    private val emailInput: TextInputEditText
        get() = requireViewById(R.id.edt_account_email)

    private val passwordInput: TextInputEditText
        get() = requireViewById(R.id.edt_account_password)

    companion object {
        const val TAG = "FragmentSignIn"
    }
}
