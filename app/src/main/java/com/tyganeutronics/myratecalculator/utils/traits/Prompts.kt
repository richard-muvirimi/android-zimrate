package com.tyganeutronics.myratecalculator.utils.traits

import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tyganeutronics.myratecalculator.R
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt

/**
 * Prompts
 *
 * For the gestures a screen has no way of showing it has. Everything they cover is in the Tips
 * dialog as well — this is the same information put where it applies, once, instead of behind an
 * overflow item nobody opens.
 */

/**
 * A prompt in the app's colours.
 *
 * Built against the activity rather than the fragment on purpose: these fragments extend
 * [com.google.android.material.bottomsheet.BottomSheetDialogFragment] but are shown by
 * transaction, so the dialog resource finder the fragment overload resolves to would go looking
 * for a window that is not there.
 */
fun Fragment.helpPrompt(
    @StringRes primary: Int,
    @StringRes secondary: Int,
): MaterialTapTargetPrompt.Builder =
    MaterialTapTargetPrompt.Builder(requireActivity())
        .setPrimaryText(primary)
        .setSecondaryText(secondary)
        .setBackgroundColour(ContextCompat.getColor(requireContext(), R.color.colorPrimaryLight))
        .setFocalColour(ContextCompat.getColor(requireContext(), R.color.prompt_focal))

/**
 * Runs [show] the first time [key] is reached and never again. Marked before showing, so a
 * prompt that cannot find its target is not retried on every visit. The Help menu item calls the
 * same sequence directly, which is what makes dismissing this one safe.
 */
fun Fragment.showHelpOnce(key: String, show: () -> Unit) {
    if (!requireContext().getBooleanPref(key, true)) return
    requireContext().putBooleanPref(key, false)
    show()
}
