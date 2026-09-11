package com.tyganeutronics.myratecalculator.migration

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.rtdb.Reward
import com.tyganeutronics.myratecalculator.database.rtdb.Spend
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant

/** Everything the old database held, ready to be written straight into the tree. */
data class LegacyData(
    val currencies: List<RateEntity>,
    val rewards: List<Reward>,
    val spends: List<Spend>,
) {
    val isEmpty: Boolean get() = currencies.isEmpty() && rewards.isEmpty() && spends.isEmpty()
}

/**
 * Reads the Room database one last time, with plain SQLite rather than Room.
 *
 * The v5 schema is known and fixed, so this is a few cursors — and doing it this way means Room,
 * kapt, the entities, the DAOs and the schema plumbing can all leave in the same release rather
 * than lingering for one more just to read a file that is about to be deleted.
 */
object LegacyDatabase {

    private const val TAG = "LegacyDatabase"
    private const val NAME = "data.db"

    fun exists(context: Context): Boolean = context.getDatabasePath(NAME).exists()

    fun delete(context: Context): Boolean = context.deleteDatabase(NAME)

    fun read(context: Context): LegacyData? {
        if (!exists(context)) return LegacyData(emptyList(), emptyList(), emptyList())

        var db: SQLiteDatabase? = null

        return try {
            // Opened writable rather than read-only: Room leaves a write-ahead log beside the
            // file, and recovering one needs write access.
            db = SQLiteDatabase.openDatabase(
                context.getDatabasePath(NAME).path,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )

            LegacyData(
                currencies = db.readCurrencies(),
                rewards = db.readRewards(),
                spends = db.readSpends(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the legacy database", e)
            null
        } finally {
            db?.close()
        }
    }

    private fun SQLiteDatabase.readCurrencies(): List<RateEntity> =
        query("rates").use { cursor ->
            cursor.map {
                RateEntity().apply {
                    currency = it.text("currency")
                    id = currency.hashCode().toLong()
                    name = it.text("name")
                    url = it.text("url")
                    rate = it.decimal("rate")
                    lastRate = it.decimal("last_rate")
                    lastChecked = it.instant("last_checked")
                    pinned = it.int("pinned") != 0
                    hidden = it.int("hidden") != 0
                    custom = it.int("custom") != 0
                    sortOrder = it.int("sort_order")
                    createdAt = it.instant("created_at")
                    updatedAt = it.instant("updated_at")
                }
            }.filter { it.currency.isNotEmpty() }
        }

    private fun SQLiteDatabase.readRewards(): List<Reward> =
        query("rewards").use { cursor ->
            cursor.map {
                val amount = it.long("amount")
                val type = it.text("type")
                val createdAt = it.instant("created_at")
                val expiresAt = it.instant("expires_at")

                Reward(
                    key = key("m", type, amount, createdAt, expiresAt),
                    amount = amount,
                    // The remaining balance, not the original amount — the arithmetic already
                    // netted out locally, so replaying spends against grants would only risk
                    // arriving at a different answer than the user has been looking at.
                    balance = it.long("balance"),
                    type = type,
                    description = it.text("description"),
                    expiresAt = expiresAt,
                    createdAt = createdAt,
                )
            }
        }

    private fun SQLiteDatabase.readSpends(): List<Spend> =
        query("purchases").use { cursor ->
            cursor.map {
                val amount = it.long("amount")
                val type = it.text("type")
                val createdAt = it.instant("created_at")

                Spend(
                    key = key("ms", type, amount, createdAt, Instant.EPOCH),
                    amount = amount,
                    type = type,
                    description = it.text("description"),
                    // The grants these drew on were keyed by row id, which no longer means
                    // anything. History still shows what was spent and when; only the link back
                    // to individual grants is dropped, and nothing reads it.
                    drawnFrom = emptyList(),
                    createdAt = createdAt,
                )
            }
        }

    /**
     * A key hashed from the fields that cannot change, so re-running the migration overwrites
     * rather than duplicating — and two handsets restored from the same backup converge instead
     * of double-crediting.
     *
     * Deliberately not the local `_id`: those are per-device autoincrement, so two genuinely
     * different histories would collide on them and one device's grants would silently vanish.
     * Balance is excluded for the opposite reason — it is the one field that does change.
     */
    private fun key(prefix: String, type: String, amount: Long, created: Instant, expires: Instant): String {
        val material = "$type|$amount|${created.epochSecond}|${expires.epochSecond}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))

        return prefix + "_" + digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private fun SQLiteDatabase.query(table: String): Cursor =
        query(table, null, null, null, null, null, null)

    private fun <T> Cursor.map(transform: (Cursor) -> T): List<T> {
        val items = mutableListOf<T>()
        while (moveToNext()) items.add(transform(this))
        return items
    }

    private fun Cursor.text(column: String): String {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) "" else getString(index)
    }

    private fun Cursor.long(column: String): Long {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0L else getLong(index)
    }

    private fun Cursor.int(column: String): Int {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0 else getInt(index)
    }

    private fun Cursor.instant(column: String): Instant = Instant.ofEpochSecond(long(column))

    private fun Cursor.decimal(column: String): BigDecimal =
        text(column).toBigDecimalOrNull() ?: BigDecimal.ZERO
}
