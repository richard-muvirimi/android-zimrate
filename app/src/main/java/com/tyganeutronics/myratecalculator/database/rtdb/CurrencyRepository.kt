package com.tyganeutronics.myratecalculator.database.rtdb

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * The user's currencies, held in memory from a listener on their subtree.
 *
 * Ordering stayed on each node rather than being lifted into a separate list, which is a change
 * from the Firestore draft of this plan. There, `normalizeVisibleSortOrder` rewriting every
 * visible row on every pin was sixty billed writes; here one [DatabaseReference.updateChildren]
 * carries all thirty paths as a single atomic call at no per-operation cost, so the Room shape
 * survives untouched and a whole section of redesign was deleted rather than implemented.
 */
object CurrencyRepository {

    private const val TAG = "CurrencyRepository"

    private var reference: DatabaseReference? = null
    private var listener: ValueEventListener? = null
    private var uid: String? = null

    private val _currencies = MutableStateFlow<List<RateEntity>?>(null)

    /** Null until the first callback — an unread list is not an empty one. */
    val currencies: StateFlow<List<RateEntity>?> = _currencies.asStateFlow()

    val loaded: Boolean get() = _currencies.value != null

    /** Visible rates, USD first, then pinned, then the rest — the tiering the SQL used to sort. */
    val visible = _currencies.map { list ->
        list.orEmpty().filterNot { it.hidden }.sortedWith(displayOrder)
    }

    val hidden = _currencies.map { list ->
        list.orEmpty().filter { it.hidden }.sortedBy { it.currency }
    }

    private val displayOrder = compareBy<RateEntity>(
        { it.currency != "USD" },
        { !it.pinned },
        { it.sortOrder },
        { it.currency },
    )

    fun attach(uid: String) {
        if (this.uid == uid) return
        detach()

        this.uid = uid

        val ref = Firebase.database.getReference(WalletContract.currenciesPath(uid))
        ref.keepSynced(true)

        val valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _currencies.value = snapshot.children.mapNotNull { CurrencyMapping.from(it) }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Currency listener cancelled", error.toException())
            }
        }

        ref.addValueEventListener(valueListener)

        reference = ref
        listener = valueListener
    }

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
        _currencies.value = null
    }

    suspend fun awaitLoaded() {
        _currencies.first { it != null }
    }

    /**
     * What is loaded right now.
     *
     * Taken before an account switch, because the rules only permit reading your own subtree —
     * the instant a different account is signed in, the previous one's data is unreachable, and
     * this in-memory copy is the only thing left of it.
     */
    fun snapshot(): List<RateEntity> = _currencies.value.orEmpty()

    // ── Reads ─────────────────────────────────────────────────────────────────────

    fun all(): List<RateEntity> = _currencies.value.orEmpty().filterNot { it.hidden }

    fun allPinned(): List<RateEntity> =
        _currencies.value.orEmpty()
            .filter { !it.hidden && it.pinned }
            .sortedBy { it.sortOrder }

    fun findByCurrency(code: String): RateEntity? =
        _currencies.value.orEmpty().firstOrNull { it.currency == code }

    fun visibleSorted(): List<RateEntity> =
        _currencies.value.orEmpty().filterNot { it.hidden }.sortedWith(displayOrder)

    // ── Writes ────────────────────────────────────────────────────────────────────

    /** Writes a whole currency node. */
    suspend fun put(entity: RateEntity): Boolean = apply(
        mapOf(pathOf(entity.currency) to CurrencyMapping.toMap(entity))
    )

    suspend fun setField(code: String, field: String, value: Any): Boolean =
        apply(mapOf("${pathOf(code)}/$field" to value))

    suspend fun delete(code: String): Boolean = apply(mapOf(pathOf(code) to null))

    /**
     * Renumbers every visible currency in one atomic call.
     *
     * This is what `applyTieredSortOrder` did a row at a time. Sending it as one multi-path
     * update means the list never renders half-renumbered, and USD is forced pinned here so the
     * rule lives in one place rather than in each caller.
     */
    suspend fun applyOrder(ordered: List<RateEntity>): Boolean {
        val currentUid = uid ?: return false

        val updates = mutableMapOf<String, Any?>()

        ordered.forEachIndexed { index, entity ->
            val base = WalletContract.currencyPath(currentUid, entity.currency)
            updates["$base/${WalletContract.SORT_ORDER}"] = index
            updates["$base/${WalletContract.PINNED}"] =
                entity.currency == "USD" || entity.pinned
        }

        return commit(updates)
    }

    /**
     * Upserts fetched rates, carrying over the pin, hide and order the user set, and skipping
     * any currency whose values did not actually move.
     *
     * The skip is not about cost here — writes are not billed per operation — but about not
     * telling the widget and the watch that something changed when nothing did.
     */
    suspend fun saveFetched(incoming: List<RateEntity>): Boolean {
        val currentUid = uid ?: return false
        awaitLoaded()

        val now = Instant.now()
        val updates = mutableMapOf<String, Any?>()

        incoming.forEach { fetched ->
            val existing = findByCurrency(fetched.currency)

            // A currency the user defined owns that code outright. Should the server ever start
            // returning it, their row stays as they typed it rather than being overwritten.
            if (existing?.custom == true) return@forEach

            if (existing != null && !CurrencyMapping.differs(fetched, existing)) return@forEach

            val merged = fetched.duplicateWithPinned(
                pinned = fetched.currency == "USD" || existing?.pinned ?: false
            ).apply {
                hidden = existing?.hidden ?: false
                sortOrder = existing?.sortOrder ?: Int.MAX_VALUE
                createdAt = existing?.createdAt?.takeIf { it > Instant.EPOCH } ?: now
                updatedAt = now
                custom = false
            }

            updates[WalletContract.currencyPath(currentUid, merged.currency)] =
                CurrencyMapping.toMap(merged)
        }

        if (updates.isEmpty()) return true

        return commit(updates)
    }

    private fun pathOf(code: String): String {
        val currentUid = uid ?: return ""
        return WalletContract.currencyPath(currentUid, code)
    }

    private suspend fun apply(updates: Map<String, Any?>): Boolean {
        if (uid == null) return false
        return commit(updates)
    }

    private suspend fun commit(updates: Map<String, Any?>): Boolean {
        if (updates.isEmpty()) return true

        return try {
            Firebase.database.reference.updateChildren(updates).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Currency write failed", e)
            false
        }
    }
}
