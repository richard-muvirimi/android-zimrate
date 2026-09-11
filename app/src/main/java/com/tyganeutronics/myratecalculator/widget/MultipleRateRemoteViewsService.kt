package com.tyganeutronics.myratecalculator.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.tyganeutronics.myratecalculator.database.rtdb.CurrencyRepository
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.utils.WidgetUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.math.RoundingMode

class MultipleRateRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext)
}

private class Factory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var rates = listOf<RateEntity>()

    override fun onCreate() = reload()

    override fun onDataSetChanged() = reload()

    override fun onDestroy() {}

    override fun getCount() = rates.size

    override fun getViewAt(position: Int): RemoteViews {
        val entity = rates[position]
        val views = RemoteViews(context.packageName, R.layout.widget_multiple_item)

        views.setImageViewResource(
            R.id.img_widget_item_flag,
            WidgetUtils.flagRes(context, entity.currency, entity.custom),
        )

        views.setTextViewText(
            R.id.txt_widget_item_code,
            WidgetUtils.label(context, entity.currency, entity.custom, entity.name),
        )
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

    /**
     * Blocking on purpose. [RemoteViewsService.RemoteViewsFactory.onDataSetChanged] is handed a
     * binder thread precisely so it can do expensive work synchronously, which is why this class
     * needed no restructuring when the store changed — unlike the providers, it was never on the
     * broadcast thread. The timeout is only there so a listener that never fires cannot wedge
     * the thread.
     */
    private fun reload() {
        rates = runBlocking {
            withTimeoutOrNull(WidgetUtils.READ_TIMEOUT_MS) {
                CurrencyRepository.awaitLoaded()
                CurrencyRepository.allPinned()
            } ?: emptyList()
        }
    }
}
