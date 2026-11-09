package com.tyganeutronics.myratecalculator.wear.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.scrollAway
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.tyganeutronics.myratecalculator.wear.BuildConfig
import com.tyganeutronics.myratecalculator.wear.R
import com.tyganeutronics.myratecalculator.wear.presentation.theme.ZimRateWearTheme

/** Version, author, links and open source notices: the watch's counterpart to the phone's About card. */
class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZimRateWearTheme {
                AboutScreen()
            }
        }
    }
}

private class Notice(val name: String, val copyright: String, val license: String)

// What this module ships, not the phone's list: the watch bundles none of the phone's
// libraries. Names and licences are proper nouns, so they are not translated.
private val notices = listOf(
    Notice(
        "Android Open Source Project",
        "Copyright (C) The Android Open Source Project",
        "Apache License 2.0",
    ),
    Notice(
        "Kotlin & kotlinx.coroutines",
        "Copyright JetBrains s.r.o. and Kotlin contributors",
        "Apache License 2.0",
    ),
    Notice("Guava", "Copyright (C) The Guava Authors", "Apache License 2.0"),
    Notice("FlagKit for Android", "Copyright (c) 2022 Mustafa Urgupluoglu", "MIT License"),
    Notice("FlagKit", "Copyright (c) 2016 Bowtie AB", "MIT License"),
)

private fun shortUrl(url: String) = url.removePrefix("https://").removeSuffix("/")

/** A watch has no browser to follow a link with, so the link is opened on the paired phone. */
private fun openOnPhone(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    Futures.addCallback(
        RemoteActivityHelper(context).startRemoteActivity(intent),
        // Nullable: the future completes with null, and Kotlin would reject that as a Void.
        object : FutureCallback<Void?> {
            override fun onSuccess(result: Void?) {
                Toast.makeText(context, R.string.opened_on_phone, Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(t: Throwable) {
                Toast.makeText(context, R.string.phone_unreachable, Toast.LENGTH_LONG).show()
            }
        },
        ContextCompat.getMainExecutor(context),
    )
}

@Composable
fun AboutScreen() {
    val listState = rememberScalingLazyListState()

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                ListHeader {
                    Text(
                        text = stringResource(R.string.about),
                        style = MaterialTheme.typography.title3,
                    )
                }
            }

            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_round),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                    )
                }
            }

            item {
                LinkChip(
                    title = stringResource(R.string.author),
                    detail = stringResource(R.string.dev_name),
                    url = stringResource(R.string.dev_url),
                )
            }

            item {
                val url = stringResource(R.string.rates_source)
                LinkChip(stringResource(R.string.rates_source_title), shortUrl(url), url)
            }

            item {
                val url = stringResource(R.string.privacy_policy_url)
                LinkChip(stringResource(R.string.privacy_policy_title), shortUrl(url), url)
            }

            item {
                ListHeader {
                    Text(
                        text = stringResource(R.string.licenses),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            items(notices) { notice ->
                InfoItem(title = notice.name, lines = listOf(notice.copyright, notice.license))
            }
        }
    }
}

@Composable
private fun LinkChip(title: String, detail: String, url: String) {
    val context = LocalContext.current
    Chip(
        onClick = { openOnPhone(context, url) },
        label = { Text(title) },
        secondaryLabel = { Text(detail) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun InfoItem(title: String, lines: List<String>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        lines.forEach { line ->
            Text(
                text = line,
                fontSize = 11.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
