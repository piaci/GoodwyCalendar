package com.goodwy.calendar.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.goodwy.calendar.activities.MainActivity
import com.goodwy.calendar.databinding.FragmentYearBinding
import com.goodwy.calendar.databinding.SmallMonthViewHolderBinding
import com.goodwy.calendar.databinding.TopNavigationBinding
import com.goodwy.calendar.extensions.config
import com.goodwy.calendar.extensions.getProperDayIndexInWeek
import com.goodwy.calendar.extensions.getViewBitmap
import com.goodwy.calendar.extensions.printBitmap
import com.goodwy.calendar.helpers.YEAR_LABEL
import com.goodwy.calendar.helpers.YearlyCalendarImpl
import com.goodwy.calendar.interfaces.NavigationListener
import com.goodwy.calendar.interfaces.YearlyCalendar
import com.goodwy.calendar.models.DayYearly
import com.goodwy.commons.extensions.getProperPrimaryColor
import com.goodwy.commons.extensions.getProperTextColor
import com.goodwy.commons.extensions.updateTextColors
import org.joda.time.DateTime

class YearFragment : Fragment(), YearlyCalendar {
    private var mYear = 0
    private var mFirstDayOfWeek = 0
    private var isPrintVersion = false
    private var lastHash = 0
    private var mCalendar: YearlyCalendarImpl? = null

    var listener: NavigationListener? = null

    private lateinit var binding: FragmentYearBinding
    private lateinit var topNavigationBinding: TopNavigationBinding
    private lateinit var monthHolders: List<SmallMonthViewHolderBinding>

    private val monthResIds = arrayOf(
        com.goodwy.commons.R.string.january,
        com.goodwy.commons.R.string.february,
        com.goodwy.commons.R.string.march,
        com.goodwy.commons.R.string.april,
        com.goodwy.commons.R.string.may,
        com.goodwy.commons.R.string.june,
        com.goodwy.commons.R.string.july,
        com.goodwy.commons.R.string.august,
        com.goodwy.commons.R.string.september,
        com.goodwy.commons.R.string.october,
        com.goodwy.commons.R.string.november,
        com.goodwy.commons.R.string.december
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentYearBinding.inflate(inflater, container, false)
        topNavigationBinding = TopNavigationBinding.bind(binding.root)
        monthHolders = arrayListOf(
            binding.month1Holder, binding.month2Holder, binding.month3Holder, binding.month4Holder, binding.month5Holder, binding.month6Holder,
            binding.month7Holder, binding.month8Holder, binding.month9Holder, binding.month10Holder, binding.month11Holder, binding.month12Holder
        ).apply {
            forEachIndexed { index, it ->
                it.monthLabel.text = getString(monthResIds[index])
            }
        }

        mYear = requireArguments().getInt(YEAR_LABEL)
        requireContext().updateTextColors(binding.calendarWrapper)
        setupMonths()
        setupButtons()

        mCalendar = YearlyCalendarImpl(this, requireContext(), mYear)
        return binding.root
    }

    override fun onPause() {
        super.onPause()
        mFirstDayOfWeek = requireContext().config.firstDayOfWeek
    }

    override fun onResume() {
        super.onResume()
        val firstDayOfWeek = requireContext().config.firstDayOfWeek
        if (firstDayOfWeek != mFirstDayOfWeek) {
            mFirstDayOfWeek = firstDayOfWeek
            setupMonths()
        }
        updateCalendar()
    }

    fun updateCalendar() {
        mCalendar?.getEvents(mYear)
    }

    private fun setupMonths() {
        val dateTime = DateTime().withYear(mYear).withHourOfDay(12)
        monthHolders.forEachIndexed { index, monthHolder ->
            val monthOfYear = index + 1
            val monthView = monthHolder.smallMonthView
            val curTextColor = when {
                isPrintVersion -> resources.getColor(com.goodwy.commons.R.color.theme_light_text_color)
                else -> requireContext().getProperTextColor()
            }

            monthHolder.monthLabel.setTextColor(curTextColor)
            val firstDayOfMonth = dateTime.withMonthOfYear(monthOfYear).withDayOfMonth(1)
            monthView.firstDay = requireContext().getProperDayIndexInWeek(firstDayOfMonth)
            val numberOfDays = dateTime.withMonthOfYear(monthOfYear).dayOfMonth().maximumValue
            monthView.setDays(numberOfDays)
            monthView.setOnClickListener {
                (activity as MainActivity).openMonthFromYearly(DateTime().withDate(mYear, monthOfYear, 1))
            }
        }

        if (!isPrintVersion) {
            val now = DateTime()
            markCurrentMonth(now)
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun setupButtons() {

        topNavigationBinding.topValue.apply {
            setTextColor(requireContext().getProperTextColor())
            setOnClickListener {
                (activity as MainActivity).showGoToDateDialog()
            }
        }
    }

    private fun markCurrentMonth(now: DateTime) {
        if (now.year == mYear) {
            val monthOfYear = now.monthOfYear
            val monthHolder = monthHolders[monthOfYear - 1]
            monthHolder.monthLabel.setTextColor(requireContext().getProperPrimaryColor())
            monthHolder.smallMonthView.todaysId = now.dayOfMonth
        }
    }

    override fun updateYearlyCalendar(events: SparseArray<ArrayList<DayYearly>>, hashCode: Int) {
        if (!isAdded) {
            return
        }

        if (hashCode == lastHash) {
            return
        }

        lastHash = hashCode
        monthHolders.forEachIndexed { index, monthHolder ->
            val monthView = monthHolder.smallMonthView
            val monthOfYear = index + 1
            monthView.setEvents(events.get(monthOfYear))
        }

        topNavigationBinding.topValue.post {
            topNavigationBinding.topValue.text = mYear.toString()
        }
    }

    fun printCurrentView() {
        isPrintVersion = true
        setupMonths()
        toggleSmallMonthPrintModes()

        requireContext().printBitmap(binding.calendarWrapper.getViewBitmap())

        isPrintVersion = false
        setupMonths()
        toggleSmallMonthPrintModes()
    }

    private fun toggleSmallMonthPrintModes() {
        monthHolders.forEach {
            it.smallMonthView.togglePrintMode()
        }
    }
}
