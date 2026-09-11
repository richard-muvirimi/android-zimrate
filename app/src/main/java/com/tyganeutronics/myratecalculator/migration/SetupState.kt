package com.tyganeutronics.myratecalculator.migration

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.tyganeutronics.myratecalculator.auth.AuthManager
import com.tyganeutronics.myratecalculator.database.models.RewardModel
import com.tyganeutronics.myratecalculator.database.rtdb.CurrencyMapping
import com.tyganeutronics.myratecalculator.database.rtdb.WalletContract
import com.tyganeutronics.myratecalculator.database.rtdb.WalletRepository
import com.tyganeutronics.myratecalculator.utils.TokenUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * First-launch setup: take an account, move the old database into the tree, delete it.
 *
 * Needs the network exactly once. A uid cannot be minted on device, so there is no offline path
 * to a first launch — but once it has succeeded the token is cached indefinitely and everything
 * afterwards works with no signal. Nothing is migrated and <em>nothing is deleted</em> until it
 * does succeed, so waiting can only ever cost a delay, never data.
 */
object SetupState {

    private const val TAG = "SetupState"
    private const val TIMEOUT_MS = 30_000L

    sealed interface Status {
        /** Signing in or copying rows. The splash holds on this, up to its ceiling. */
        data object Working : Status

        /** Through the gate — either migrated, or there was nothing to migrate. */
        data object Ready : Status

        /** Could not reach a server. The only state that asks the user for something. */
        data object NeedsConnection : Status
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow<Status>(Status.Working)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun begin(context: Context) {
        val appContext = context.applicationContext
        scope.launch { attempt(appContext) }
    }

    /** Called by the setup screen's retry. */
    fun retry(context: Context) {
        if (_status.value == Status.Ready) return
        _status.value = Status.Working
        begin(context)
    }

    private suspend fun attempt(context: Context) {
        if (AuthManager.ensureSignedIn().isFailure) {
            _status.value = Status.NeedsConnection
            return
        }

        if (!migrate(context)) {
            _status.value = Status.NeedsConnection
            return
        }

        maybeAwardStarterPack(context)
        _status.value = Status.Ready
    }

    /**
     * The starter pack used to hang off Room creating its file for the first time, which went
     * with Room. An empty wallet on a days-old install is the same signal without the database:
     * a fresh install has nothing, and the age check is what stops someone clearing storage to
     * collect it again.
     */
    private suspend fun maybeAwardStarterPack(context: Context) {
        if (TokenUtils.installOlderThan(context, 1)) return

        WalletRepository.awaitLoaded()
        if (WalletRepository.rewards.value?.isNotEmpty() == true) return

        RewardModel.rewardStarterPack(context)
    }

    /**
     * Copies the whole of the old database across in one atomic multi-path write.
     *
     * No chunking and no batch limit — this is the call that needed splitting into two or three
     * under a per-document store. The write is awaited rather than merely queued, so the file is
     * only deleted once the server has it; the gate guarantees there is a connection to wait on.
     */
    private suspend fun migrate(context: Context): Boolean {
        val uid = AuthManager.uid ?: return false

        if (!LegacyDatabase.exists(context)) return true

        val data = LegacyDatabase.read(context) ?: return false

        if (data.isEmpty) {
            LegacyDatabase.delete(context)
            return true
        }

        val updates = mutableMapOf<String, Any?>()

        data.currencies.forEach {
            updates[WalletContract.currencyPath(uid, it.currency)] = CurrencyMapping.toMap(it)
        }
        data.rewards.forEach {
            updates["${WalletContract.rewardsPath(uid)}/${it.key}"] = it.toMap()
        }
        data.spends.forEach {
            updates["${WalletContract.spendsPath(uid)}/${it.key}"] = it.toMap()
        }

        return try {
            withTimeout(TIMEOUT_MS) {
                Firebase.database.reference.updateChildren(updates).await()
            }

            LegacyDatabase.delete(context)
            true
        } catch (e: Exception) {
            // The old database is untouched, so the next attempt starts over cleanly — and the
            // content-hashed keys mean a partial write is overwritten rather than duplicated.
            Log.w(TAG, "Migration failed; leaving the legacy database in place", e)
            false
        }
    }
}
