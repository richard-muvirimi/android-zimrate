package com.tyganeutronics.myratecalculator.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.TypeConverters
import com.tyganeutronics.myratecalculator.database.Database
import com.tyganeutronics.myratecalculator.database.contract.RatesContract
import com.tyganeutronics.myratecalculator.database.converter.BigDecimalConverter
import com.tyganeutronics.myratecalculator.database.converter.InstantConverter
import com.tyganeutronics.myratecalculator.utils.traits.optBigDecimal
import com.tyganeutronics.myratecalculator.utils.traits.optInstant
import com.tyganeutronics.myratecalculator.utils.traits.putBigDecimal
import com.tyganeutronics.myratecalculator.utils.traits.putInstant
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant

@Entity(tableName = RatesContract.TABLE_NAME)
@TypeConverters(value = [InstantConverter::class, BigDecimalConverter::class])
open class RateEntity : BaseEntity() {

    @ColumnInfo(name = RatesContract.COLUMN_NAME_URL)
    var url: String = ""

    @ColumnInfo(name = RatesContract.COLUMN_NAME_NAME)
    var name: String = ""

    @ColumnInfo(name = RatesContract.COLUMN_NAME_CURRENCY)
    var currency: String = ""

    @ColumnInfo(name = RatesContract.COLUMN_NAME_RATE)
    var rate: BigDecimal = BigDecimal(0)

    @ColumnInfo(name = RatesContract.COLUMN_NAME_LAST_RATE)
    var lastRate: BigDecimal = BigDecimal(0)

    @ColumnInfo(name = RatesContract.COLUMN_NAME_LAST_CHECKED)
    var lastChecked: Instant = Instant.MIN

    @ColumnInfo(name = RatesContract.COLUMN_NAME_PINNED)
    var pinned: Boolean = false

    @ColumnInfo(name = RatesContract.COLUMN_NAME_HIDDEN)
    var hidden: Boolean = false

    @ColumnInfo(name = RatesContract.COLUMN_NAME_SORT_ORDER)
    var sortOrder: Int = Int.MAX_VALUE

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
        }
    }

    override fun doInsert(database: Database) {
        database.rates().insert(this)
    }

    override fun doUpdate(database: Database) {
        database.rates().update(this)
    }

    override fun doDelete(database: Database) {
        database.rates().delete(this)
    }

    override fun toJson(): String {
        val jsonObject = JSONObject(super.toJson())
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

    override fun fromJson(json: String) {
        super.fromJson(json)

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
