package com.goodwy.calendar.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.goodwy.calendar.R
import com.goodwy.calendar.databinding.MonthViewBackgroundBinding
import com.goodwy.calendar.databinding.MonthViewBinding
import com.goodwy.calendar.extensions.config
import com.goodwy.calendar.extensions.getWeekNumberWidth
import com.goodwy.calendar.extensions.launchNewEventIntent
import com.goodwy.calendar.extensions.launchNewTaskIntent
import com.goodwy.calendar.helpers.*
import com.goodwy.calendar.models.DayMonthly
import com.goodwy.commons.compose.extensions.getActivity
import com.goodwy.commons.dialogs.RadioGroupIconDialog
import com.goodwy.commons.extensions.onGlobalLayout
import com.goodwy.commons.models.RadioItem
import kotlin.math.roundToInt

class MonthViewWrapper(context: Context, attrs: AttributeSet, defStyle: Int) : FrameLayout(context, attrs, defStyle) {
    private var dayWidth = 0f
    private var dayHeight = 0f
    private var expandedHeight = 0
    private var oneWeekHeight = 0
    private var weekDaysLetterHeight = 0
    private var horizontalOffset = 0
    private var wereViewsAdded = false
    private var isMonthDayView = true
    private var days = ArrayList<DayMonthly>()
    private var inflater: LayoutInflater
    private var binding: MonthViewBinding
    private var dayClickCallback: ((day: DayMonthly) -> Unit)? = null

    private var progress = 0f
    private var visibleWeek = 0
    private var settleAnimator: ValueAnimator? = null
    private var snapping = false
    var collapseEnabled = false
    private val expandedHeightFraction = 0.30f

    var onVisibleHeightChanged: ((Int) -> Unit)? = null

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    init {
        val normalTextSize = resources.getDimensionPixelSize(com.goodwy.commons.R.dimen.normal_text_size).toFloat()
        weekDaysLetterHeight = 2 * normalTextSize.toInt()

        inflater = LayoutInflater.from(context)
        binding = MonthViewBinding.inflate(inflater, this, true)
        horizontalOffset = context.getWeekNumberWidth()

        onGlobalLayout {
            if (!wereViewsAdded && days.isNotEmpty()) {
                if (isInLayout) post { initializeViews() } else initializeViews()
            }
        }
    }

    private fun initializeViews() {
        addClickableBackgrounds()
        binding.monthView.updateDays(days, isMonthDayView)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)

        horizontalOffset = context.getWeekNumberWidth()
        dayWidth = (width - horizontalOffset) / COLUMN_COUNT.toFloat()

        if (!collapseEnabled) {
            dayHeight = (availableHeight - weekDaysLetterHeight) / ROW_COUNT.toFloat()
            expandedHeight = availableHeight
            oneWeekHeight = (weekDaysLetterHeight + dayHeight).toInt()
        } else {
            val targetExpandedHeight = (availableHeight * expandedHeightFraction).toInt()
            dayHeight = ((targetExpandedHeight - weekDaysLetterHeight) / ROW_COUNT.toFloat())
                .coerceAtLeast(1f)
            val minDayHeight = resources.getDimensionPixelSize(com.goodwy.commons.R.dimen.normal_text_size) * 2.5f
            dayHeight = dayHeight.coerceAtLeast(minDayHeight)
            expandedHeight = (weekDaysLetterHeight + ROW_COUNT * dayHeight).toInt()
            oneWeekHeight = (weekDaysLetterHeight + dayHeight).toInt()
        }

        binding.monthView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(expandedHeight, MeasureSpec.EXACTLY)
        )

        var dayIndex = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child === binding.monthView) continue
            child.measure(
                MeasureSpec.makeMeasureSpec(dayWidth.toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dayHeight.toInt(), MeasureSpec.EXACTLY)
            )
            dayIndex++
        }

        setMeasuredDimension(width, expandedHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        if (dayHeight <= 0f) return

        binding.monthView.layout(0, 0, width, expandedHeight)
        binding.monthView.setCollapseState(progress, visibleWeek)

        val topRow = (progress * visibleWeek).roundToInt()
        val translation = topRow * dayHeight

        var dayIndex = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child === binding.monthView) continue
            val row = dayIndex / COLUMN_COUNT
            val col = dayIndex % COLUMN_COUNT
            child.visibility = VISIBLE
            val childLeft = (col * dayWidth + horizontalOffset).toInt()
            val childTop = (row * dayHeight + weekDaysLetterHeight).toInt()
            child.layout(
                childLeft,
                childTop,
                (childLeft + dayWidth).toInt(),
                (childTop + dayHeight).toInt()
            )
            child.translationY = -translation
            dayIndex++
        }

        onVisibleHeightChanged?.invoke(getVisibleMonthHeight())
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!collapseEnabled) {
            super.dispatchDraw(canvas)
            return
        }
        val trimPx = progress * (8f * resources.displayMetrics.density)
        val clipHeight = (getVisibleMonthHeight() - trimPx).coerceAtLeast(1f)
        val save = canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), clipHeight)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    fun updateDays(newDays: ArrayList<DayMonthly>, addEvents: Boolean, callback: ((DayMonthly) -> Unit)? = null) {
        dayClickCallback = callback
        days = newDays
        if (dayWidth != 0f && dayHeight != 0f) {
            addClickableBackgrounds()
        }
        isMonthDayView = !addEvents
        binding.monthView.updateDays(days, isMonthDayView)
    }

    private fun addClickableBackgrounds() {
        var i = childCount - 1
        while (i >= 0) {
            if (getChildAt(i) !== binding.monthView) removeViewAt(i)
            i--
        }
        wereViewsAdded = true
        days.forEachIndexed { index, day ->
            addViewBackground(index % COLUMN_COUNT, index / COLUMN_COUNT, day)
        }
    }

    private fun addViewBackground(viewX: Int, viewY: Int, day: DayMonthly) {
        MonthViewBackgroundBinding.inflate(inflater, this, false).root.apply {
            if (isMonthDayView) {
                background = null
            }
            contentDescription = "${day.value} ${
                Formatter.getMonthName(
                    context,
                    Formatter.getDateTimeFromCode(day.code).monthOfYear
                )
            }"

            setOnClickListener {
                dayClickCallback?.invoke(day)
                if (isMonthDayView) {
                    binding.monthView.updateCurrentlySelectedDay(viewX, viewY)
                }
            }

            setOnLongClickListener {
                if (context.config.allowCreatingTasks) {
                    val items = arrayListOf(
                        RadioItem(TYPE_EVENT, context.getString(R.string.event), icon = R.drawable.ic_today_vector),
                        RadioItem(TYPE_TASK, context.getString(R.string.task), icon = R.drawable.ic_task_vector)
                    )
                    RadioGroupIconDialog(context.getActivity(), items) {
                        if (it == TYPE_EVENT) context.launchNewEventIntent(day.code)
                        else context.launchNewTaskIntent(day.code)
                    }
                } else {
                    context.launchNewEventIntent(day.code)
                }
                true
            }

            addView(this)
        }
    }

    fun togglePrintMode() {
        binding.monthView.togglePrintMode()
    }

    fun getCollapseProgress(): Float = progress

    fun getCollapseDistance(): Int = (expandedHeight - oneWeekHeight).coerceAtLeast(1)

    fun getVisibleMonthHeight(): Int {
        if (!collapseEnabled) return expandedHeight
        return (oneWeekHeight + (1 - progress) * (expandedHeight - oneWeekHeight)).toInt()
    }

    fun setCollapseProgress(newProgress: Float) {
        if (snapping) return
        val clamped = newProgress.coerceIn(0f, 1f)
        if (progress == clamped) return
        progress = clamped
        notifyHeightChanged()
        binding.monthView.setCollapseState(progress, visibleWeek)
        updateChildTranslations()
        (parent as? View)?.invalidate()
        invalidate()
    }

    fun setVisibleWeek(week: Int) {
        if (week !in 0 until ROW_COUNT) return
        if (visibleWeek == week) return
        visibleWeek = week
        binding.monthView.setCollapseState(progress, visibleWeek)
        updateChildTranslations()
        (parent as? View)?.invalidate()
        invalidate()
    }

    fun snapTo(targetProgress: Float) {
        val target = targetProgress.coerceIn(0f, 1f)
        settleAnimator?.cancel()
        if (progress == target) return
        snapping = true
        settleAnimator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                notifyHeightChanged()
                binding.monthView.setCollapseState(progress, visibleWeek)
                updateChildTranslations()
                (parent as? View)?.invalidate()
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    snapping = false
                }
            })
            start()
        }
    }

    fun cancelSnap() {
        settleAnimator?.cancel()
        settleAnimator = null
        snapping = false
    }

    private fun notifyHeightChanged() {
        onVisibleHeightChanged?.invoke(getVisibleMonthHeight())
    }

    private fun updateChildTranslations() {
        val topRow = (progress * visibleWeek).roundToInt()
        val translation = topRow * dayHeight
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child === binding.monthView) continue
            child.translationY = -translation
        }
    }

    fun getWeekIndexForDayCode(dayCode: String): Int = binding.monthView.getWeekIndexForDayCode(dayCode)

    fun setTrackedDayCode(code: String?) {
        binding.monthView.setTrackedDayCode(code)
    }

}
