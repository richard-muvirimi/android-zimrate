package com.tyganeutronics.myratecalculator.database.rtdb

import com.google.firebase.database.DataSnapshot
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import java.math.BigDecimal
import java.time.Instant

/**
 * Maps a currency node onto [RateEntity], which stays the type the rest of the app speaks.
 *
 * Keeping the existing class means the adapters, view holders, glance bubbles, watch sync and
 * widgets all carry on unchanged; only the code that used to reach for the DAO has to move. Its
 * Room annotations are inert from here on — nothing calls `save()` any more — and phase 4
 * removes them along with the database itself.
 */
object CurrencyMapping {

    fun from(snapshot: DataSnapshot): RateEntity? {
        val code = snapshot.key ?: return null

        return RateEntity().apply {
            currency = code
            // Stable across devices and restarts, unlike the autoincrement row id it replaces,
            // which RecyclerView wants for setHasStableIds.
            id = code.hashCode().toLong()
            name = snapshot.string(WalletContract.NAME)
            url = snapshot.string(WalletContract.URL)
            rate = snapshot.decimal(WalletContract.RATE)
            lastRate = snapshot.decimal(WalletContract.LAST_RATE)
            lastChecked = snapshot.instant(WalletContract.LAST_CHECKED)
            pinned = snapshot.bool(WalletContract.PINNED)
            hidden = snapshot.bool(WalletContract.HIDDEN)
            custom = snapshot.bool(WalletContract.CUSTOM)
            sortOrder = snapshot.long(WalletContract.SORT_ORDER).toInt()
            createdAt = snapshot.instant(WalletContract.CREATED_AT)
            updatedAt = snapshot.instant(WalletContract.UPDATED_AT)
        }
    }

    fun toMap(entity: RateEntity): Map<String, Any> = mapOf(
        WalletContract.NAME to entity.name,
        WalletContract.URL to entity.url,
        // toPlainString, never toDouble — this is the whole reason rates are stored as text.
        WalletContract.RATE to entity.rate.toPlainString(),
        WalletContract.LAST_RATE to entity.lastRate.toPlainString(),
        WalletContract.LAST_CHECKED to entity.lastChecked.orZero(),
        WalletContract.PINNED to entity.pinned,
        WalletContract.HIDDEN to entity.hidden,
        WalletContract.CUSTOM to entity.custom,
        WalletContract.SORT_ORDER to entity.sortOrder,
        WalletContract.CREATED_AT to entity.createdAt.orZero(),
        WalletContract.UPDATED_AT to entity.updatedAt.orZero(),
    )

    /** True when the server actually moved something worth writing back. */
    fun differs(incoming: RateEntity, existing: RateEntity): Boolean =
        incoming.rate.compareTo(existing.rate) != 0 ||
            incoming.lastRate.compareTo(existing.lastRate) != 0 ||
            incoming.name != existing.name ||
            incoming.url != existing.url
}

/**
 * `Instant.MIN` is the "never" sentinel the Room entities carry, and it is nowhere near
 * representable — epoch second of roughly minus thirty-one billion years. Zero says the same
 * thing, and the watch and the widgets already read zero as unknown.
 */
private fun Instant.orZero(): Long = if (this > Instant.EPOCH) epochSecond else 0L

private fun DataSnapshot.long(field: String): Long =
    (child(field).value as? Number)?.toLong() ?: 0L

private fun DataSnapshot.string(field: String): String =
    child(field).value as? String ?: ""

private fun DataSnapshot.bool(field: String): Boolean =
    child(field).value as? Boolean ?: false

private fun DataSnapshot.instant(field: String): Instant =
    Instant.ofEpochSecond(long(field))

private fun DataSnapshot.decimal(field: String): BigDecimal =
    (child(field).value as? String)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
