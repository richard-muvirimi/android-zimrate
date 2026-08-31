package com.tyganeutronics.myratecalculator.wear.presentation

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.murgupluoglu.flagkit.FlagKit
import com.tyganeutronics.myratecalculator.wear.R
import com.tyganeutronics.myratecalculator.wear.data.WearRateModel
import com.tyganeutronics.myratecalculator.wear.data.WearRateStore
import com.tyganeutronics.myratecalculator.wear.presentation.theme.ZimRateWearTheme
import com.tyganeutronics.myratecalculator.wear.util.countryCode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZimRateWearTheme {
                RatesScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh by recomposing — state is read in RatesScreen
        setContent {
            ZimRateWearTheme {
                RatesScreen()
            }
        }
    }
}

@Composable
fun RatesScreen() {
    val context = LocalContext.current
    val rates = WearRateStore.load(context)
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            ListHeader {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.title3,
                )
            }
        }

        if (rates.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_rates),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        } else {
            items(rates) { rate ->
                RateRow(rate)
            }

            item {
                Spacer(Modifier.height(4.dp))
                val lastChecked = rates.maxOfOrNull { it.lastChecked } ?: 0L
                if (lastChecked > 0L) {
                    Text(
                        text = stringResource(
                            R.string.updated_at,
                            DateUtils.getRelativeTimeSpanString(lastChecked),
                        ),
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun RateRow(rate: WearRateModel) {
    val context = LocalContext.current
    var flagBitmap by remember(rate.currency) {
        mutableStateOf(
            runCatching {
                val code = countryCode(rate.currency)
                val resId = if (code.isNotEmpty()) FlagKit.getResId(context, code) else 0
                if (resId != 0) context.getDrawable(resId)?.toBitmap() else null
            }.getOrNull()
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (flagBitmap != null) {
                Image(
                    bitmap = flagBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(width = 24.dp, height = 16.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = rate.currency,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
        Text(
            text = rate.rate,
            fontSize = 13.sp,
        )
    }
}
