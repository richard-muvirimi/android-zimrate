package com.tyganeutronics.myratecalculator.database.rtdb

import com.google.firebase.database.DataSnapshot
import java.time.Instant

/**
 * A grant. [balance] is what remains of [amount] after spends have drawn on it, and it is the
 * one field that must never be written as an absolute value — see [WalletRepository.consume].
 */
data class Reward(
    val key: String,
    val amount: Long,
    val balance: Long,
    val type: String,
    val description: String,
    val expiresAt: Instant,
    val createdAt: Instant,
) {
    /** RecyclerView wants a stable Long; the key is a String, so fold it into one. */
    val itemId: Long get() = key.hashCode().toLong()

    fun isActiveAt(now: Instant) = expiresAt >= now

    companion object {
        fun from(snapshot: DataSnapshot): Reward? {
            val key = snapshot.key ?: return null

            return Reward(
                key = key,
                amount = snapshot.long(WalletContract.AMOUNT),
                balance = snapshot.long(WalletContract.BALANCE),
                type = snapshot.string(WalletContract.TYPE),
                description = snapshot.string(WalletContract.DESCRIPTION),
                expiresAt = snapshot.instant(WalletContract.EXPIRES_AT),
                createdAt = snapshot.instant(WalletContract.CREATED_AT),
            )
        }
    }

    fun toMap(): Map<String, Any> = mapOf(
        WalletContract.AMOUNT to amount,
        WalletContract.BALANCE to balance,
        WalletContract.TYPE to type,
        WalletContract.DESCRIPTION to description,
        WalletContract.EXPIRES_AT to expiresAt.epochSecond,
        WalletContract.CREATED_AT to createdAt.epochSecond,
        WalletContract.PURGE_AT to expiresAt.plusSeconds(
            WalletContract.RETENTION_DAYS * 24 * 60 * 60
        ).epochSecond,
    )
}

/** A spend, naming the grants it drew from so history can show where the coins went. */
data class Spend(
    val key: String,
    val amount: Long,
    val type: String,
    val description: String,
    val drawnFrom: List<String>,
    val createdAt: Instant,
) {
    val itemId: Long get() = key.hashCode().toLong()

    companion object {
        fun from(snapshot: DataSnapshot): Spend? {
            val key = snapshot.key ?: return null

            return Spend(
                key = key,
                amount = snapshot.long(WalletContract.AMOUNT),
                type = snapshot.string(WalletContract.TYPE),
                description = snapshot.string(WalletContract.DESCRIPTION),
                // Written as a set rather than a list — Realtime Database turns arrays into
                // objects with numeric keys and back again, which reorders badly.
                drawnFrom = snapshot.child(WalletContract.DRAWN_FROM).children
                    .mapNotNull { it.key },
                createdAt = snapshot.instant(WalletContract.CREATED_AT),
            )
        }
    }

    fun toMap(): Map<String, Any> = mapOf(
        WalletContract.AMOUNT to amount,
        WalletContract.TYPE to type,
        WalletContract.DESCRIPTION to description,
        WalletContract.DRAWN_FROM to drawnFrom.associateWith { true },
        WalletContract.CREATED_AT to createdAt.epochSecond,
        WalletContract.PURGE_AT to createdAt.plusSeconds(
            WalletContract.RETENTION_DAYS * 24 * 60 * 60
        ).epochSecond,
    )
}

/**
 * Realtime Database hands a number back as Long when it is integral and Double when it is not,
 * and asking for the wrong one throws. Going through Number covers both.
 */
private fun DataSnapshot.long(field: String): Long =
    (child(field).value as? Number)?.toLong() ?: 0L

private fun DataSnapshot.string(field: String): String =
    child(field).value as? String ?: ""

private fun DataSnapshot.instant(field: String): Instant =
    Instant.ofEpochSecond(long(field))

/** Everything a wallet holds, for carrying it to another account. See [Reward] on balances. */
data class WalletSnapshot(
    val rewards: List<Reward>,
    val spends: List<Spend>,
) {
    val isEmpty: Boolean get() = rewards.isEmpty() && spends.isEmpty()
}
