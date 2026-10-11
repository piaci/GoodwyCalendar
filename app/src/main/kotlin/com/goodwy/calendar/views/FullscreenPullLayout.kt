package com.goodwy.calendar.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Symmetric vertical pull detector. Unlike SwipeRefreshLayout, intercepts both
 * downward and upward drags. Pure touch hook — no drawing, no state, no animation.
 *
 * The host decides what a gesture means via [canIntercept], called once per
 * gesture after touch slop is crossed. If it returns false, all touch events
 * flow to children unchanged.
 */
class FullscreenPullLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    /** Signed dy from ACTION_DOWN; positive = downward. Return true to intercept. */
    var canIntercept: ((dy: Float) -> Boolean)? = null

    /** Called on each MOVE while intercepting. dy positive = downward. */
    var onPull: ((dy: Float) -> Unit)? = null

    /** Called on UP/CANCEL while intercepting. velocityY positive = downward. */
    var onRelease: ((velocityY: Float) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f
    private var intercepting = false
    private var velocityTracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                intercepting = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(ev) }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                if (intercepting) return true

                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (dy < touchSlop) return false
                if (dy < dx) return false  // horizontal, not our gesture

                if (canIntercept?.invoke(ev.y - downY) != true) return false

                intercepting = true
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endGesture()
                return false
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!intercepting) return super.onTouchEvent(ev)

        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> onPull?.invoke(ev.y - downY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val velY = velocityTracker?.yVelocity ?: 0f
                endGesture()
                onRelease?.invoke(velY)
            }
        }
        return true
    }

    private fun endGesture() {
        velocityTracker?.recycle()
        velocityTracker = null
        intercepting = false
    }
}

