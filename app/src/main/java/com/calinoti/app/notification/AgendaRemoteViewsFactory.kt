package com.calinoti.app.notification

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.calinoti.app.R
import com.calinoti.app.data.AgendaEntry
import com.calinoti.app.data.AgendaListEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** 아젠다 데이터를 알림용 RemoteViews 레이아웃으로 조립한다. */
class AgendaRemoteViewsFactory(private val context: Context) {

    /** 알림이 접힌 상태에서 보일 요약 뷰. 항목 몇 개만 담는다. */
    fun createCollapsedViews(listEntries: List<AgendaListEntry>): RemoteViews =
        createAgendaViews(listEntries, itemLimit = COLLAPSED_ITEM_LIMIT)

    /** 알림을 펼쳤을 때 보일 전체 뷰. [maxVisibleEntries]개까지만 담는다. */
    fun createExpandedViews(listEntries: List<AgendaListEntry>, maxVisibleEntries: Int): RemoteViews =
        createAgendaViews(listEntries, itemLimit = maxVisibleEntries)

    private fun createAgendaViews(listEntries: List<AgendaListEntry>, itemLimit: Int): RemoteViews {
        val rootViews = RemoteViews(context.packageName, R.layout.notification_agenda)
        rootViews.removeAllViews(R.id.notification_agenda_container)
        if (listEntries.isEmpty()) {
            rootViews.addView(
                R.id.notification_agenda_container,
                RemoteViews(context.packageName, R.layout.notification_item_empty),
            )
            return rootViews
        }
        var addedItemCount = 0
        for (listEntry in listEntries) {
            if (addedItemCount >= itemLimit) break
            when (listEntry) {
                is AgendaListEntry.DayHeader -> {
                    val headerViews =
                        RemoteViews(context.packageName, R.layout.notification_item_day_header)
                    headerViews.setTextViewText(
                        R.id.day_header_text,
                        formatDayHeaderText(listEntry.dayStartMilliseconds),
                    )
                    rootViews.addView(R.id.notification_agenda_container, headerViews)
                }

                is AgendaListEntry.Event -> {
                    rootViews.addView(
                        R.id.notification_agenda_container,
                        createEventItemViews(listEntry.entry),
                    )
                }
            }
            addedItemCount++
        }
        return rootViews
    }

    private fun createEventItemViews(entry: AgendaEntry): RemoteViews {
        val itemViews = RemoteViews(context.packageName, R.layout.notification_item_event)
        if (entry.isAllDay) {
            itemViews.setViewVisibility(R.id.event_time_text, View.GONE)
        } else {
            itemViews.setViewVisibility(R.id.event_time_text, View.VISIBLE)
            itemViews.setTextViewText(R.id.event_time_text, formatTimeText(entry.beginTimeMilliseconds))
        }
        itemViews.setTextViewText(
            R.id.event_title_text,
            entry.title.ifEmpty { context.getString(R.string.agenda_untitled_event) },
        )
        if (entry.location.isNullOrBlank()) {
            itemViews.setViewVisibility(R.id.event_location_text, View.GONE)
        } else {
            itemViews.setViewVisibility(R.id.event_location_text, View.VISIBLE)
            itemViews.setTextViewText(R.id.event_location_text, entry.location)
        }
        return itemViews
    }

    /** "08.31, 금요일" 형태의 날짜 헤더. */
    private fun formatDayHeaderText(dayStartMilliseconds: Long): String =
        Instant.ofEpochMilli(dayStartMilliseconds)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(dayHeaderFormatter)

    private fun formatTimeText(timeMilliseconds: Long): String =
        Instant.ofEpochMilli(timeMilliseconds)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(timeFormatter)

    private companion object {
        const val COLLAPSED_ITEM_LIMIT = 3

        val dayHeaderFormatter = DateTimeFormatter.ofPattern("MM.dd, EEEE", Locale.getDefault())

        // 시스템의 12/24시간 설정을 자동으로 따른다.
        val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }
}
