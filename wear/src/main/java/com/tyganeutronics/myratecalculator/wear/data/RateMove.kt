package com.tyganeutronics.myratecalculator.wear.data

/**
 * Which way a rate last moved, and how the watch shows it.
 *
 * A rate is units per USD, so a rise means that currency weakened against the dollar — warm for a
 * rise, then, which is the opposite of the stock-ticker reflex. The mark carries the same meaning
 * as the colour, because colour alone is no use to a red green colour blind wearer, and a watch
 * face is exactly where a small coloured number is hardest to read.
 *
 * Colours match the dark values the phone uses; a tile draws on its own dark surface either way.
 */
enum class RateMove(val color: Int?, val mark: String) {
    ROSE(0xFFEF9A9A.toInt(), "▲"),
    FELL(0xFFA5D6A7.toInt(), "▼"),
    FLAT(null, ""),
}

/**
 * [value] with the movement mark after it, or untouched when nothing moved — so a flat rate never
 * carries a stray separator. [separator] is dropped on a complication, where a short text slot
 * renders only a handful of characters and every one of them counts.
 */
fun RateMove.append(value: String, separator: String = " "): String =
    if (mark.isEmpty()) value else "$value$separator$mark"

/**
 * Both values arrive already rounded to what is displayed, so a change too small to alter the
 * number on screen is deliberately not marked as a move.
 */
fun WearRateModel.move(): RateMove {
    val current = rate.toDoubleOrNull() ?: return RateMove.FLAT
    // Empty from an older phone build, zero before the rate has ever been compared.
    val previous = lastRate.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return RateMove.FLAT

    return when {
        current > previous -> RateMove.ROSE
        current < previous -> RateMove.FELL
        else -> RateMove.FLAT
    }
}
