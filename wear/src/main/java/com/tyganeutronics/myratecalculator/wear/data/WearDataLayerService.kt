package com.tyganeutronics.myratecalculator.wear.data

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.tyganeutronics.myratecalculator.wear.complication.RateComplicationService
import com.tyganeutronics.myratecalculator.wear.tile.RateTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WearDataLayerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            val path = event.dataItem.uri.path ?: return@forEach
            if (path == "/zimrate/rates") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val json = dataMap.getString("rates") ?: return@forEach
                val rates = WearRateModel.listFromJson(json)
                WearRateStore.save(applicationContext, rates)
                notifySurfaces()
            }
        }
    }

    private fun notifySurfaces() {
        scope.launch {
            // Notify complication to refresh
            RateComplicationService.notifyUpdate(applicationContext)
            // Notify tile to refresh
            RateTileService.requestUpdate(applicationContext)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
