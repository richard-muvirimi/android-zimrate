package com.tyganeutronics.myratecalculator.ui

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.view.children
import com.tyganeutronics.myratecalculator.R
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lays children out left to right, wrapping to a new row when one runs out of width, with each
 * row centred and each child centred against the tallest child beside it.
 *
 * A grid was the obvious choice for the glance screen and the wrong one: fixed columns give every
 * bubble the same centre line, so circles of different sizes still read as a table. Here a child
 * takes exactly the width it was given, which means no two bubbles line up unless they happen to.
 *
 * Children are expected to carry exact pixel width and height in their layout params, which is
 * true of the bubbles — their diameter is computed from the whole set before they are added.
 */
class BubbleFlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    private val spacing = resources.getDimensionPixelSize(R.dimen.padding_8)

    init {
        // Both default to true, which crops a bubble the moment it drifts into the padding or
        // past the edge — and the padding is exactly the room the drift is meant to use. Set here
        // rather than in the layout file so the behaviour cannot be lost by reusing the view.
        clipToPadding = false
        clipChildren = false
    }

    /** Built during measure and read back during layout, so the wrapping is only worked out once. */
    private val rows = mutableListOf<List<View>>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = width - paddingLeft - paddingRight

        rows.clear()
        var row = mutableListOf<View>()
        var rowWidth = 0
        var rowHeight = 0
        var totalHeight = 0

        children.forEach { child ->
            if (child.visibility == GONE) return@forEach

            val params = child.layoutParams
            child.measure(
                MeasureSpec.makeMeasureSpec(params.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY),
            )

            var needed = child.measuredWidth + if (row.isEmpty()) 0 else spacing
            if (row.isNotEmpty() && rowWidth + needed > available) {
                rows += row
                totalHeight += rowHeight + spacing
                row = mutableListOf()
                rowWidth = 0
                rowHeight = 0
                needed = child.measuredWidth
            }

            row += child
            rowWidth += needed
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }

        if (row.isNotEmpty()) {
            rows += row
            totalHeight += rowHeight
        }

        setMeasuredDimension(width, totalHeight + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val available = measuredWidth - paddingLeft - paddingRight
        var top = paddingTop

        rows.forEach { row ->
            val rowWidth =
                row.sumOf { it.measuredWidth } + spacing * (row.size - 1)
            val rowHeight = row.maxOf { it.measuredHeight }

            var left = paddingLeft + (available - rowWidth) / 2
            row.forEach { child ->
                // Centred against the tallest in the row, so a small bubble sits in the middle of
                // the gap between two large ones rather than hanging off a shared top edge.
                val childTop = top + (rowHeight - child.measuredHeight) / 2
                child.layout(
                    left,
                    childTop,
                    left + child.measuredWidth,
                    childTop + child.measuredHeight,
                )
                left += child.measuredWidth + spacing
            }

            top += rowHeight + spacing
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    // --- drift ---------------------------------------------------------------------------------

    /**
     * How each bubble wanders. Two sine waves with their own period and phase, so no two bubbles
     * share a rhythm and the group never falls into step — which is what would make it read as an
     * animation rather than as floating.
     */
    private class Drift(
        val view: View,
        val amplitudeX: Float,
        val amplitudeY: Float,
        val periodX: Float,
        val periodY: Float,
        val phaseX: Float,
        val phaseY: Float,
    )

    private val drifts = mutableListOf<Drift>()
    private var driftStartNanos = 0L
    private var driftPausedNanos = 0L

    private val driftAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = DRIFT_FRAME_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { applyDrift() }
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)

        val density = resources.displayMetrics.density
        fun amplitude() = Random.nextFloat()
            .let { AMPLITUDE_MIN_DP + it * (AMPLITUDE_MAX_DP - AMPLITUDE_MIN_DP) } * density
        fun period() = Random.nextFloat()
            .let { PERIOD_MIN_SECONDS + it * (PERIOD_MAX_SECONDS - PERIOD_MIN_SECONDS) }

        drifts += Drift(
            view = child,
            amplitudeX = amplitude(),
            amplitudeY = amplitude(),
            periodX = period(),
            periodY = period(),
            phaseX = Random.nextFloat(),
            phaseY = Random.nextFloat(),
        )
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        drifts.removeAll { it.view === child }
        child.translationX = 0f
        child.translationY = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startDrift()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        driftAnimator.cancel()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startDrift() else driftAnimator.cancel()
    }

    /**
     * Holds the bubbles where they are. A tap target prompt reads its focal circle off the
     * target's position once, when it is shown, so a bubble that keeps wandering leaves the
     * circle behind and the whole thing reads as broken.
     */
    fun pauseDrift() {
        if (!driftAnimator.isStarted) return
        driftPausedNanos = System.nanoTime()
        driftAnimator.cancel()
    }

    /**
     * Picks the wander back up where it stopped. The clock is carried forward by the length of
     * the pause, so no bubble snaps back to the start of its cycle on the way.
     */
    fun resumeDrift() {
        if (driftPausedNanos == 0L) return
        driftStartNanos += System.nanoTime() - driftPausedNanos
        driftPausedNanos = 0L
        if (animatorScale() != 0f) driftAnimator.start()
    }

    /**
     * Left still when the system animation scale is zero. That setting is how someone turns
     * animations off, whether for motion sensitivity or to save battery, and a screen that drifts
     * anyway is ignoring them.
     */
    private fun startDrift() {
        if (driftAnimator.isStarted) return
        if (animatorScale() == 0f) return

        driftStartNanos = System.nanoTime()
        driftAnimator.start()
    }

    private fun applyDrift() {
        val seconds = (System.nanoTime() - driftStartNanos) / NANOS_PER_SECOND

        drifts.forEach { drift ->
            drift.view.translationX =
                sin((seconds / drift.periodX + drift.phaseX) * TWO_PI) * drift.amplitudeX
            drift.view.translationY =
                sin((seconds / drift.periodY + drift.phaseY) * TWO_PI) * drift.amplitudeY
        }
    }

    private fun animatorScale(): Float = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )

    companion object {
        /**
         * Kept under the gap between bubbles, so two neighbours drifting toward each other stay
         * clear of one another instead of colliding.
         */
        private const val AMPLITUDE_MIN_DP = 2.5f
        private const val AMPLITUDE_MAX_DP = 5f

        /** Slow enough to read as floating rather than as jitter. */
        private const val PERIOD_MIN_SECONDS = 3.5f
        private const val PERIOD_MAX_SECONDS = 7f

        private const val DRIFT_FRAME_MS = 1000L
        private const val NANOS_PER_SECOND = 1_000_000_000f
        private const val TWO_PI = (2 * PI).toFloat()
    }
}
