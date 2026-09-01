package com.tyganeutronics.myratecalculator.wear.complication

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import com.tyganeutronics.myratecalculator.wear.R
import com.tyganeutronics.myratecalculator.wear.data.WearRateModel
import com.tyganeutronics.myratecalculator.wear.data.WearRateStore
import com.tyganeutronics.myratecalculator.wear.presentation.RateRow
import com.tyganeutronics.myratecalculator.wear.presentation.theme.ZimRateWearTheme

class ComplicationConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Identifies which slot on the watch face is being configured, so two ZimRate
        // complications on the same face can show different currencies.
        val instanceId = intent.getIntExtra(
            ComplicationDataSourceService.EXTRA_CONFIG_COMPLICATION_ID,
            NO_INSTANCE_ID,
        )

        val rates = WearRateStore.load(this)
        val selected = WearRateStore.selectedCurrency(this, instanceId)

        setContent {
            ZimRateWearTheme {
                CurrencyPickerScreen(rates, selected) { picked ->
                    WearRateStore.setSelectedCurrency(this, instanceId, picked)
                    RateComplicationService.notifyUpdate(this, instanceId)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
        }
    }

    companion object {
        private const val NO_INSTANCE_ID = -1
    }
}

@Composable
private fun CurrencyPickerScreen(
    rates: List<WearRateModel>,
    selected: String?,
    onPick: (String) -> Unit,
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Denser than this is hard to hit accurately on a watch.
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            ListHeader {
                Text(
                    text = stringResource(R.string.pick_currency),
                    fontSize = 12.sp,
                )
            }
        }

        if (rates.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_rates),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        } else {
            // Same row treatment as the main screen: flag, code and the current rate.
            items(rates) { rate ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(rate.currency) }
                        // Applied inside the clickable, so it grows the touch target rather
                        // than just insetting the text. RateRow alone is about 21dp tall.
                        .padding(vertical = 12.dp),
                ) {
                    RateRow(rate, highlighted = rate.currency == selected)
                }
            }
        }
    }
}
