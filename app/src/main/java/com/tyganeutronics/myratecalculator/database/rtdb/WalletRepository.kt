package com.tyganeutronics.myratecalculator.database.rtdb

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.tyganeutronics.myratecalculator.database.contract.RewardContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The wallet, held in memory from a listener on the signed-in user's subtree.
 *
 * Realtime Database can order by one child and nothing more, so none of the old DAO queries
 * survive as queries — they become Kotlin over a list that `keepSynced` guarantees is present.
 * That is not a downgrade: the same filtering had to move client-side anyway because there is no
 * offline aggregation, and as pure functions over a list they are finally testable without a
 * device.
 *
 * Nothing here writes a balance as an absolute value. Two handsets spending the same grant while
 * offline must end up at minus ten rather than at zero, and only a transform composes that way.
 */
object WalletRepository {

    private const val TAG = "WalletRepository"

    private var reference: DatabaseReference? = null
    private var listener: ValueEventListener? = null
    private var uid: String? = null

    private val _rewards = MutableStateFlow<List<Reward>?>(null)
    private val _spends = MutableStateFlow<List<Spend>?>(null)

    /** Null until the first callback. A wallet that has not arrived is not an empty wallet. */
    val rewards: StateFlow<List<Reward>?> = _rewards.asStateFlow()
    val spends: StateFlow<List<Spend>?> = _spends.asStateFlow()

    val loaded: Boolean get() = _rewards.value != null

    /** Emits only once there is something real to say, so observers never see a false zero. */
    val coins = combine(_rewards, _spends) { rewards, _ ->
        rewards?.let { total(it) }
    }

    /**
     * Points the wallet at a user and keeps their subtree synchronised.
     *
     * [DatabaseReference.keepSynced] is what makes every read local and every filter reliable:
     * without it a query offline can answer from a partially cached node and quietly return
     * less than the truth.
     */
    fun attach(uid: String) {
        if (this.uid == uid) return
        detach()

        this.uid = uid

        val ref = Firebase.database.getReference(WalletContract.userPath(uid))
        ref.keepSynced(true)

        val valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _rewards.value = snapshot.child(WalletContract.REWARDS).children
                    .mapNotNull { Reward.from(it) }
                _spends.value = snapshot.child(WalletContract.SPENDS).children
                    .mapNotNull { Spend.from(it) }
            }

            override fun onCancelled(error: DatabaseError) {
                // Usually a rules rejection. Leaving the previous values in place beats
                // replacing a real balance with an empty one because a read was refused.
                Log.w(TAG, "Wallet listener cancelled", error.toException())
            }
        }

        ref.addValueEventListener(valueListener)

        reference = ref
        listener = valueListener
    }

    /**
     * Drops the listener and forgets the wallet.
     *
     * Persistence is per app rather than per account, so signing out has to clear what is held
     * here or the next person to sign in on this handset reads the previous one's coins.
     */
    fun detach() {
        val ref = reference
        val current = listener

        if (ref != null && current != null) {
            ref.keepSynced(false)
            ref.removeEventListener(current)
        }

        reference = null
        listener = null
        uid = null
        _rewards.value = null
        _spends.value = null
    }

    // ── Reads, all of them pure over the loaded list ───────────────────────────────

    /** Total unexpired balance, overdrawn grants included, so arrears show as a negative. */
    fun balance(now: Instant = Instant.now()): Long? =
        _rewards.value?.let { total(it, now) }

    fun balanceOfType(type: String, now: Instant = Instant.now()): Long? =
        _rewards.value
            ?.filter { it.isActiveAt(now) && it.type == type }
            ?.sumOf { it.balance }

    /**
     * Unexpired grants with something left in them, soonest to expire first.
     *
     * Not newest first, which a screen titled "History" would suggest. Coins are spent
     * oldest-expiry-first, so this order is the order they will actually be used in, and what is
     * about to be lost sits at the top where it can still be acted on.
     *
     * Ties break on creation date — ascending here, and descending in
     * [activeRewardsOfType] below. That asymmetry came across from the SQL these replaced and is
     * kept deliberately rather than tidied, so the lists look exactly as they always have.
     */
    fun activeRewards(now: Instant = Instant.now()): List<Reward> =
        _rewards.value.orEmpty()
            .filter { it.isActiveAt(now) && it.balance > 0 }
            .sortedWith(compareBy({ it.expiresAt }, { it.createdAt }))

    /** The same list narrowed to one kind of grant — purchases, for the buying history. */
    fun activeRewardsOfType(type: String, now: Instant = Instant.now()): List<Reward> =
        activeRewards(now).filter { it.type == type }
            .sortedWith(compareBy<Reward> { it.expiresAt }.thenByDescending { it.createdAt })

    fun spendHistory(): List<Spend> =
        _spends.value.orEmpty().sortedByDescending { it.createdAt }

    /** Grants of [type] created on the day [daysAgo] days back, for the clock-in streak. */
    fun rewardsOnDay(daysAgo: Int, type: String = RewardContract.TYPES.CLOCK_IN): List<Reward> {
        val day = LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo.toLong())
        val start = day.atStartOfDay().toInstant(ZoneOffset.UTC)
        val end = start.plusSeconds(24 * 60 * 60)

        return _rewards.value.orEmpty().filter {
            it.type == type && it.createdAt >= start && it.createdAt < end
        }
    }

    /**
     * The most recent thing the wallet has recorded, or the start of today if it has recorded
     * nothing. Winding the device clock backwards puts "now" behind this, which is how a
     * tampered clock is caught.
     */
    fun latestActivity(): Instant {
        val startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC)

        val newest = (_rewards.value.orEmpty().map { it.createdAt } +
            _spends.value.orEmpty().map { it.createdAt }).maxOrNull()

        return maxOf(newest ?: startOfDay, startOfDay)
    }

    private fun total(rewards: List<Reward>, now: Instant = Instant.now()): Long =
        rewards.filter { it.isActiveAt(now) }.sumOf { it.balance }

    // ── Writes ────────────────────────────────────────────────────────────────────

    /** Adds a grant. [key] is deterministic where the grant must not be able to repeat. */
    suspend fun grant(reward: Reward, key: String? = null): Boolean {
        val currentUid = uid ?: return false

        return try {
            val ref = Firebase.database
                .getReference(WalletContract.rewardsPath(currentUid))

            val node = if (key != null) ref.child(key) else ref.push()
            node.setValue(reward.toMap()).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Grant failed", e)
            false
        }
    }

    /** Suspends until the first callback has landed, so a caller never reads an unread wallet. */
    suspend fun awaitLoaded() {
        _rewards.first { it != null }
    }

    /**
     * The whole wallet as it stands.
     *
     * Taken before an account switch: the rules only permit reading your own subtree, so once a
     * different account is signed in the previous one is unreachable and this in-memory copy is
     * all that is left of it.
     */
    fun snapshot(): WalletSnapshot =
        WalletSnapshot(_rewards.value.orEmpty(), _spends.value.orEmpty())

    /**
     * Adds a grant only if [key] has none, for the paths that must never credit twice.
     *
     * A plain write would be idempotent in content but not in effect: repeating it after coins
     * had been spent would reset balance back to amount and hand them back. Waiting for the
     * wallet to load first is what makes the check mean anything — `keepSynced` guarantees the
     * loaded list is the full picture and not a partial cache.
     */
    suspend fun grantOnce(key: String, reward: Reward): Boolean {
        awaitLoaded()
        if (_rewards.value.orEmpty().any { it.key == key }) return false
        return grant(reward, key)
    }

    /**
     * Spends [credits], drawing oldest-expiry-first and overdrawing the longest-living grant
     * when nothing active is left — the same rule the SQL walked, now over the loaded list.
     *
     * The decrements and the spend record go in one [DatabaseReference.updateChildren], which is
     * atomic and queues offline as a unit. Each decrement is a transform, so a second handset
     * doing this at the same time composes with it rather than overwriting it.
     */
    suspend fun consume(credits: Long, type: String, description: String): Boolean {
        val currentUid = uid ?: return false
        if (credits <= 0) return false

        val drawn = plan(credits) ?: return false

        val updates = mutableMapOf<String, Any?>()

        drawn.forEach { (key, taken) ->
            updates[WalletContract.rewardBalancePath(currentUid, key)] =
                ServerValue.increment(-taken)
        }

        val spendKey = Firebase.database
            .getReference(WalletContract.spendsPath(currentUid)).push().key ?: return false

        val spend = Spend(
            key = spendKey,
            amount = credits,
            type = type,
            description = description,
            drawnFrom = drawn.keys.toList(),
            createdAt = Instant.now(),
        )

        updates["${WalletContract.spendsPath(currentUid)}/$spendKey"] = spend.toMap()

        return try {
            Firebase.database.reference.updateChildren(updates).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Consume failed", e)
            false
        }
    }

    /** How many coins come off which grants, or null when there is nothing to draw on at all. */
    private fun plan(credits: Long): Map<String, Long>? {
        val drawn = linkedMapOf<String, Long>()
        var owed = credits

        for (reward in activeRewards()) {
            if (owed <= 0) break
            val take = minOf(reward.balance, owed)
            drawn[reward.key] = take
            owed -= take
        }

        if (owed > 0) {
            // Nothing active covers the rest, so the longest-living grant carries it and goes
            // negative. normalizeOverdrawn shuffles that back once something active exists.
            val fallback = _rewards.value.orEmpty().maxByOrNull { it.expiresAt } ?: return null
            drawn[fallback.key] = (drawn[fallback.key] ?: 0) + owed
        }

        return drawn.ifEmpty { null }
    }

    /**
     * Moves an overdraft off a grant that has gone negative and onto one that has room.
     *
     * Without it an expiring negative simply vanishes and hands back coins that were spent.
     * Both sides are transforms, so two handsets running this at once settle rather than fight;
     * it may take a launch or two to converge, and the total is correct throughout.
     */
    suspend fun normalizeOverdrawn(): Boolean {
        val currentUid = uid ?: return false

        val overdrawn = _rewards.value.orEmpty()
            .filter { it.balance < 0 }
            .minByOrNull { it.expiresAt } ?: return false

        val donor = activeRewards().firstOrNull() ?: return false

        val moved = minOf(-overdrawn.balance, donor.balance)
        if (moved <= 0) return false

        val updates = mapOf<String, Any?>(
            WalletContract.rewardBalancePath(currentUid, overdrawn.key)
                to ServerValue.increment(moved),
            WalletContract.rewardBalancePath(currentUid, donor.key)
                to ServerValue.increment(-moved),
        )

        return try {
            Firebase.database.reference.updateChildren(updates).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Normalise failed", e)
            false
        }
    }
}
