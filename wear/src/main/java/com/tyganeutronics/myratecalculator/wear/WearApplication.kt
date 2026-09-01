package com.tyganeutronics.myratecalculator.wear

import android.app.Application
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.tyganeutronics.myratecalculator.wear.complication.RateComplicationService
import com.tyganeutronics.myratecalculator.wear.data.WearRateModel
import com.tyganeutronics.myratecalculator.wear.data.WearRateStore
import com.tyganeutronics.myratecalculator.wear.tile.RateTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        seedFromDataLayer()
    }

    /**
     * WearDataLayerService only fires on *changes*, so an app installed after the phone last
     * pushed would never see those rates. Data items persist on the node, so read whatever is
     * already there at startup and seed the store from it.
     */
    private fun seedFromDataLayer() {
        scope.launch {
            runCatching {
                val items = Wearable.getDataClient(this@WearApplication).getDataItems().await()

                val json = try {
                    items.firstOrNull { it.uri.path == RATES_PATH }
                        ?.let { DataMapItem.fromDataItem(it).dataMap.getString("rates") }
                } finally {
                    items.release()
                }

                if (json != null) {
                    WearRateStore.save(applicationContext, WearRateModel.listFromJson(json))
                    // Tile and complication may already have rendered an empty state.
                    RateComplicationService.notifyUpdate(applicationContext)
                    RateTileService.requestUpdate(applicationContext)
                }
            }
        }
    }

    companion object {
        private const val RATES_PATH = "/zimrate/rates"
    }
}
