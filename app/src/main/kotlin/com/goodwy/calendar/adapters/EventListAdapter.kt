package com.goodwy.calendar.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.goodwy.calendar.R
import com.goodwy.calendar.activities.SimpleActivity
import com.goodwy.calendar.databinding.EventListItemBinding
import com.goodwy.calendar.databinding.EventListItemWidgetBinding
import com.goodwy.calendar.databinding.EventListSectionDayBinding
import com.goodwy.calendar.databinding.EventListSectionDayWidgetBinding
import com.goodwy.calendar.databinding.EventListSectionMonthBinding
import com.goodwy.calendar.dialogs.DeleteEventDialog
import com.goodwy.calendar.extensions.*
import com.goodwy.calendar.helpers.*
import com.goodwy.calendar.models.ListEvent
import com.goodwy.calendar.models.ListItem
import com.goodwy.calendar.models.ListSectionDay
import com.goodwy.calendar.models.ListSectionMonth
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.MEDIUM_ALPHA
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.interfaces.RefreshRecyclerViewListener
import com.goodwy.commons.views.MyRecyclerView

class EventListAdapter(
    activity: SimpleActivity,
    var listItems: ArrayList<ListItem>,
    val allowLongClick: Boolean,
    val widgetView: Boolean = false,
    val listener: RefreshRecyclerViewListener?,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private val allDayString = resources.getString(R.string.all_day)
    private val displayDescription = activity.config.displayDescription
    private val replaceDescription = activity.config.replaceDescription
    private val dimPastEvents = activity.config.dimPastEvents
    private val dimCompletedTasks = activity.config.dimCompletedTasks
    private val now = getNowSeconds()
    private var use24HourFormat = activity.config.use24HourFormat
    private var currentItemsHash = listItems.hashCode()
    private var isPrintVersion = false
    private val mediumMargin = activity.resources.getDimension(com.goodwy.commons.R.dimen.medium_margin).toInt()

    init {
        setupDragListener(true)
        val firstNonPastSectionIndex = listItems.indexOfFirst { it is ListSectionDay && !it.isPastSection }
        if (firstNonPastSectionIndex != -1) {
            activity.runOnUiThread {
                recyclerView.scrollToPosition(firstNonPastSectionIndex)
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_event_list

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_share -> shareEvents()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = listItems.filterIsInstance<ListEvent>().size

    override fun getIsItemSelectable(position: Int) = listItems.getOrNull(position) is ListEvent

    override fun getItemSelectionKey(position: Int) = (listItems.getOrNull(position) as? ListEvent)?.hashCode()

    override fun getItemKeyPosition(key: Int) = listItems.indexOfFirst { (it as? ListEvent)?.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyRecyclerViewAdapter.ViewHolder {
        val layoutInflater = activity.layoutInflater
        val binding = when (viewType) {
            ITEM_SECTION_DAY -> {
                if (widgetView) EventListSectionDayWidgetBinding.inflate(layoutInflater, parent, false)
                else EventListSectionDayBinding.inflate(layoutInflater, parent, false)
            }
            ITEM_SECTION_MONTH -> EventListSectionMonthBinding.inflate(layoutInflater, parent, false)
            else -> {
                if (widgetView) EventListItemWidgetBinding.inflate(layoutInflater, parent, false)
                else EventListItemBinding.inflate(layoutInflater, parent, false)
            }
        }

        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        val listItem = listItems[position]
        holder.bindView(listItem, allowSingleClick = true, allowLongClick = allowLongClick && listItem is ListEvent) { itemView, _ ->
            when (listItem) {
                is ListSectionDay -> setupListSectionDay(itemView, listItem)
                is ListEvent -> setupListEvent(itemView, listItem)
                is ListSectionMonth -> setupListSectionMonth(itemView, listItem)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = listItems.size

    override fun getItemViewType(position: Int) = when {
        listItems[position] is ListEvent -> ITEM_EVENT
        listItems[position] is ListSectionDay -> ITEM_SECTION_DAY
        else -> ITEM_SECTION_MONTH
    }

    fun toggle24HourFormat(use24HourFormat: Boolean) {
        this.use24HourFormat = use24HourFormat
        notifyDataSetChanged()
    }

    fun updateListItems(newListItems: ArrayList<ListItem>) {
        if (newListItems.hashCode() != currentItemsHash) {
            currentItemsHash = newListItems.hashCode()
            listItems = newListItems.clone() as ArrayList<ListItem>
            recyclerView.resetItemCount()
            notifyDataSetChanged()
            finishActMode()
        }
    }

    fun togglePrintMode() {
        isPrintVersion = !isPrintVersion
        textColor = if (isPrintVersion) {
            resources.getColor(com.goodwy.commons.R.color.theme_light_text_color)
        } else {
            activity.getProperTextColor()
        }
        notifyDataSetChanged()
    }

    @SuppressLint("SetTextI18n")
    private fun setupListEvent(view: View, listEvent: ListEvent) {
        if (widgetView) {
            EventListItemWidgetBinding.bind(view).apply {
                var newTextColor = listEvent.color ?: if (activity.config.widgetBgColor == Color.WHITE) Color.BLACK else Color.WHITE
                eventItemColorBackground.applyColorFilter(newTextColor.adjustAlpha(0.1f))
                eventItemColorBar.applyColorFilter(newTextColor)

                eventItemHolder.isSelected = selectedKeys.contains(listEvent.hashCode())
                eventItemTitle.text = listEvent.title
                eventItemTitle.checkViewStrikeThrough(listEvent.shouldStrikeThrough())
                eventItemTime.text = if (listEvent.isAllDay) allDayString else Formatter.getTimeFromTS(activity, listEvent.startTS)
                if (listEvent.startTS != listEvent.endTS) {
                    if (!listEvent.isAllDay) {
                        eventItemTime.text = "${eventItemTime.text}\n${Formatter.getTimeFromTS(activity, listEvent.endTS)}"
                    }

                    val startCode = Formatter.getDayCodeFromTS(listEvent.startTS)
                    val endCode = Formatter.getDayCodeFromTS(listEvent.endTS)
                    if (startCode != endCode) {
                        eventItemTime.text = "${eventItemTime.text} (${Formatter.getDateDayTitle(endCode)})"
                    }
                }

                eventItemColorBar.beVisibleIf(!listEvent.isTask)
                eventItemTaskImage.beVisibleIf(listEvent.isTask)
                eventItemTaskImage.applyColorFilter(newTextColor)

                val smallMargin = activity.resources.getDimension(com.goodwy.commons.R.dimen.small_margin).toInt()
                val normalMargin = activity.resources.getDimension(com.goodwy.commons.R.dimen.normal_margin).toInt()
                if (listEvent.isTask) {
                    eventItemTitle.setPadding(0, 0, smallMargin, 0)
                } else {
                    eventItemTitle.setPadding(normalMargin, 0, smallMargin, 0)
                }

                newTextColor = if (activity.config.widgetBgColor.getContrastColor() == Color.WHITE) newTextColor.lightenColor(20)
                                else newTextColor.darkenColor(36)
                if (listEvent.isAllDay || listEvent.startTS <= now && listEvent.endTS <= now) {
                    if (listEvent.isAllDay && Formatter.getDayCodeFromTS(listEvent.startTS) == Formatter.getDayCodeFromTS(now) && !isPrintVersion) {
                        newTextColor = properPrimaryColor
                    }

                    val adjustAlpha = if (listEvent.isTask) {
                        dimCompletedTasks && listEvent.isTaskCompleted
                    } else {
                        dimPastEvents && listEvent.isPastEvent && !isPrintVersion
                    }
                    if (adjustAlpha) {
                        newTextColor = newTextColor.adjustAlpha(MEDIUM_ALPHA)
                    }
                } else if (listEvent.startTS <= now && listEvent.endTS >= now && !isPrintVersion) {
                    newTextColor = properPrimaryColor
                }

                eventItemTime.setTextColor(newTextColor)
                eventItemTitle.setTextColor(newTextColor)

            }
        } else {
            EventListItemBinding.bind(view).apply {
                eventItemHolder.isSelected = selectedKeys.contains(listEvent.hashCode())
//            eventItemHolder.background.applyColorFilter(textColor)
                eventItemTitle.text = listEvent.title
                eventItemTitle.checkViewStrikeThrough(listEvent.shouldStrikeThrough())
                eventItemTime.text = if (listEvent.isAllDay) allDayString else Formatter.getTimeFromTS(activity, listEvent.startTS)
                if (listEvent.startTS != listEvent.endTS) {
                    if (!listEvent.isAllDay) {
                        eventItemTime.text = "${eventItemTime.text}\n${Formatter.getTimeFromTS(activity, listEvent.endTS)}"
                    }

                    val startCode = Formatter.getDayCodeFromTS(listEvent.startTS)
                    val endCode = Formatter.getDayCodeFromTS(listEvent.endTS)
                    if (startCode != endCode) {
                        eventItemTime.text = "${eventItemTime.text} (${Formatter.getDateDayTitle(endCode)})"
                    }
                }

                eventItemDescription.text = if (replaceDescription) listEvent.location else listEvent.description.replace("\n", " ")
                eventItemDescription.beVisibleIf(displayDescription && eventItemDescription.text.isNotEmpty())
                eventItemColorBar.background.applyColorFilter(listEvent.color ?: if (activity.getProperBackgroundColor() == Color.WHITE) Color.BLACK else Color.WHITE)

                var newTextColor = textColor
                if (listEvent.isAllDay || listEvent.startTS <= now && listEvent.endTS <= now) {
                    if (listEvent.isAllDay && Formatter.getDayCodeFromTS(listEvent.startTS) == Formatter.getDayCodeFromTS(now) && !isPrintVersion) {
                        newTextColor = properPrimaryColor
                    }

                    val adjustAlpha = if (listEvent.isTask) {
                        dimCompletedTasks && listEvent.isTaskCompleted
                    } else {
                        dimPastEvents && listEvent.isPastEvent && !isPrintVersion
                    }
                    if (adjustAlpha) {
                        newTextColor = newTextColor.adjustAlpha(MEDIUM_ALPHA)
                    }
                } else if (listEvent.startTS <= now && listEvent.endTS >= now && !isPrintVersion) {
                    newTextColor = properPrimaryColor
                }

                eventItemTime.setTextColor(newTextColor)
                eventItemTitle.setTextColor(newTextColor)
                eventItemDescription.setTextColor(newTextColor)
                eventItemTaskImage.applyColorFilter(newTextColor)
                eventItemTaskImage.beVisibleIf(listEvent.isTask)

                val startMargin = if (listEvent.isTask) {
                    0
                } else {
                    mediumMargin
                }

                (eventItemTitle.layoutParams as ConstraintLayout.LayoutParams).marginStart = startMargin
            }
        }
    }

    private fun setupListSectionDay(view: View, listSectionDay: ListSectionDay) {
        if (widgetView) {
            EventListSectionDayWidgetBinding.bind(view).eventSectionTitle.apply {
                text = listSectionDay.title
                setTextColor(textColor)
            }
        } else {
            EventListSectionDayBinding.bind(view).eventSectionTitle.apply {
                text = listSectionDay.title
                val dayColor = if (listSectionDay.isToday) properPrimaryColor else textColor
                setTextColor(dayColor)
            }
        }
    }

    private fun setupListSectionMonth(view: View, listSectionMonth: ListSectionMonth) {
        EventListSectionMonthBinding.bind(view).eventSectionTitle.apply {
            text = listSectionMonth.title
            setTextColor(properPrimaryColor)
        }
    }

    private fun shareEvents() = activity.shareEvents(getSelectedEventIds())

    private fun getSelectedEventIds() =
        listItems.filter { it is ListEvent && selectedKeys.contains(it.hashCode()) }.map { (it as ListEvent).id }.toMutableList() as ArrayList<Long>

    private fun askConfirmDelete() {
        val eventIds = getSelectedEventIds()
        val eventsToDelete = listItems.filter { selectedKeys.contains((it as? ListEvent)?.hashCode()) } as List<ListEvent>
        val timestamps = eventsToDelete.mapNotNull { (it as? ListEvent)?.startTS }

        val hasRepeatableEvent = eventsToDelete.any { it.isRepeatable }
        DeleteEventDialog(activity, eventIds, hasRepeatableEvent) {
            listItems.removeAll(eventsToDelete)

            ensureBackgroundThread {
                val nonRepeatingEventIDs = eventsToDelete.filter { !it.isRepeatable }.map { it.id }.toMutableList()
                activity.eventsHelper.deleteEvents(nonRepeatingEventIDs, true)

                val repeatingEventIDs = eventsToDelete.filter { it.isRepeatable }.map { it.id }
                activity.handleEventDeleting(repeatingEventIDs, timestamps, it)
                activity.runOnUiThread {
                    listener?.refreshItems()
                    finishActMode()
                }
            }
        }
    }

    fun getDayCodeAtPosition(position: Int): String? {
        val item = listItems[position] ?: return null
        return when (item) {
            is ListEvent -> Formatter.getDayCodeFromTS(item.startTS)
            is ListSectionDay -> item.code
            else -> null
        }
    }
}
