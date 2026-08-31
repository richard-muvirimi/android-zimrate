package com.tyganeutronics.myratecalculator.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.RemoteViews
import androidx.appcompat.widget.AppCompatButton
import com.murgupluoglu.flagkit.FlagKit
import com.tyganeutronics.myratecalculator.AppZimRate
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.ui.base.BaseAppActivity
import com.tyganeutronics.myratecalculator.utils.CurrencyFlagUtil
import com.tyganeutronics.myratecalculator.utils.WidgetUtils
import com.tyganeutronics.myratecalculator.utils.traits.getStringPref
import com.tyganeutronics.myratecalculator.utils.traits.putStringPref

class WidgetActivity : BaseAppActivity(), View.OnClickListener {

    private var mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val rates = mutableListOf<RateEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.widget_configure)
    }

    override fun bindViews() {
        mAppWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val current = getStringPref("widget-$mAppWidgetId", "")

        // Blank placeholder only for a brand new widget. On a reconfigure this would wipe a
        // working widget, and cancelling would leave it wiped.
        if (current.isEmpty()) {
            RemoteViews(packageName, R.layout.widget_single).also { views ->
                getWidgetManager().updateAppWidget(mAppWidgetId, views)
            }
        }

        rates.clear()
        rates.addAll(AppZimRate.database.rates().getAll().sortedBy { it.currency })

        val lv = findViewById<ListView>(R.id.lv_currencies)
        lv.adapter = CurrencyAdapter(this, rates)

        // Reopened from the widget's reconfigure action — start on its current currency
        // rather than the top of the list.
        if (rates.isNotEmpty()) {
            val selected = rates.indexOfFirst { it.currency == current }
            lv.setItemChecked(if (selected >= 0) selected else 0, true)
            if (selected > 0) lv.setSelection(selected)
        }
    }

    override fun syncViews() {
        findViewById<AppCompatButton>(R.id.btn_add).setOnClickListener(this)
    }

    private fun getWidgetManager(): AppWidgetManager = AppWidgetManager.getInstance(this)

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_add -> {
                val lv = findViewById<ListView>(R.id.lv_currencies)
                val pos = lv.checkedItemPosition
                if (pos < 0 || pos >= rates.size) return

                putStringPref("widget-$mAppWidgetId", rates[pos].currency)

                // A first-time configure gets an onUpdate from the host, but a reconfigure
                // does not — redraw here so the change shows either way.
                WidgetUtils.refreshAll(this)

                setResult(Activity.RESULT_OK, Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId)
                })
                finish()
            }
        }
    }

    private class CurrencyAdapter(
        context: Context,
        items: List<RateEntity>,
    ) : ArrayAdapter<RateEntity>(context, R.layout.item_widget_currency, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as CheckedTextView
            val entity = getItem(position) ?: return view

            val countryCode = CurrencyFlagUtil.countryCode(entity.currency)
            val flagRes = if (countryCode.isNotEmpty()) FlagKit.getResId(context, countryCode) else 0
            view.setCompoundDrawablesWithIntrinsicBounds(
                if (flagRes != 0) flagRes else R.mipmap.ic_launcher, 0, 0, 0
            )
            view.text = context.getString(
                R.string.widget_currency_item,
                entity.currency,
                entity.name.ifEmpty { entity.currency },
            )
            return view
        }
    }
}
