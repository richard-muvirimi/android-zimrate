package com.tyganeutronics.myratecalculator.database.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import com.tyganeutronics.myratecalculator.database.rtdb.WalletRepository

class RewardViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * Null until the wallet has actually been read, so a screen can tell "no coins" apart from
     * "not known yet" — the menu and the balance sheet both render a waiting state for null
     * rather than a zero.
     */
    val coins = WalletRepository.coins.asLiveData()
}
