package com.nboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

class SwipeTrailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class TrailPoint(
        val x: Float,
        val y: Float,
        val timestampMs: Long
    )

    private val points = ArrayList<TrailPoint>(MAX_POINTS)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val density = resources.displayMetrics.density

    init {
        visibility = INVISIBLE
        alpha = 1f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            else -> 0
        }
        val measuredHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            else -> 0
        }
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    fun updateTrail(newPoints: List<TrailPoint>) {
        if (newPoints.size < 2) {
            clearNow()
            return
        }
        points.clear()
        if (newPoints.size > MAX_POINTS) {
            points.addAll(newPoints.takeLast(MAX_POINTS))
        } else {
            points.addAll(newPoints)
        }
        animate().cancel()
        alpha = 1f
        visibility = VISIBLE
        invalidate()
    }

    fun fadeOutTrail() {
        if (points.isEmpty() && visibility != VISIBLE) {
            return
        }
        animate().cancel()
        animate()
            .alpha(0f)
            .setDuration(FADE_OUT_DURATION_MS)
            .withEndAction { clearNow() }
            .start()
    }

    private fun clearNow() {
        points.clear()
        alpha = 1f
        visibility = INVISIBLE
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        trimExpiredPoints(now)
        if (points.size < 2) {
            clearNow()
            return
        }

        val newestAge = (now - points.last().timestampMs).coerceAtLeast(0L)
        val life = (1f - newestAge.toFloat() / TRAIL_WINDOW_MS).coerceIn(0f, 1f)
        if (life <= 0f) {
            clearNow()
            return
        }

        rebuildSmoothPath(points)
        glowPaint.color = Color.argb((life * 90f).toInt().coerceIn(18, 90), 247, 190, 0)
        glowPaint.strokeWidth = dp(9f)
        corePaint.color = Color.argb((life * 205f).toInt().coerceIn(28, 205), 247, 190, 0)
        corePaint.strokeWidth = dp(5f)
        canvas.drawPath(path, glowPaint)
        canvas.drawPath(path, corePaint)

        if (points.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }

    private fun rebuildSmoothPath(activePoints: List<TrailPoint>) {
        path.reset()
        if (activePoints.isEmpty()) {
            return
        }
        val first = activePoints.first()
        path.moveTo(first.x, first.y)
        if (activePoints.size == 2) {
            val second = activePoints[1]
            path.lineTo(second.x, second.y)
            return
        }
        for (index in 1 until activePoints.size) {
            val previous = activePoints[index - 1]
            val current = activePoints[index]
            val midX = (previous.x + current.x) * 0.5f
            val midY = (previous.y + current.y) * 0.5f
            path.quadTo(previous.x, previous.y, midX, midY)
        }
        val last = activePoints.last()
        path.lineTo(last.x, last.y)
    }

    private fun trimExpiredPoints(nowMs: Long) {
        while (points.size > 2) {
            val second = points[1]
            if (nowMs - second.timestampMs <= TRAIL_WINDOW_MS) {
                break
            }
            points.removeAt(0)
        }
    }

    private fun dp(value: Float): Float = value * density

    companion object {
        private const val MAX_POINTS = 140
        private const val TRAIL_WINDOW_MS = 280L
        private const val FADE_OUT_DURATION_MS = 170L
    }
}
