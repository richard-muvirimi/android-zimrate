package com.tyganeutronics.myratecalculator.fragments.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.auth.AuthManager
import com.tyganeutronics.myratecalculator.fragments.rewards.FragmentSignIn
import com.tyganeutronics.myratecalculator.ui.base.BaseFragment
import com.tyganeutronics.myratecalculator.utils.traits.requireViewById
import kotlinx.coroutines.launch

/**
 * Who the wallet belongs to, and the way to change that.
 *
 * Signing in used to be reachable only by starting a purchase, which put the one action that
 * protects someone's coins behind the one action they might never take. It is an ordinary
 * setting now; the purchase gate still exists, but it is the last resort rather than the only
 * door.
 */
class FragmentSectionAccount : BaseFragment(), View.OnClickListener {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_profile_account, container, false)

    override fun bindViews() {
        super.bindViews()

        requireViewById<MaterialButton>(R.id.btn_account_sign_in).setOnClickListener(this)
        requireViewById<MaterialButton>(R.id.btn_account_sign_out).setOnClickListener(this)
        requireViewById<MaterialButton>(R.id.btn_account_delete).setOnClickListener(this)

        // Redraws when a credential is linked or dropped, so the section cannot sit stale
        // behind the sheet that just changed it.
        viewLifecycleOwner.lifecycleScope.launch {
            AuthManager.state.collect { render() }
        }
    }

    override fun syncViews() {
        super.syncViews()
        render()
    }

    private fun render() {
        // Everything below is requireViewById, so the view is the thing to check.
        if (view == null) return

        val signedIn = AuthManager.hasAccount

        requireViewById<AppCompatTextView>(R.id.txt_account_identity).setText(
            if (signedIn) AuthManager.email.orEmpty() else getString(R.string.account_not_signed_in)
        )

        requireViewById<AppCompatTextView>(R.id.txt_account_detail).setText(
            if (signedIn) R.string.account_detail_signed_in
            else R.string.account_detail_anonymous
        )

        requireViewById<MaterialButton>(R.id.btn_account_sign_out).visibility =
            if (signedIn) View.VISIBLE else View.GONE

        requireViewById<MaterialButton>(R.id.btn_account_sign_in).visibility =
            if (signedIn) View.GONE else View.VISIBLE
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_account_sign_in ->
                FragmentSignIn().show(parentFragmentManager, FragmentSignIn.TAG)

            R.id.btn_account_sign_out -> signOut()

            R.id.btn_account_delete -> confirmDelete()
        }
    }

    /**
     * Drops the credential and takes a fresh anonymous account, so there is always a uid to
     * write under. The wallet listener follows that change and clears what the previous account
     * had cached — persistence is per app rather than per account, so nothing else would.
     */
    private fun signOut() {
        viewLifecycleOwner.lifecycleScope.launch {
            AuthManager.signOut()
        }
    }

    /**
     * Irreversible and takes paid-for coins with it, so it is spelled out in full before anything
     * happens. Shown for anonymous accounts as well as signed-in ones — they hold the same coins
     * and currency setup, and most installs never sign in at all.
     */
    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.account_delete_title)
            .setMessage(R.string.account_delete_message)
            .setNegativeButton(R.string.calculator_dialog_cancel, null)
            .setPositiveButton(R.string.account_delete_confirm) { _, _ -> delete() }
            .show()
    }

    /**
     * The account is erased server side and a fresh anonymous one taken, so the app is usable the
     * moment this returns. Nothing here has to clear the wallet: the repositories follow
     * [AuthManager.state], so the new uid attaches and the old cache is dropped on its own.
     */
    private fun delete() {
        viewLifecycleOwner.lifecycleScope.launch {
            val deleted = AuthManager.deleteAccount().isSuccess

            Toast.makeText(
                requireContext().applicationContext,
                getString(
                    if (deleted) R.string.account_deleted else R.string.account_delete_failed
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    companion object {
        const val TAG = "FragmentSectionAccount"
    }
}
