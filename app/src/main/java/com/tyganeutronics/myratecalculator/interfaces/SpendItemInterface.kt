package com.tyganeutronics.myratecalculator.interfaces

import com.tyganeutronics.myratecalculator.database.rtdb.Spend

interface SpendItemInterface {

    val items: List<Spend>
}
