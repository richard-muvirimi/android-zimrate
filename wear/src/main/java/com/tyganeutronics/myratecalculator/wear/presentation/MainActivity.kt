package com.tyganeutronics.myratecalculator.wear.presentation

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.tyganeutronics.myratecalculator.wear.data.label

class MainActivity : ComponentActivity() {

    private val rates = mutableStateOf<List<WearRateModel>>(emptyList())

    /** Currency named by a complication tap, scrolled to and highlighted on arrival. */
    private val focused = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        focused.value = intent?.getStringExtra(EXTRA_CURRENCY)
        setContent {
            ZimRateWearTheme {
                RatesScreen(rates.value, focused.value)
            }
        }
    }

    /** Launch mode is singleTop, so a second tap arrives here rather than in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        focused.value = intent.getStringExtra(EXTRA_CURRENCY)
    }

    override fun onResume() {
        super.onResume()
        // Re-read the store rather than rebuilding the composition, so rates that arrive
        // while the screen is open show up.
        rates.value = WearRateStore.load(this)
    }

    companion object {
        const val EXTRA_CURRENCY = "currency"
    }
}

@Composable
fun RatesScreen(rates: List<WearRateModel>, focused: String? = null) {
    val listState = rememberScalingLazyListState()

    LaunchedEffect(focused, rates) {
        if (focused == null) return@LaunchedEffect
        val index = rates.indexOfFirst { it.currency == focused }
        // +1 for the header item occupying index 0.
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }

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
                RateRow(rate, highlighted = rate.currency == focused)
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
fun RateRow(rate: WearRateModel, highlighted: Boolean = false) {
    val context = LocalContext.current
    // A code the user invented is not ISO, so looking up a flag for it would show an unrelated
    // country's. Those rows carry the code and name alone.
    var flagBitmap by remember(rate.currency) {
        mutableStateOf(
            runCatching {
                if (rate.custom) return@runCatching null
                val code = countryCode(rate.currency)
                val resId = if (code.isNotEmpty()) FlagKit.getResId(context, code) else 0
                if (resId != 0) context.getDrawable(resId)?.toBitmap() else null
            }.getOrNull()
        )
    }

    val color = if (highlighted) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.onBackground
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flagBitmap != null) {
            Image(
                bitmap = flagBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(width = 24.dp, height = 16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        // Weighted so a long country name yields space to the rate rather than pushing it off.
        Text(
            text = rate.label(context),
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = rate.rate,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = color,
        )
    }
}
