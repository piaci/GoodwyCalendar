package com.goodwy.calendar.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.goodwy.calendar.activities.MainActivity
import com.goodwy.calendar.activities.SimpleActivity
import com.goodwy.calendar.adapters.DayEventsAdapter
import com.goodwy.calendar.databinding.FragmentDayBinding
import com.goodwy.calendar.databinding.TopNavigationBinding
import com.goodwy.calendar.extensions.config
import com.goodwy.calendar.extensions.eventsHelper
import com.goodwy.calendar.extensions.getViewBitmap
import com.goodwy.calendar.extensions.printBitmap
import com.goodwy.calendar.helpers.*
import com.goodwy.calendar.interfaces.NavigationListener
import com.goodwy.calendar.models.Event
import com.goodwy.commons.extensions.*

class DayFragment : Fragment() {
    var mListener: NavigationListener? = null
    private var mTextColor = 0
    private var mDayCode = ""
    private var lastHash = 0

    private lateinit var binding: FragmentDayBinding
    private lateinit var topNavigationBinding: TopNavigationBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDayBinding.inflate(inflater, container, false)
        topNavigationBinding = TopNavigationBinding.bind(binding.root)
        mDayCode = requireArguments().getString(DAY_CODE)!!
        setupButtons()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updateCalendar()
    }

    private fun setupButtons() {
        mTextColor = requireContext().getProperTextColor()

        val day = Formatter.getDayTitle(requireContext(), mDayCode)
        topNavigationBinding.topValue.apply {
            setTextColor(mTextColor)

            val spannable = SpannableString(day)

            val pipeIndex = day.indexOf('|')
            if (pipeIndex >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(requireContext().getProperAccentColor()),
                    pipeIndex,
                    pipeIndex + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            text = spannable
            contentDescription = day
            setOnClickListener {
                (activity as MainActivity).showGoToDateDialog()
            }
        }
    }

    fun updateCalendar() {
        val startTS = Formatter.getDayStartTS(mDayCode)
        val endTS = Formatter.getDayEndTS(mDayCode)
        context?.eventsHelper?.getEvents(startTS, endTS) {
            receivedEvents(it)
        }
    }

    private fun receivedEvents(events: List<Event>) {
        val newHash = events.hashCode()
        if (newHash == lastHash || !isAdded) {
            return
        }
        lastHash = newHash

        val replaceDescription = requireContext().config.replaceDescription
        val sorted = ArrayList(events.sortedWith(compareBy({ !it.getIsAllDay() }, { it.startTS }, { it.endTS }, { it.title }, {
            if (replaceDescription) it.location else it.description
        })))

        activity?.runOnUiThread {
            updateEvents(sorted)
        }
    }

    private fun updateEvents(events: ArrayList<Event>) {
        if (activity == null)
            return

        DayEventsAdapter(activity as SimpleActivity, events, binding.dayEvents, mDayCode) {
            editEvent(it as Event)
        }.apply {
            binding.dayEvents.adapter = this
        }

        if (requireContext().areSystemAnimationsEnabled) {
            binding.dayEvents.scheduleLayoutAnimation()
        }
    }

    private fun editEvent(event: Event) {
        Intent(context, getActivityToOpen(event.isTask())).apply {
            putExtra(EVENT_ID, event.id)
            putExtra(EVENT_OCCURRENCE_TS, event.startTS)
            putExtra(IS_TASK_COMPLETED, event.isTaskCompleted())
            startActivity(this)
        }
    }

    fun printCurrentView() {
        topNavigationBinding.apply {
            topValue.setTextColor(resources.getColor(com.goodwy.commons.R.color.theme_light_text_color))
            (binding.dayEvents.adapter as? DayEventsAdapter)?.togglePrintMode()

            Handler().postDelayed({
                requireContext().printBitmap(binding.dayHolder.getViewBitmap())

                Handler().postDelayed({
                    topValue.setTextColor(requireContext().getProperTextColor())
                    (binding.dayEvents.adapter as? DayEventsAdapter)?.togglePrintMode()
                }, 1000)
            }, 1000)
        }
    }
}
