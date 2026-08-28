package com.calinoti.app.notification

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.View
import android.widget.RemoteViews
import com.calinoti.app.R
import com.calinoti.app.data.AgendaEntry
import com.calinoti.app.data.AgendaListEntry
import com.calinoti.app.data.NotificationSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** 아젠다 데이터를 알림용 RemoteViews 레이아웃으로 조립한다. */
class AgendaRemoteViewsFactory(private val context: Context) {

    /** 알림이 접힌 상태에서 보일 요약 뷰. 항목 몇 개만 담는다. */
    fun createCollapsedViews(
        listEntries: List<AgendaListEntry>,
        spacing: NotificationSpacing,
        notificationTextSizeSp: Int,
    ): RemoteViews = createAgendaViews(
        listEntries,
        itemLimit = COLLAPSED_ITEM_LIMIT,
        spacing = spacing,
        notificationTextSizeSp = notificationTextSizeSp,
    )

    /** 알림을 펼쳤을 때 보일 전체 뷰. [maxVisibleEntries]개까지만 담는다. */
    fun createExpandedViews(
        listEntries: List<AgendaListEntry>,
        maxVisibleEntries: Int,
        spacing: NotificationSpacing,
        notificationTextSizeSp: Int,
    ): RemoteViews = createAgendaViews(
        listEntries,
        itemLimit = maxVisibleEntries,
        spacing = spacing,
        notificationTextSizeSp = notificationTextSizeSp,
    )

    private fun createAgendaViews(
        listEntries: List<AgendaListEntry>,
        itemLimit: Int,
        spacing: NotificationSpacing,
        notificationTextSizeSp: Int,
    ): RemoteViews {
        // 글자 크기는 레이아웃 xml이 아니라 이 설정값이 유일한 출처다.
        // 시각·위치·날짜 헤더는 제목보다 2sp 작게 표시한다 (기존 레이아웃의 15/13sp 관계).
        val titleTextSizeSp = notificationTextSizeSp.toFloat()
        val secondaryTextSizeSp = (notificationTextSizeSp - SECONDARY_TEXT_SIZE_OFFSET_SP).toFloat()
        val rootViews = RemoteViews(context.packageName, R.layout.notification_agenda)
        rootViews.removeAllViews(R.id.notification_agenda_container)
        if (listEntries.isEmpty()) {
            val emptyViews = RemoteViews(context.packageName, R.layout.notification_item_empty)
            emptyViews.setTextViewTextSize(R.id.agenda_empty_text, COMPLEX_UNIT_SP, secondaryTextSizeSp)
            rootViews.addView(R.id.notification_agenda_container, emptyViews)
            return rootViews
        }
        // 캘린더 앱 탐침(PackageManager 쿼리)은 행마다가 아니라 뷰 조립당 한 번만 한다.
        val canOpenEventRows = canOpenEventInCalendarApp()
        // 간격은 "뒤 항목의 paddingTop이 담당" 규칙으로 배분한다. AgendaListBuilder가 헤더를
        // 일정 직전에만 추가하므로 첫 항목은 항상 헤더고, 세 수직 간격이 서로 겹치지 않는다.
        val visibleEntries = listEntries.take(itemLimit)
        var previousListEntry: AgendaListEntry? = null
        for ((itemIndex, listEntry) in visibleEntries.withIndex()) {
            val topPaddingDp = when {
                itemIndex == 0 -> FIRST_ITEM_TOP_PADDING_DP
                listEntry is AgendaListEntry.DayHeader -> spacing.betweenDayHeadersSpacingDp
                previousListEntry is AgendaListEntry.DayHeader -> spacing.dayHeaderToEventSpacingDp
                else -> spacing.betweenEventsSpacingDp
            }
            val bottomPaddingDp =
                if (itemIndex == visibleEntries.lastIndex) LAST_ITEM_BOTTOM_PADDING_DP else 0
            when (listEntry) {
                is AgendaListEntry.DayHeader -> {
                    val headerViews =
                        RemoteViews(context.packageName, R.layout.notification_item_day_header)
                    headerViews.setTextViewText(
                        R.id.day_header_text,
                        formatDayHeaderText(listEntry.dayStartMilliseconds),
                    )
                    headerViews.setTextViewTextSize(
                        R.id.day_header_text,
                        COMPLEX_UNIT_SP,
                        secondaryTextSizeSp,
                    )
                    headerViews.applyItemPadding(
                        viewId = R.id.day_header_text,
                        startPaddingDp = spacing.dayHeaderStartPaddingDp,
                        topPaddingDp = topPaddingDp,
                        bottomPaddingDp = bottomPaddingDp,
                    )
                    rootViews.addView(R.id.notification_agenda_container, headerViews)
                }

                is AgendaListEntry.Event -> {
                    val eventItemViews = createEventItemViews(
                        listEntry.entry,
                        canOpenEventRows,
                        titleTextSizeSp,
                        secondaryTextSizeSp,
                    )
                    eventItemViews.applyItemPadding(
                        viewId = R.id.notification_event_item,
                        startPaddingDp = spacing.eventStartPaddingDp,
                        topPaddingDp = topPaddingDp,
                        bottomPaddingDp = bottomPaddingDp,
                    )
                    rootViews.addView(R.id.notification_agenda_container, eventItemViews)
                }
            }
            previousListEntry = listEntry
        }
        return rootViews
    }

    private fun createEventItemViews(
        entry: AgendaEntry,
        canOpenEventRows: Boolean,
        titleTextSizeSp: Float,
        secondaryTextSizeSp: Float,
    ): RemoteViews {
        val itemViews = RemoteViews(context.packageName, R.layout.notification_item_event)
        if (entry.isAllDay) {
            itemViews.setViewVisibility(R.id.event_time_text, View.GONE)
        } else {
            itemViews.setViewVisibility(R.id.event_time_text, View.VISIBLE)
            itemViews.setTextViewText(R.id.event_time_text, formatTimeText(entry.beginTimeMilliseconds))
            itemViews.setTextViewTextSize(R.id.event_time_text, COMPLEX_UNIT_SP, secondaryTextSizeSp)
        }
        itemViews.setTextViewText(
            R.id.event_title_text,
            entry.title.ifEmpty { context.getString(R.string.agenda_untitled_event) },
        )
        itemViews.setTextViewTextSize(R.id.event_title_text, COMPLEX_UNIT_SP, titleTextSizeSp)
        // 제목 색은 캘린더 앱과 같은 캘린더 색으로 표시한다. 일정별 개별 색(EVENT_COLOR)은
        // 무시하고 캘린더 색만 따른다.
        itemViews.setTextColor(R.id.event_title_text, entry.calendarColor)
        if (entry.location.isNullOrBlank()) {
            itemViews.setViewVisibility(R.id.event_location_text, View.GONE)
        } else {
            itemViews.setViewVisibility(R.id.event_location_text, View.VISIBLE)
            itemViews.setTextViewText(R.id.event_location_text, entry.location)
            itemViews.setTextViewTextSize(R.id.event_location_text, COMPLEX_UNIT_SP, secondaryTextSizeSp)
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

    /**
     * 항목 뷰의 여백을 dp에서 픽셀로 바꿔 적용한다. setViewPadding은 지정하지 않은 면을
     * 0으로 덮어쓰므로 네 면을 항상 모두 지정한다 — 레이아웃 XML에는 padding이 없다(SSOT).
     */
    private fun RemoteViews.applyItemPadding(
        viewId: Int,
        startPaddingDp: Int,
        topPaddingDp: Int,
        bottomPaddingDp: Int,
    ) {
        val displayMetrics = context.resources.displayMetrics

        fun toPixels(paddingDp: Int): Int =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                paddingDp.toFloat(),
                displayMetrics,
            ).toInt()

        // QUIRK(remoteviews-absolute-padding): RemoteViews에는 paddingStart 액션이 없어 절대
        //   left 좌표를 쓴다. 시스템 로캘이 RTL이면 start/end 값을 뒤집어 보정한다. 앱과
        //   SystemUI가 같은 시스템 로캘로 방향을 정하므로 이 검사로 서로 일치한다.
        // QUIRK-REMOVE-WHEN: start 패딩을 지정하는 RemoteViews API가 추가될 때
        val isRtlLayout =
            context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val startPaddingPixels = toPixels(startPaddingDp)
        val endPaddingPixels = toPixels(ITEM_HORIZONTAL_INSET_DP)
        setViewPadding(
            viewId,
            if (isRtlLayout) endPaddingPixels else startPaddingPixels,
            toPixels(topPaddingDp),
            if (isRtlLayout) startPaddingPixels else endPaddingPixels,
            toPixels(bottomPaddingDp),
        )
    }

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

        // 항목의 End(오른쪽) 고정 여백과 알림 맨 위/맨 아래 바깥 여백. 사용자가 조절하지 않는
        // 렌더링 상수로, v1.2.4까지 레이아웃 XML에 있던 값을 옮겨온 것과 같다.
        const val ITEM_HORIZONTAL_INSET_DP = 16
        const val FIRST_ITEM_TOP_PADDING_DP = 8
        const val LAST_ITEM_BOTTOM_PADDING_DP = 4

        // 시각·위치·날짜 헤더가 제목 글자보다 작은 정도. 기존 레이아웃의 15/13sp 관계를 유지한다.
        const val SECONDARY_TEXT_SIZE_OFFSET_SP = 2

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
