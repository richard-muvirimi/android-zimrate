package com.tyganeutronics.myratecalculator.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders.StringProp
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tyganeutronics.myratecalculator.wear.data.WearRateModel
import com.tyganeutronics.myratecalculator.wear.data.WearRateStore
import com.tyganeutronics.myratecalculator.wear.presentation.MainActivity

class RateTileService : TileService() {

    override fun onTileRequest(request: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val rates = WearRateStore.load(applicationContext)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(buildLayout(applicationContext, rates))
            )
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        request: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion("1").build()
        )
    }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        requestUpdate(applicationContext)
    }

    companion object {
        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(RateTileService::class.java)
        }

        private fun buildLayout(context: Context, rates: List<WearRateModel>): LayoutElement {
            val launchApp = Clickable.Builder()
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

            val column = Column.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .setModifiers(Modifiers.Builder().setClickable(launchApp).build())

            // Title
            column.addContent(
                Text.Builder()
                    .setText(StringProp.Builder("ZimRate").build())
                    .setFontStyle(
                        androidx.wear.protolayout.LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(14f))
                            .setColor(argb(0xFF0F8AFD.toInt()))
                            .setWeight(androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .build()
                    )
                    .build()
            )

            column.addContent(Spacer.Builder().setHeight(dp(4f)).build())

            if (rates.isEmpty()) {
                column.addContent(
                    Text.Builder()
                        .setText(StringProp.Builder("No favourites").build())
                        .build()
                )
            } else {
                rates.forEach { rate ->
                    column.addContent(buildRateRow(rate))
                }
            }

            return Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .addContent(column.build())
                .build()
        }

        private fun buildRateRow(rate: WearRateModel): Row {
            return Row.Builder()
                .setWidth(expand())
                .setModifiers(
                    Modifiers.Builder()
                        .setPadding(
                            Padding.Builder()
                                .setStart(dp(8f)).setEnd(dp(8f))
                                .setTop(dp(2f)).setBottom(dp(2f))
                                .build()
                        )
                        .build()
                )
                .addContent(
                    Text.Builder()
                        .setText(StringProp.Builder(rate.currency).build())
                        .setFontStyle(
                            androidx.wear.protolayout.LayoutElementBuilders.FontStyle.Builder()
                                .setSize(sp(12f))
                                .setWeight(androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                .build()
                        )
                        .build()
                )
                .addContent(Spacer.Builder().setWidth(expand()).build())
                .addContent(
                    Text.Builder()
                        .setText(StringProp.Builder(rate.rate).build())
                        .setFontStyle(
                            androidx.wear.protolayout.LayoutElementBuilders.FontStyle.Builder()
                                .setSize(sp(12f))
                                .build()
                        )
                        .build()
                )
                .build()
        }
    }
}
