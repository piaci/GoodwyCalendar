package com.goodwy.calendar.fragments

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.goodwy.calendar.activities.MainActivity
import com.goodwy.calendar.activities.SimpleActivity
import com.goodwy.calendar.adapters.EventListAdapter
import com.goodwy.calendar.databinding.FragmentMonthDayBinding
import com.goodwy.calendar.extensions.*
import com.goodwy.calendar.helpers.Config
import com.goodwy.calendar.helpers.DAY_CODE
import com.goodwy.calendar.helpers.Formatter
import com.goodwy.calendar.helpers.Formatter.YEAR_PATTERN
import com.goodwy.calendar.helpers.MonthlyCalendarImpl
import com.goodwy.calendar.interfaces.MonthlyCalendar
import com.goodwy.calendar.interfaces.NavigationListener
import com.goodwy.calendar.models.DayMonthly
import com.goodwy.calendar.models.Event
import com.goodwy.calendar.models.ListEvent
import com.goodwy.commons.extensions.areSystemAnimationsEnabled
import com.goodwy.commons.extensions.beVisibleIf
import com.goodwy.commons.extensions.getProperTextColor
import com.goodwy.commons.interfaces.RefreshRecyclerViewListener
import org.joda.time.DateTime

class MonthDayFragment : Fragment(), MonthlyCalendar, RefreshRecyclerViewListener {
    private var mShowWeekNumbers = false
    private var mDayCode = ""
    private var mSelectedDayCode = ""
    private var mPackageName = ""
    private var mLastHash = 0L
    private var mCalendar: MonthlyCalendarImpl? = null
    private var mListEvents = ArrayList<Event>()

    var listener: NavigationListener? = null

    private lateinit var mRes: Resources
    private lateinit var binding: FragmentMonthDayBinding
    private lateinit var mConfig: Config

    private var flingVelocityEstimate = 0f
    private val flingThresholdPxPerFrame = 4f  // tune this

    private var accumulatedScroll = 0f
    private val collapseDeadZonePx = 1f  // tune this

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMonthDayBinding.inflate(inflater, container, false)
        mRes = resources
        mPackageName = requireActivity().packageName
        mDayCode = requireArguments().getString(DAY_CODE)!!

        val shownMonthDateTime = Formatter.getDateTimeFromCode(mDayCode)
        binding.monthDaySelectedDayLabel.apply {
            text = getMonthLabel(shownMonthDateTime)
            setOnClickListener {
                (activity as MainActivity).showGoToDateDialog()
            }
        }

        mConfig = requireContext().config
        storeStateVariables()
        setupButtons()
        mCalendar = MonthlyCalendarImpl(this, requireContext())

        binding.monthDayViewWrapper.collapseEnabled = true

        binding.monthDayViewWrapper.onVisibleHeightChanged = { visibleHeight ->
            binding.monthDayListHolder.translationY = visibleHeight.toFloat()
        }

        binding.monthDayEventsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var state = RecyclerView.SCROLL_STATE_IDLE
            private var lastDy = 0

            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                val previous = state
                state = newState
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        binding.monthDayViewWrapper.cancelSnap()
                        flingVelocityEstimate = 0f
                        accumulatedScroll = 0f
                    }
                    RecyclerView.SCROLL_STATE_SETTLING -> {
                        val target = if (kotlin.math.abs(flingVelocityEstimate) < flingThresholdPxPerFrame) {
                            // weak fling — decide by current progress, same as a drag-end
                            val current = binding.monthDayViewWrapper.getCollapseProgress()
                            if (current > 0.5f) 1f else 0f
                        } else {
                            // real fling — go with the direction
                            if (flingVelocityEstimate > 0) 1f else 0f
                        }
                        binding.monthDayViewWrapper.snapTo(target)
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (previous == RecyclerView.SCROLL_STATE_DRAGGING) {
                            val current = binding.monthDayViewWrapper.getCollapseProgress()
                            val target = if (current > 0.5f) 1f else 0f
                            binding.monthDayViewWrapper.snapTo(target)
                        }
                    }
                }
            }

            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                flingVelocityEstimate = flingVelocityEstimate * 0.7f + dy * 0.3f
                lastDy = dy
                handleListScroll(rv, dy)
            }
        })

        return binding.root
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onResume() {
        super.onResume()
        if (mConfig.showWeekNumbers != mShowWeekNumbers) {
            mLastHash = -1L
        }

        mCalendar!!.apply {
            mTargetDate = Formatter.getDateTimeFromCode(mDayCode)
            getDays(false)    // prefill the screen asap, even if without events
        }

        storeStateVariables()
        updateCalendar()
    }

    private fun storeStateVariables() {
        mConfig.apply {
            mShowWeekNumbers = showWeekNumbers
        }
    }

    fun updateCalendar() {
        mCalendar?.updateMonthlyCalendar(Formatter.getDateTimeFromCode(mDayCode))
    }

    override fun updateMonthlyCalendar(context: Context, month: String, days: ArrayList<DayMonthly>, checkedEvents: Boolean, currTargetDate: DateTime) {
        val newHash = month.hashCode() + days.hashCode().toLong()
        if ((mLastHash != 0L && !checkedEvents) || mLastHash == newHash) {
            return
        }

        mLastHash = newHash

        activity?.runOnUiThread {
            binding.monthDayViewWrapper.updateDays(days, false) {
                mSelectedDayCode = it.code
                updateVisibleEvents()
            }
        }

        refreshItems()
    }

    private fun updateVisibleEvents() {
        if (activity == null) {
            return
        }

        val filtered = mListEvents.filter {
            if (mSelectedDayCode.isEmpty()) {
                val shownMonthDateTime = Formatter.getDateTimeFromCode(mDayCode)
                val startDateTime = Formatter.getDateTimeFromTS(it.startTS)
                shownMonthDateTime.year == startDateTime.year && shownMonthDateTime.monthOfYear == startDateTime.monthOfYear
            } else {
                val selectionDate = Formatter.getDateTimeFromCode(mSelectedDayCode).toLocalDate()
                val startDate = Formatter.getDateFromTS(it.startTS)
                val endDate = Formatter.getDateFromTS(it.endTS)
                selectionDate in startDate..endDate
            }
        }

        val listItems = requireActivity().getEventListItems(filtered, mSelectedDayCode.isEmpty(), false)
        if (mSelectedDayCode.isNotEmpty()) {
            binding.monthDaySelectedDayLabel.text = Formatter.getDateFromCode(requireActivity(), mSelectedDayCode, false)
        }

        activity?.runOnUiThread {
            if (activity != null) {
                binding.monthDayEventsList.beVisibleIf(listItems.isNotEmpty())
                binding.monthDayNoEventsPlaceholder.beVisibleIf(listItems.isEmpty())

                val currAdapter = binding.monthDayEventsList.adapter
                if (currAdapter == null) {
                    EventListAdapter(activity as SimpleActivity, listItems, true, false, this, binding.monthDayEventsList) {
                        if (it is ListEvent) {
                            activity?.editEvent(it)
                        }
                    }.apply {
                        binding.monthDayEventsList.adapter = this
                    }

                    if (requireContext().areSystemAnimationsEnabled) {
                        binding.monthDayEventsList.scheduleLayoutAnimation()
                    }
                } else {
                    (currAdapter as EventListAdapter).updateListItems(listItems)
                }
                updateCalendar()
            }
        }
    }

    private fun setupButtons() {
        val textColor = requireContext().getProperTextColor()
        binding.apply {
            monthDaySelectedDayLabel.setTextColor(textColor)
            monthDayNoEventsPlaceholder.setTextColor(textColor)
        }
    }

    fun printCurrentView() {}

    fun getNewEventDayCode() = mSelectedDayCode.ifEmpty { null }

    private fun getMonthLabel(shownMonthDateTime: DateTime): String {
        var month = Formatter.getMonthName(requireActivity(), shownMonthDateTime.monthOfYear)
        val targetYear = shownMonthDateTime.toString(YEAR_PATTERN)
        if (targetYear != DateTime().toString(YEAR_PATTERN)) {
            month += " $targetYear"
        }
        return month
    }

    override fun refreshItems() {
        val startDateTime = Formatter.getLocalDateTimeFromCode(mDayCode).minusWeeks(1)
        val endDateTime = startDateTime.plusWeeks(7)
        activity?.eventsHelper?.getEvents(startDateTime.seconds(), endDateTime.seconds()) { events ->
            mListEvents = events
            activity?.runOnUiThread {
                updateVisibleEvents()
            }
        }
    }

    private fun handleListScroll(rv: RecyclerView, dy: Int) {
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val firstPos = lm.findFirstVisibleItemPosition()
        if (firstPos == RecyclerView.NO_POSITION) return

        val adapter = rv.adapter as? EventListAdapter ?: return
        val dayCode = adapter.getDayCodeAtPosition(firstPos) ?: return

        binding.monthDayViewWrapper.setTrackedDayCode(dayCode)

        val weekIndex = binding.monthDayViewWrapper.getWeekIndexForDayCode(dayCode)
        binding.monthDayViewWrapper.setVisibleWeek(weekIndex)

        if (rv.scrollState != RecyclerView.SCROLL_STATE_DRAGGING) return

        val distance = binding.monthDayViewWrapper.getCollapseDistance().toFloat()
        if (distance <= 0f) return

        accumulatedScroll += dy
        val absScroll = kotlin.math.abs(accumulatedScroll)
        if (absScroll < collapseDeadZonePx) return

        val effectiveScroll = if (accumulatedScroll > 0f) {
            accumulatedScroll - collapseDeadZonePx
        } else {
            accumulatedScroll + collapseDeadZonePx
        }

        val deltaProgress = effectiveScroll / distance
        val newProgress = (binding.monthDayViewWrapper.getCollapseProgress() + deltaProgress)
            .coerceIn(0f, 1f)
        binding.monthDayViewWrapper.setCollapseProgress(newProgress)

        accumulatedScroll = if (accumulatedScroll > 0f) collapseDeadZonePx else -collapseDeadZonePx
    }

}
