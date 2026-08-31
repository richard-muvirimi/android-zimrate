package com.tyganeutronics.myratecalculator.wear.complication

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tyganeutronics.myratecalculator.wear.R
import com.tyganeutronics.myratecalculator.wear.data.WearRateModel
import com.tyganeutronics.myratecalculator.wear.data.WearRateStore
import com.tyganeutronics.myratecalculator.wear.presentation.theme.ZimRateWearTheme

class ComplicationConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rates = WearRateStore.load(this)
        setContent {
            ZimRateWearTheme {
                CurrencyPickerScreen(rates) { selected ->
                    WearRateStore.setSelectedCurrency(this, selected)
                    RateComplicationService.notifyUpdate(this)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
        }
    }
}

@Composable
private fun CurrencyPickerScreen(
    rates: List<WearRateModel>,
    onPick: (String) -> Unit,
) {
    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ListHeader {
                Text(
                    text = stringResource(R.string.pick_currency),
                    fontSize = 12.sp,
                )
            }
        }
        items(rates) { rate ->
            Text(
                text = "${rate.currency}  ${rate.name.ifEmpty { rate.currency }}",
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(rate.currency) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
