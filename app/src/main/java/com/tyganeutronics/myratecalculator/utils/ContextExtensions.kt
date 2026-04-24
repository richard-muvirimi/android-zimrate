package com.tyganeutronics.myratecalculator.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes

fun Context.resolveAttr(@AttrRes attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return tv.resourceId
}
