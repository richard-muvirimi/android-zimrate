package com.tyganeutronics.myratecalculator.database.entities

import com.tyganeutronics.myratecalculator.database.contract.RatesContract
import com.tyganeutronics.myratecalculator.utils.traits.optBigDecimal
import com.tyganeutronics.myratecalculator.utils.traits.optInstant
import com.tyganeutronics.myratecalculator.utils.traits.putBigDecimal
import com.tyganeutronics.myratecalculator.utils.traits.putInstant
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant

/**
 * A currency and its rate, as the app passes it around.
 *
 * Was a Room entity; the annotations, the base class and the save/insert/update helpers went
 * with the database. It is now a plain holder that
 * [com.tyganeutronics.myratecalculator.database.rtdb.CurrencyMapping] fills from the tree, which
 * is why the adapters, view holders, glance bubbles, watch sync and widgets all carried across
 * the store change untouched.
 */
open class RateEntity {

    /** A hash of [currency], so RecyclerView's stable ids survive a reinstall. */
    var id: Long = 0

    var createdAt: Instant = Instant.EPOCH

    var updatedAt: Instant = Instant.EPOCH

    var url: String = ""

    var name: String = ""

    var currency: String = ""

    var rate: BigDecimal = BigDecimal(0)

    var lastRate: BigDecimal = BigDecimal(0)

    var lastChecked: Instant = Instant.EPOCH

    var pinned: Boolean = false

    var hidden: Boolean = false

    var sortOrder: Int = Int.MAX_VALUE

    /** Entered by the user rather than returned by the API. Server rates never overwrite one. */
    var custom: Boolean = false

    fun duplicateWithPinned(pinned: Boolean = this.pinned): RateEntity {
        return RateEntity().also { copy ->
            copy.id = id
            copy.createdAt = createdAt
            copy.updatedAt = updatedAt
            copy.url = url
            copy.name = name
            copy.currency = currency
            copy.rate = rate
            copy.lastRate = lastRate
            copy.lastChecked = lastChecked
            copy.pinned = pinned
            copy.hidden = hidden
            copy.sortOrder = sortOrder
            copy.custom = custom
        }
    }

    open fun toJson(): String {
        val jsonObject = JSONObject()
        try {
            jsonObject.put(RatesContract.COLUMN_NAME_NAME, name)
            jsonObject.put(RatesContract.COLUMN_NAME_URL, url)
            jsonObject.put(RatesContract.COLUMN_NAME_CURRENCY, currency)
            jsonObject.putBigDecimal(RatesContract.COLUMN_NAME_RATE, rate)
            jsonObject.putBigDecimal(RatesContract.COLUMN_NAME_LAST_RATE, lastRate)
            jsonObject.putInstant(RatesContract.COLUMN_NAME_LAST_CHECKED, lastChecked)
            jsonObject.put(RatesContract.COLUMN_NAME_PINNED, pinned)
        } catch (je: JSONException) {
            je.printStackTrace()
        }
        return jsonObject.toString()
    }

    open fun fromJson(json: String) {
        try {
            val jsonObject = JSONObject(json)

            name = jsonObject.optString(RatesContract.COLUMN_NAME_NAME, "")
            url = jsonObject.optString(RatesContract.COLUMN_NAME_URL, "")
            currency = jsonObject.optString(RatesContract.COLUMN_NAME_CURRENCY, "")
            rate = jsonObject.optBigDecimal(RatesContract.COLUMN_NAME_RATE, BigDecimal(1))
            lastRate = jsonObject.optBigDecimal(RatesContract.COLUMN_NAME_LAST_RATE, BigDecimal(1))
            lastChecked = jsonObject
                .optInstant(RatesContract.COLUMN_NAME_LAST_CHECKED)
                .coerceAtMost(Instant.now())
            pinned = jsonObject.optBoolean(RatesContract.COLUMN_NAME_PINNED, false)

        } catch (je: JSONException) {
            je.printStackTrace()
        }
    }
}
