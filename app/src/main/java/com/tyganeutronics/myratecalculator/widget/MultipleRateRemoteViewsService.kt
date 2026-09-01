package com.tyganeutronics.myratecalculator.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.murgupluoglu.flagkit.FlagKit
import com.tyganeutronics.myratecalculator.AppZimRate
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.utils.CurrencyFlagUtil
import java.math.RoundingMode

class MultipleRateRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext)
}

private class Factory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var rates = listOf<com.tyganeutronics.myratecalculator.database.entities.RateEntity>()

    override fun onCreate() = reload()

    override fun onDataSetChanged() = reload()

    override fun onDestroy() {}

    override fun getCount() = rates.size

    override fun getViewAt(position: Int): RemoteViews {
        val entity = rates[position]
        val views = RemoteViews(context.packageName, R.layout.widget_multiple_item)

        val countryCode = CurrencyFlagUtil.countryCode(entity.currency)
        val flagRes = if (countryCode.isNotEmpty()) FlagKit.getResId(context, countryCode) else 0
        views.setImageViewResource(R.id.img_widget_item_flag, if (flagRes != 0) flagRes else R.mipmap.ic_launcher)

        views.setTextViewText(R.id.txt_widget_item_code, CurrencyFlagUtil.countryName(entity.currency))
        views.setTextViewText(
            R.id.txt_widget_item_rate,
            entity.rate.setScale(2, RoundingMode.HALF_UP).toPlainString()
        )

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount() = 1

    override fun getItemId(position: Int) = rates[position].id

    override fun hasStableIds() = true

    private fun reload() {
        rates = try {
            AppZimRate.database.rates().getAllPinned()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
