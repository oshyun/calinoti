package com.calinoti.app.notification

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
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
        // 캘린더 앱 탐침(PackageManager 쿼리)은 행마다가 아니라 뷰 조립당 한 번만 한다.
        val canOpenEventRows = canOpenEventInCalendarApp()
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
                        createEventItemViews(listEntry.entry, canOpenEventRows),
                    )
                }
            }
            addedItemCount++
        }
        return rootViews
    }

    private fun createEventItemViews(entry: AgendaEntry, canOpenEventRows: Boolean): RemoteViews {
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
        if (canOpenEventRows) {
            // 줄에 클릭이 걸리면 탭이 그 줄에서 소비된다. 캘린더 앱이 없는 기기에서는
            // 걸지 않아 알림 전체 contentIntent가 행을 포함해 그대로 동작하게 둔다.
            itemViews.setOnClickPendingIntent(
                R.id.notification_event_item,
                createOpenEventPendingIntent(entry.eventId),
            )
        }
        return itemViews
    }

    /**
     * 캘린더 앱이 일정 상세(content://com.android.calendar/events의 ACTION_VIEW)를 열 수 있는지.
     * 알림을 조립할 때마다 확인한다.
     */
    private fun canOpenEventInCalendarApp(): Boolean =
        context.packageManager.queryIntentActivities(
            buildOpenEventIntent(eventId = PROBE_EVENT_ID),
            PackageManager.MATCH_DEFAULT_ONLY,
        ).isNotEmpty()

    private fun createOpenEventPendingIntent(eventId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            EVENT_CLICK_REQUEST_CODE,
            buildOpenEventIntent(eventId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun buildOpenEventIntent(eventId: Long): Intent =
        Intent(Intent.ACTION_VIEW)
            .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))

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

        // 행 구분은 requestCode가 아니라 인텐트 data(이벤트 URI)가 담당한다. requestCode는
        // 알림 전체 클릭의 CONTENT_REQUEST_CODE(1002)와 겹치지 않는 고정값이고, 접힘·펼침 뷰가
        // 같은 조합을 요청하면 FLAG_UPDATE_CURRENT로 같은 레코드가 갱신 재사용된다.
        const val EVENT_CLICK_REQUEST_CODE = 1003

        // 일정 열기 capability 확인용 탐침 id. 실제 일정 id가 아니라 인텐트 shape만 맞으면 충분하다.
        const val PROBE_EVENT_ID = 0L

        val dayHeaderFormatter = DateTimeFormatter.ofPattern("MM.dd, EEEE", Locale.getDefault())

        // 시스템의 12/24시간 설정을 자동으로 따른다.
        val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }
}
