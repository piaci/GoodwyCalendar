package com.goodwy.calendar.fragments

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.goodwy.calendar.R
import com.goodwy.calendar.activities.MainActivity
import com.goodwy.calendar.databinding.FragmentMonthBinding
import com.goodwy.calendar.databinding.TopNavigationBinding
import com.goodwy.calendar.extensions.config
import com.goodwy.calendar.extensions.getViewBitmap
import com.goodwy.calendar.extensions.printBitmap
import com.goodwy.calendar.helpers.Config
import com.goodwy.calendar.helpers.DAY_CODE
import com.goodwy.calendar.helpers.Formatter
import com.goodwy.calendar.helpers.MonthlyCalendarImpl
import com.goodwy.calendar.interfaces.MonthlyCalendar
import com.goodwy.calendar.interfaces.NavigationListener
import com.goodwy.calendar.models.DayMonthly
import com.goodwy.commons.extensions.applyColorFilter
import com.goodwy.commons.extensions.beGone
import com.goodwy.commons.extensions.beVisible
import com.goodwy.commons.extensions.getProperTextColor
import org.joda.time.DateTime

class MonthFragment : Fragment(), MonthlyCalendar {
    private var mTextColor = 0
    private var mShowWeekNumbers = false
    private var mDayCode = ""
    private var mPackageName = ""
    private var mLastHash = 0L
    private var mCalendar: MonthlyCalendarImpl? = null

    var listener: NavigationListener? = null

    private lateinit var mRes: Resources
    private lateinit var mConfig: Config
    private lateinit var binding: FragmentMonthBinding
    private lateinit var topNavigationBinding: TopNavigationBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMonthBinding.inflate(inflater, container, false)
        topNavigationBinding = TopNavigationBinding.bind(binding.root)
        mRes = resources
        mPackageName = requireActivity().packageName
        mDayCode = requireArguments().getString(DAY_CODE)!!
        mConfig = requireContext().config
        storeStateVariables()

        setupButtons()
        mCalendar = MonthlyCalendarImpl(this, requireContext())

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
            topNavigationBinding.topValue.apply {
                text = month
                contentDescription = text

                if (activity != null) {
                    setTextColor(requireActivity().getProperTextColor())
                }
            }
            updateDays(days)
        }
    }

    private fun setupButtons() {
        mTextColor = requireContext().getProperTextColor()

        topNavigationBinding.topValue.apply {
            setTextColor(requireContext().getProperTextColor())
            setOnClickListener {
                (activity as MainActivity).showGoToDateDialog()
            }
        }
    }

    private fun updateDays(days: ArrayList<DayMonthly>) {
        binding.monthViewWrapper.updateDays(days, true) {
            (activity as MainActivity).openDayFromMonthly(Formatter.getDateTimeFromCode(it.code))
        }
    }

    fun printCurrentView() {
        topNavigationBinding.apply {
            topValue.setTextColor(resources.getColor(com.goodwy.commons.R.color.theme_light_text_color))
            binding.monthViewWrapper.togglePrintMode()

            requireContext().printBitmap(binding.monthCalendarHolder.getViewBitmap())

            topValue.setTextColor(requireContext().getProperTextColor())
            binding.monthViewWrapper.togglePrintMode()
        }
    }
}
