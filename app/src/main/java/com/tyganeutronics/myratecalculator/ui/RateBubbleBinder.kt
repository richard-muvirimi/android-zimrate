package com.tyganeutronics.myratecalculator.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.murgupluoglu.flagkit.FlagKit
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.utils.CurrencyFlagUtil
import com.tyganeutronics.myratecalculator.utils.resolveAttr
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Builds the circles on the glance screen. There is no recycling here — the screen shows the
 * favourites, which is a handful of rows, and each bubble's size depends on the whole set anyway.
 */
object RateBubbleBinder {

    /** Diameter as a share of the sizing basis, from the smallest rate on screen to the largest. */
    private const val MIN_FRACTION = 0.22
    private const val MAX_FRACTION = 0.40
    private const val EPSILON = 1e-6

    /** Past this the circles stop growing, so a tablet does not get absurd ones. */
    private const val MAX_BASIS_DP = 420

    /** Content inset either side, as a share of the diameter. */
    private const val CONTENT_INSET = 0.16

    /**
     * A diameter for each of [rates], in the order given.
     *
     * Rates run from well under 1 to the thousands, so area on a linear scale would collapse
     * everything below ZAR into an invisible dot. A log scale spread across the set keeps every
     * bubble legible and still ranks them.
     *
     * Note this ranks by denomination as much as by strength — a currency quoted in thousands is
     * a big circle because of how it is denominated, not because it moved.
     */
    fun diameters(context: Context, rates: List<RateEntity>): List<Int> {
        val basis = basisPx(context)
        val logs = rates.map { logOf(it.rate) }
        val known = logs.filterNotNull()
        val low = known.minOrNull()
        val span = if (low == null) 0.0 else known.max() - low

        return logs.map { value ->
            // A single rate, or a set that is all one value, has no spread to rank — those sit
            // mid size rather than collapsing to the smallest.
            val fraction = if (low == null || value == null || span < EPSILON) {
                0.5
            } else {
                (value - low) / span
            }
            (basis * (MIN_FRACTION + fraction * (MAX_FRACTION - MIN_FRACTION))).roundToInt()
        }
    }

    /** Inflates one bubble into [parent] and fills it in. */
    fun addBubble(
        parent: BubbleFlowLayout,
        entity: RateEntity,
        diameterPx: Int,
        onClick: (RateEntity) -> Unit,
    ) {
        val card = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rate_bubble, parent, false) as MaterialCardView

        card.layoutParams = card.layoutParams.apply {
            width = diameterPx
            height = diameterPx
        }
        card.setOnClickListener { onClick(entity) }

        // A circle is only as wide as its diameter at the very middle, so the content has to be
        // inset proportionally or a two line name would run out past the edge of a small one.
        val inset = (diameterPx * CONTENT_INSET).roundToInt()
        card.findViewById<View>(R.id.layout_bubble_content)
            .setPadding(inset, inset / 2, inset, inset / 2)

        bindFlag(card.findViewById(R.id.img_bubble_flag), entity)

        val rate = entity.rate.setScale(2, RoundingMode.HALF_UP).toPlainString()
        card.findViewById<TextView>(R.id.txt_bubble_rate).text = rate
        card.findViewById<TextView>(R.id.txt_bubble_name).text = nameOf(card.context, entity)

        val move = moveOf(entity)
        applyMove(card, move)
        card.contentDescription = describe(card.context, entity, rate, move)

        parent.addView(card)
    }

    private fun bindFlag(imgFlag: ImageView, entity: RateEntity) {
        // Same reasoning as the rates list: a code the user invented is not ISO, so a country
        // lookup would dress it in an unrelated flag. It gets the generic currency mark.
        if (entity.custom) {
            imgFlag.setImageResource(imgFlag.context.resolveAttr(R.attr.ic_dollar))
            return
        }

        val countryCode = CurrencyFlagUtil.countryCode(entity.currency)
        val flagRes =
            if (countryCode.isNotEmpty()) FlagKit.getResId(imgFlag.context, countryCode) else 0
        imgFlag.setImageResource(if (flagRes != 0) flagRes else R.mipmap.ic_launcher)
    }

    /**
     * A rate is units per USD, so a rise means that currency weakened against the dollar. Warm
     * for a rise, then, which is the opposite of the stock-ticker reflex.
     */
    private fun moveOf(entity: RateEntity): Move = when {
        // Zero is what a rate carries before it has ever been compared: a custom rate nobody has
        // edited, or a currency whose first sync has not landed. Neither has moved.
        entity.lastRate <= BigDecimal.ZERO -> Move.FLAT
        entity.rate > entity.lastRate -> Move.ROSE
        entity.rate < entity.lastRate -> Move.FELL
        else -> Move.FLAT
    }

    private fun applyMove(card: MaterialCardView, move: Move) {
        val context = card.context
        card.setCardBackgroundColor(ContextCompat.getColor(context, move.backgroundRes))

        val trend = card.findViewById<ImageView>(R.id.img_bubble_trend)
        if (move.arrowRes == null || move.trendRes == null) {
            trend.visibility = View.GONE
            return
        }
        trend.visibility = View.VISIBLE
        trend.setImageResource(move.arrowRes)
        trend.setColorFilter(ContextCompat.getColor(context, move.trendRes))
    }

    /** A circle this small only fits the code, so the full label is what a screen reader hears. */
    private fun describe(
        context: Context,
        entity: RateEntity,
        rate: String,
        move: Move,
    ): String {
        // Carries the code as well, which the bubble itself no longer shows.
        val label = CurrencyFlagUtil.codeWithName(context, entity.currency, nameOf(context, entity))
        val movement = move.captionRes?.let {
            val previous = entity.lastRate.setScale(2, RoundingMode.HALF_UP).toPlainString()
            " ${context.getString(it, previous)}"
        }.orEmpty()

        return "$label $rate.$movement"
    }

    /**
     * The country the money is from, which means more to most people than a three letter code.
     * A rate the user added carries their own label — its code resolves to no country at all.
     */
    private fun nameOf(context: Context, entity: RateEntity): String =
        if (entity.custom) {
            entity.name.ifEmpty { entity.currency }
        } else {
            CurrencyFlagUtil.countryName(entity.currency)
        }

    /** Null for a rate that cannot be placed on a log scale, which sizes it mid range. */
    private fun logOf(rate: BigDecimal): Double? =
        if (rate <= BigDecimal.ZERO) null else ln(rate.toDouble())

    private fun basisPx(context: Context): Int {
        val metrics = context.resources.displayMetrics
        return minOf(metrics.widthPixels, (MAX_BASIS_DP * metrics.density).roundToInt())
    }

    private enum class Move(
        @param:ColorRes val backgroundRes: Int,
        @param:DrawableRes val arrowRes: Int?,
        @param:ColorRes val trendRes: Int?,
        /** What a screen reader says in place of the colour and arrow. */
        @param:StringRes val captionRes: Int?,
    ) {
        ROSE(
            R.color.glance_bubble_rose,
            R.drawable.ic_trend_up,
            R.color.glance_trend_rose,
            R.string.glance_rate_rose,
        ),
        FELL(
            R.color.glance_bubble_fell,
            R.drawable.ic_trend_down,
            R.color.glance_trend_fell,
            R.string.glance_rate_fell,
        ),
        FLAT(R.color.glance_bubble_flat, null, null, null),
    }
}
