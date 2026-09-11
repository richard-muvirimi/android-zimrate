package com.tyganeutronics.myratecalculator.auth

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.database.database
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.rtdb.CurrencyMapping
import com.tyganeutronics.myratecalculator.database.rtdb.CurrencyRepository
import com.tyganeutronics.myratecalculator.database.rtdb.WalletContract
import com.tyganeutronics.myratecalculator.database.rtdb.WalletRepository
import com.tyganeutronics.myratecalculator.database.rtdb.WalletSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Owns the Firebase Auth account the wallet hangs off.
 *
 * Every install gets an anonymous account so there is always a uid to write under; earning and
 * spending free coins never asks for anything more. Buying coins does, because an anonymous
 * account cannot be recovered.
 *
 * Upgrading is a link rather than a fresh sign-in, which matters: linking keeps the same uid, so
 * the coins already earned are simply still there afterwards with nothing to migrate.
 */
object AuthManager {

    private const val TAG = "AuthManager"

    private val auth: FirebaseAuth get() = Firebase.auth

    val user: FirebaseUser? get() = auth.currentUser

    val uid: String? get() = user?.uid

    /** Signed in with a real credential rather than an anonymous one. */
    val hasAccount: Boolean get() = user?.isAnonymous == false

    /** The signed-in address, for showing who is currently attached to the wallet. */
    val email: String? get() = user?.email

    private val _state = MutableStateFlow(currentState())

    /** Lets the UI redraw when an account is linked, switched or signed out of. */
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        // Firebase restores the persisted user asynchronously, so reading currentUser once when
        // this object happens to be touched is a race: land on the wrong side of it and the
        // initial state says signed out, nothing ever publishes a correction, and the
        // repositories are never pointed at a uid — which shows up as a balance that never
        // arrives and history screens that stay blank.
        //
        // The listener is the cure and is what it exists for: it fires as soon as auth state is
        // known, and again on every sign in, sign out and token refresh.
        auth.addAuthStateListener { publish() }
    }

    /**
     * Makes sure there is a uid to write under, signing in anonymously if there is not.
     *
     * A brand new anonymous account cannot be minted offline — it needs one round trip — so this
     * can fail with no network. Once it has succeeded the token is cached indefinitely and
     * nothing here touches the network again.
     */
    suspend fun ensureSignedIn(): Result<FirebaseUser> {
        user?.let { return Result.success(it) }

        return try {
            val result = auth.signInAnonymously().await()
            val signedIn = result.user ?: error("Anonymous sign in returned no user")
            publish()
            Result.success(signedIn)
        } catch (e: Exception) {
            Log.w(TAG, "Anonymous sign in failed", e)
            Result.failure(e)
        }
    }

    /**
     * Attaches an email and password to the current account.
     *
     * If that address already has an account the link cannot happen — Firebase Auth has no way to
     * merge two — so this signs into the existing one and reports [LinkOutcome.SwitchedAccount],
     * bringing the coins and rate setup with it. See [carryAccountData].
     */
    suspend fun linkEmail(email: String, password: String): LinkOutcome {
        val credential = EmailAuthProvider.getCredential(email, password)

        return try {
            val result = auth.currentUser
                ?.linkWithCredential(credential)?.await()
                ?: auth.signInWithEmailAndPassword(email, password).await()

            publish()
            LinkOutcome.Linked(result.user ?: error("Link returned no user"))
        } catch (e: FirebaseAuthUserCollisionException) {
            signInExisting(email, password)
        } catch (e: FirebaseAuthWeakPasswordException) {
            LinkOutcome.Failed(LinkError.WEAK_PASSWORD)
        } catch (e: Exception) {
            Log.w(TAG, "Email link failed", e)
            LinkOutcome.Failed(LinkError.GENERIC)
        }
    }

    /** The address is taken, so the password given has to be that account's or this goes nowhere. */
    private suspend fun signInExisting(email: String, password: String): LinkOutcome {
        // Read before the switch — once the other account is signed in, this one is unreadable.
        val currencies = CurrencyRepository.snapshot()
        val wallet = WalletRepository.snapshot()

        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: error("Sign in returned no user")

            carryAccountData(user.uid, currencies, wallet)
            publish()

            LinkOutcome.SwitchedAccount(user)
        } catch (e: Exception) {
            Log.w(TAG, "Sign in to existing account failed", e)
            LinkOutcome.Failed(LinkError.WRONG_PASSWORD)
        }
    }

    /**
     * Carries a stranded anonymous account's coins and rate setup onto the account just signed
     * into.
     *
     * Firebase Auth cannot merge two accounts, so when the credential already belongs to one, a
     * uid has to be abandoned — that part is the platform's rule, not a choice. What happens to
     * the abandoned account's contents is a choice, and leaving days of accumulated coins behind
     * was the wrong one. Somebody who earns coins for a week and then signs in has done nothing
     * to deserve starting over.
     *
     * Withholding this bought no safety. The rules already let any client write its own rewards
     * within the per-type amount bounds, so a forged balance was always reachable; refusing to
     * carry a real one only penalised the honest.
     *
     * Merged key by key rather than all or nothing, which is what makes it right for a returning
     * user. Their account already holds coins from before, so a rule of "only adopt into an empty
     * account" would drop everything they earned while they had forgotten to sign in — the very
     * case this exists for.
     *
     * Key-wise merging is also what makes it safe, and the keys were built for it. A clock-in is
     * `c_yyyy-MM-dd` and a purchase is `p_{hash of the purchase token}`, so the same day or the
     * same payment carries the same name on both accounts and simply is not copied twice. The
     * farm this would otherwise invite — sign out, take a clock-in on a fresh anonymous account,
     * sign back in, repeat — collides with itself and yields nothing.
     */
    private suspend fun carryAccountData(
        targetUid: String,
        currencies: List<RateEntity>,
        wallet: WalletSnapshot,
    ) {
        mergeMissing(
            WalletContract.currenciesPath(targetUid),
            currencies.associate { it.currency to CurrencyMapping.toMap(it) },
        )

        mergeMissing(
            WalletContract.rewardsPath(targetUid),
            wallet.rewards.associate { it.key to it.toMap() },
        )

        mergeMissing(
            WalletContract.spendsPath(targetUid),
            wallet.spends.associate { it.key to it.toMap() },
        )
    }

    /**
     * Adds the entries of [incoming] that [path] does not already have, and touches nothing else.
     *
     * Never overwriting matters as much as never duplicating: a grant the target has already
     * spent from would otherwise be reset to its original balance and hand those coins back.
     */
    private suspend fun mergeMissing(path: String, incoming: Map<String, Any>) {
        if (incoming.isEmpty()) return

        try {
            val ref = Firebase.database.getReference(path)
            val existing = ref.get().await().children.mapNotNull { it.key }.toSet()

            val missing = incoming.filterKeys { it !in existing }
            if (missing.isEmpty()) return

            ref.updateChildren(missing).await()
        } catch (e: Exception) {
            // Logged and not fatal: the sign in itself succeeded, and failing it here would
            // strand the user with neither account working.
            Log.w(TAG, "Could not carry $path to the signed-in account", e)
        }
    }

    /** Same shape as [linkEmail], for a Google ID token obtained through Credential Manager. */
    suspend fun linkGoogle(idToken: String): LinkOutcome {
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        return try {
            val result = auth.currentUser
                ?.linkWithCredential(credential)?.await()
                ?: auth.signInWithCredential(credential).await()

            publish()
            LinkOutcome.Linked(result.user ?: error("Link returned no user"))
        } catch (e: FirebaseAuthUserCollisionException) {
            try {
                val currencies = CurrencyRepository.snapshot()
                val wallet = WalletRepository.snapshot()
                val result = auth.signInWithCredential(credential).await()
                val user = result.user ?: error("Sign in returned no user")

                carryAccountData(user.uid, currencies, wallet)
                publish()

                LinkOutcome.SwitchedAccount(user)
            } catch (inner: Exception) {
                Log.w(TAG, "Sign in to existing Google account failed", inner)
                LinkOutcome.Failed(LinkError.GENERIC)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google link failed", e)
            LinkOutcome.Failed(LinkError.GENERIC)
        }
    }

    suspend fun sendPasswordReset(email: String): Boolean {
        return try {
            auth.sendPasswordResetEmail(email).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Password reset failed", e)
            false
        }
    }

    /**
     * Signs out and immediately takes a fresh anonymous account, so the app is never left
     * without a uid to write under.
     *
     * Phase 3 has to clear the cached wallet here too — persistence is per app, not per account,
     * so without that the next person to sign in on this handset reads the previous one's coins.
     */
    suspend fun signOut(): Result<FirebaseUser> {
        auth.signOut()
        publish()
        return ensureSignedIn()
    }

    private fun publish() {
        _state.value = currentState()
    }

    private fun currentState(): AuthState {
        val current = auth.currentUser
        return when {
            current == null -> AuthState.SignedOut
            current.isAnonymous -> AuthState.Anonymous(current.uid)
            else -> AuthState.Account(
                current.email ?: current.displayName.orEmpty(),
                current.uid,
            )
        }
    }
}

/**
 * Carries the uid on purpose. A StateFlow only emits when the value changes, so states that
 * differ only by account would be swallowed — signing out of one anonymous account straight into
 * another would look like no change at all, and the repositories would stay pointed at a uid
 * that is no longer signed in.
 */
sealed interface AuthState {
    /** No uid yet — the anonymous sign in has not run or could not reach a server. */
    data object SignedOut : AuthState

    /** Usable for free coins, but nothing recoverable. */
    data class Anonymous(val uid: String) : AuthState

    data class Account(val label: String, val uid: String) : AuthState
}

sealed interface LinkOutcome {
    /** The uid was kept, so coins earned before signing in came along. */
    data class Linked(val user: FirebaseUser) : LinkOutcome

    /** The address already had an account. That one is now signed in; anonymous coins stayed behind. */
    data class SwitchedAccount(val user: FirebaseUser) : LinkOutcome

    data class Failed(val error: LinkError) : LinkOutcome
}

enum class LinkError { WEAK_PASSWORD, WRONG_PASSWORD, GENERIC }
