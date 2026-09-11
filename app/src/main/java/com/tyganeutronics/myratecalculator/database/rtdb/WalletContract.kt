package com.tyganeutronics.myratecalculator.database.rtdb

/**
 * Node paths and field names for the wallet.
 *
 * Mirrors the Room schema a table at a time, so the port is a change of store rather than a
 * change of shape. Times are plain epoch seconds, exactly as `InstantConverter` already writes
 * them — Realtime Database has no timestamp type, which means the existing conversion carries
 * across untouched and there is no year-range trap of the sort a Firestore Timestamp brings.
 */
object WalletContract {

    const val USERS = "users"

    const val REWARDS = "rewards"
    const val SPENDS = "spends"
    const val CURRENCIES = "currencies"
    const val APPDATA = "appdata"

    const val AMOUNT = "amount"
    const val BALANCE = "balance"
    const val TYPE = "type"
    const val DESCRIPTION = "description"
    const val EXPIRES_AT = "expiresAt"
    const val CREATED_AT = "createdAt"
    const val DRAWN_FROM = "rewards"

    /**
     * What the scheduled sweep reads, and deliberately not [EXPIRES_AT].
     *
     * An expired grant still has to appear in history and still has to be visible to the
     * overdraw normaliser, so purging on expiry would take rows that are still doing work.
     * Six months past expiry reproduces what `cleanExpired()` did on every app start.
     */
    const val PURGE_AT = "purgeAt"

    /** Matches the six month retention the Room `cleanExpired` queries used. */
    const val RETENTION_DAYS = 183L

    fun userPath(uid: String) = "$USERS/$uid"

    fun rewardsPath(uid: String) = "${userPath(uid)}/$REWARDS"

    fun spendsPath(uid: String) = "${userPath(uid)}/$SPENDS"

    fun rewardBalancePath(uid: String, key: String) = "${rewardsPath(uid)}/$key/$BALANCE"

    fun currenciesPath(uid: String) = "${userPath(uid)}/$CURRENCIES"

    fun currencyPath(uid: String, code: String) = "${currenciesPath(uid)}/$code"

    // Currency fields. Rates are strings because JSON has one numeric type and it is a double;
    // an exchange rate that round-trips through a double does not come back the same.
    const val NAME = "name"
    const val URL = "url"
    const val RATE = "rate"
    const val LAST_RATE = "lastRate"
    const val LAST_CHECKED = "lastChecked"
    const val PINNED = "pinned"
    const val HIDDEN = "hidden"
    const val CUSTOM = "custom"
    const val SORT_ORDER = "sortOrder"
    const val UPDATED_AT = "updatedAt"
}
