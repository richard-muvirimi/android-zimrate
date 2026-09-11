package com.tyganeutronics.myratecalculator.fragments.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        if (!isAdded) return

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

    companion object {
        const val TAG = "FragmentSectionAccount"
    }
}
