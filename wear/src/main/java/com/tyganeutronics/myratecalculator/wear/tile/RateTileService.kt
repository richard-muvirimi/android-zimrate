package com.tyganeutronics.myratecalculator.wear.tile

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.CONTENT_SCALE_MODE_FIT
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_START
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.murgupluoglu.flagkit.FlagKit
import com.tyganeutronics.myratecalculator.wear.R
import com.tyganeutronics.myratecalculator.wear.data.WearRateModel
import com.tyganeutronics.myratecalculator.wear.data.WearRateStore
import com.tyganeutronics.myratecalculator.wear.presentation.MainActivity
import com.tyganeutronics.myratecalculator.wear.util.countryCode
import com.tyganeutronics.myratecalculator.wear.util.countryName
import java.nio.ByteBuffer

class RateTileService : TileService() {

    override fun onTileRequest(
        request: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val rates = WearRateStore.load(applicationContext)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(resourcesVersion(rates))
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(
                    buildLayout(applicationContext, rates, request.deviceConfiguration)
                )
            )
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        request: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val builder = ResourceBuilders.Resources.Builder().setVersion(request.version)

        // FlagKit resolves to vector drawables on API 24+, which protolayout cannot reference
        // by resource id, so each flag is rasterised and inlined instead.
        WearRateStore.load(applicationContext)
            .take(MAX_VISIBLE)
            .forEach { rate ->
                flagImage(applicationContext, rate.currency)?.let {
                    builder.addIdToImageMapping(rate.currency, it)
                }
            }

        return Futures.immediateFuture(builder.build())
    }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        requestUpdate(applicationContext)
    }

    companion object {

        /**
         * Upper bound on rows: hero plus the supporting rows beneath it. Flags are supplied
         * for this many regardless of screen, so the resource set stays independent of layout
         * decisions — see [maxVisible] for what is actually drawn.
         */
        private const val MAX_VISIBLE = 4

        /** Below this height a fourth row falls off the bottom of the tile. */
        private const val LARGE_SCREEN_DP = 220
        private const val FLAG_WIDTH_PX = 48
        private const val FLAG_HEIGHT_PX = 32

        private val COLORS = Colors(
            0xFF0F8AFD.toInt(), // primary — the hero rate
            0xFF000000.toInt(), // onPrimary
            0xFF202124.toInt(), // surface
            0xFFFFFFFF.toInt(), // onSurface
        )
        private const val MUTED = 0xFF9AA0A6.toInt()

        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(RateTileService::class.java)
        }

        /**
         * Resources are cached against this string, so it has to change whenever the set of
         * flags does — otherwise a new currency renders with a stale or missing image.
         */
        private fun resourcesVersion(rates: List<WearRateModel>): String =
            rates.take(MAX_VISIBLE).joinToString(",") { it.currency }.ifEmpty { "empty" }

        /**
         * Tiles do not scroll, so anything that does not fit is simply cut off. Small round
         * watches lose the fourth row.
         */
        private fun maxVisible(device: DeviceParameters): Int =
            if (device.screenHeightDp >= LARGE_SCREEN_DP) MAX_VISIBLE else MAX_VISIBLE - 1

        /** The hero number is the largest single claim on vertical space. */
        private fun heroTypography(device: DeviceParameters): Int =
            if (device.screenHeightDp >= LARGE_SCREEN_DP) {
                Typography.TYPOGRAPHY_DISPLAY2
            } else {
                Typography.TYPOGRAPHY_DISPLAY3
            }

        private fun buildLayout(
            context: Context,
            rates: List<WearRateModel>,
            device: DeviceParameters,
        ): LayoutElement {
            val layout = PrimaryLayout.Builder(device)
                // Applies the official Wear margins, including the round-screen insets.
                .setResponsiveContentInsetEnabled(true)

            if (rates.isEmpty()) {
                layout.setContent(
                    Text.Builder(context, context.getString(R.string.no_rates))
                        .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                        .setColor(argb(MUTED))
                        .setMaxLines(3)
                        .build()
                )
                return clickableWrapper(context, layout.build())
            }

            val visible = maxVisible(device)
            val hero = rates.first()
            val supporting = rates.drop(1).take(visible - 1)

            layout.setPrimaryLabelTextContent(heroLabel(context, hero))

            val content = Column.Builder()
                .setWidth(expand())
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    Text.Builder(context, hero.rate)
                        .setTypography(heroTypography(device))
                        .setColor(argb(COLORS.primary))
                        .build()
                )

            if (supporting.isNotEmpty()) {
                content.addContent(Spacer.Builder().setHeight(dp(6f)).build())
                supporting.forEach { content.addContent(supportingRow(context, it)) }
            }

            layout.setContent(content.build())

            // The overflow count rides in the label slot rather than costing a content row —
            // an extra row is exactly what pushes the last rate under the bottom of the tile.
            val hidden = rates.size - visible
            val footer = if (hidden > 0) {
                Text.Builder(context, context.getString(R.string.more_rates, hidden))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION3)
                    .setColor(argb(MUTED))
                    .build()
            } else {
                updatedLabel(context, rates)
            }
            footer?.let { layout.setSecondaryLabelTextContent(it) }

            return clickableWrapper(context, layout.build())
        }

        /** Flag and code for the hero rate, sitting above the large number. */
        private fun heroLabel(context: Context, rate: WearRateModel): LayoutElement =
            Row.Builder()
                .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                .addContent(flagElement(rate.currency))
                .addContent(Spacer.Builder().setWidth(dp(5f)).build())
                .addContent(
                    Text.Builder(context, countryName(rate.currency))
                        .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                        .setColor(argb(COLORS.onSurface))
                        .setMaxLines(1)
                        .setOverflow(TEXT_OVERFLOW_ELLIPSIZE)
                        .build()
                )
                .build()

        private fun supportingRow(context: Context, rate: WearRateModel): LayoutElement =
            Row.Builder()
                .setWidth(expand())
                .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    Modifiers.Builder()
                        .setPadding(
                            Padding.Builder().setTop(dp(2f)).setBottom(dp(2f)).build()
                        )
                        .build()
                )
                .addContent(flagElement(rate.currency))
                .addContent(Spacer.Builder().setWidth(dp(5f)).build())
                // Expanding box so a long country name ellipsizes rather than squeezing the
                // rate off the row.
                .addContent(
                    Box.Builder()
                        .setWidth(expand())
                        .setHorizontalAlignment(HORIZONTAL_ALIGN_START)
                        .addContent(
                            Text.Builder(context, countryName(rate.currency))
                                .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                                .setColor(argb(MUTED))
                                .setMaxLines(1)
                                .setOverflow(TEXT_OVERFLOW_ELLIPSIZE)
                                .build()
                        )
                        .build()
                )
                .addContent(Spacer.Builder().setWidth(dp(4f)).build())
                .addContent(
                    Text.Builder(context, rate.rate)
                        .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                        .setColor(argb(COLORS.onSurface))
                        .build()
                )
                .build()

        private fun flagElement(currency: String): LayoutElement =
            Image.Builder()
                .setResourceId(currency)
                .setWidth(dp(18f))
                .setHeight(dp(12f))
                .setContentScaleMode(CONTENT_SCALE_MODE_FIT)
                .build()

        private fun updatedLabel(
            context: Context,
            rates: List<WearRateModel>,
        ): LayoutElement? {
            val lastChecked = rates.maxOfOrNull { it.lastChecked } ?: 0L
            if (lastChecked <= 0L) return null
            return Text.Builder(
                context,
                context.getString(
                    R.string.updated_at,
                    DateUtils.getRelativeTimeSpanString(lastChecked),
                ),
            )
                .setTypography(Typography.TYPOGRAPHY_CAPTION3)
                .setColor(argb(MUTED))
                .build()
        }

        /** The whole tile opens the app; PrimaryLayout does not take modifiers itself. */
        private fun clickableWrapper(context: Context, content: LayoutElement): LayoutElement =
            Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setModifiers(
                    Modifiers.Builder()
                        .setClickable(
                            Clickable.Builder()
                                .setOnClick(
                                    ActionBuilders.LaunchAction.Builder()
                                        .setAndroidActivity(
                                            ActionBuilders.AndroidActivity.Builder()
                                                .setPackageName(context.packageName)
                                                .setClassName(MainActivity::class.java.name)
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .addContent(content)
                .build()

        private fun flagImage(
            context: Context,
            currency: String,
        ): ResourceBuilders.ImageResource? {
            val code = countryCode(currency).takeIf { it.isNotEmpty() } ?: return null
            val resId = runCatching { FlagKit.getResId(context, code) }.getOrNull() ?: return null
            if (resId == 0) return null
            val drawable = runCatching { context.getDrawable(resId) }.getOrNull() ?: return null

            val bitmap = drawable.toBitmap(FLAG_WIDTH_PX, FLAG_HEIGHT_PX, Bitmap.Config.RGB_565)
            val buffer = ByteBuffer.allocate(bitmap.byteCount)
            bitmap.copyPixelsToBuffer(buffer)

            return ResourceBuilders.ImageResource.Builder()
                .setInlineResource(
                    ResourceBuilders.InlineImageResource.Builder()
                        .setData(buffer.array())
                        .setWidthPx(FLAG_WIDTH_PX)
                        .setHeightPx(FLAG_HEIGHT_PX)
                        .setFormat(ResourceBuilders.IMAGE_FORMAT_RGB_565)
                        .build()
                )
                .build()
        }
    }
}
