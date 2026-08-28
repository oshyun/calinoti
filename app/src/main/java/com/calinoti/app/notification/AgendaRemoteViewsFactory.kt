package com.calinoti.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.provider.CalendarContract
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.calinoti.app.R
import com.calinoti.app.data.AgendaEntry
import com.calinoti.app.data.AgendaListEntry
import com.calinoti.app.data.CalendarIntents
import com.calinoti.app.data.NotificationSpacing
import com.calinoti.app.data.UserPreferences
import com.calinoti.app.ui.CalendarColorTone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 아젠다 데이터를 알림용 RemoteViews 레이아웃으로 조립한다. */
class AgendaRemoteViewsFactory(private val context: Context) {

    /** 알림이 접힌 상태에서 보일 요약 뷰. 항목 몇 개만 담는다. */
    fun createCollapsedViews(
        listEntries: List<AgendaListEntry>,
        calendarAppPackageName: String,
        spacing: NotificationSpacing,
        notificationTextSizeSp: Int,
        allDayEventTextSizeSp: Int,
        currentTimeMilliseconds: Long,
    ): RemoteViews = createAgendaViews(
        listEntries,
        itemLimit = COLLAPSED_ITEM_LIMIT,
        calendarAppPackageName = calendarAppPackageName,
        spacing = spacing,
        notificationTextSizeSp = notificationTextSizeSp,
        allDayEventTextSizeSp = allDayEventTextSizeSp,
        currentTimeMilliseconds = currentTimeMilliseconds,
    )

    /** 알림을 펼쳤을 때 보일 전체 뷰. [maxVisibleEntries]개까지만 담는다. */
    fun createExpandedViews(
        listEntries: List<AgendaListEntry>,
        maxVisibleEntries: Int,
        calendarAppPackageName: String,
        spacing: NotificationSpacing,
        notificationTextSizeSp: Int,
        allDayEventTextSizeSp: Int,
        currentTimeMilliseconds: Long,
    ): RemoteViews = createAgendaViews(
        listEntries,
        itemLimit = maxVisibleEntries,
        calendarAppPackageName = calendarAppPackageName,
        spacing = spacing,
        notificationTextSizeSp = notificationTextSizeSp,
        allDayEventTextSizeSp = allDayEventTextSizeSp,
        currentTimeMilliseconds = currentTimeMilliseconds,
    )

    private fun createAgendaViews(
        listEntries: List<AgendaListEntry>,
        itemLimit: Int,
        calendarAppPackageName: String,
        spacing: NotificationSpacing,
        notificationTextSizeSp: Int,
        allDayEventTextSizeSp: Int,
        currentTimeMilliseconds: Long,
    ): RemoteViews {
        // 글자 크기는 레이아웃 xml이 아니라 이 설정값이 유일한 출처다. 종일 일정 제목은
        // 시간 있는 일정 제목 크기와 독립적인 설정값을 쓴다. 시각·위치·날짜 헤더는 시간
        // 있는 일정 제목보다 2sp 작게 표시한다 (기존 레이아웃의 15/13sp 관계).
        val titleTextSizeSp = notificationTextSizeSp.toFloat()
        val allDayTitleTextSizeSp = allDayEventTextSizeSp.toFloat()
        val secondaryTextSizeSp = (notificationTextSizeSp - SECONDARY_TEXT_SIZE_OFFSET_SP).toFloat()
        val rootViews = RemoteViews(context.packageName, R.layout.notification_agenda)
        rootViews.removeAllViews(R.id.notification_agenda_container)
        if (listEntries.isEmpty()) {
            val emptyViews = RemoteViews(context.packageName, R.layout.notification_item_empty)
            emptyViews.setTextViewTextSize(R.id.agenda_empty_text, COMPLEX_UNIT_SP, secondaryTextSizeSp)
            rootViews.addView(R.id.notification_agenda_container, emptyViews)
            return rootViews
        }
        // 일정 행 클릭 대상 탐침(PackageManager 쿼리)은 행마다가 아니라 뷰 조립당 한 번만 한다.
        val eventClickTarget = resolveEventClickTarget(calendarAppPackageName)
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
                        formatDayHeaderText(
                            dayStartMilliseconds = listEntry.dayStartMilliseconds,
                            currentTimeMilliseconds = currentTimeMilliseconds,
                        ),
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
                        eventClickTarget,
                        // 종일 일정 제목은 종일 전용 설정 크기를 쓴다.
                        titleTextSizeSp =
                            if (listEntry.entry.isAllDay) allDayTitleTextSizeSp else titleTextSizeSp,
                        secondaryTextSizeSp,
                        currentTimeMilliseconds,
                        spacing.timeToTitleSpacingDp,
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
        eventClickTarget: EventClickTarget,
        titleTextSizeSp: Float,
        secondaryTextSizeSp: Float,
        currentTimeMilliseconds: Long,
        timeToTitleSpacingDp: Int,
    ): RemoteViews {
        val itemViews = RemoteViews(context.packageName, R.layout.notification_item_event)
        // 시각-제목 간격은 RemoteViews에 margin 액션이 없어 시간 뷰의 end padding으로 준다.
        // 배경 없는 텍스트라 margin과 시각적으로 동일하다. 종일 일정은 시간 뷰가 GONE이라
        // 적용돼도 그려지지 않는다.
        itemViews.applyItemPadding(
            viewId = R.id.event_time_text,
            startPaddingDp = 0,
            endPaddingDp = timeToTitleSpacingDp,
            topPaddingDp = 0,
            bottomPaddingDp = 0,
        )
        if (entry.isAllDay) {
            itemViews.setViewVisibility(R.id.event_time_text, View.GONE)
        } else {
            itemViews.setViewVisibility(R.id.event_time_text, View.VISIBLE)
            itemViews.setTextViewText(R.id.event_time_text, formatTimeText(entry.beginTimeMilliseconds))
            itemViews.setTextViewTextSize(R.id.event_time_text, COMPLEX_UNIT_SP, secondaryTextSizeSp)
        }
        // 상대 시간 라벨은 그 일정 제목보다 2sp 작다. 종일 일정은 종일 제목 크기를 따른다.
        val relativeLabelTextSizeSp = titleTextSizeSp - SECONDARY_TEXT_SIZE_OFFSET_SP
        itemViews.setTextViewText(
            R.id.event_title_text,
            createEventTitleText(entry, currentTimeMilliseconds, relativeLabelTextSizeSp),
        )
        itemViews.setTextViewTextSize(R.id.event_title_text, COMPLEX_UNIT_SP, titleTextSizeSp)
        // 제목 색은 캘린더 앱과 같은 캘린더 색으로 표시한다. 일정별 개별 색(EVENT_COLOR)은
        // 무시하고 캘린더 색만 따른다. 색은 표준 톤으로 통일해 카드 배경에 묻히지 않게 한다.
        itemViews.setTextColor(
            R.id.event_title_text,
            CalendarColorTone.standardizeCalendarColor(
                color = entry.calendarColor,
                isDarkTheme = isSystemDarkTheme(),
            ),
        )
        if (entry.location.isNullOrBlank()) {
            itemViews.setViewVisibility(R.id.event_location_text, View.GONE)
        } else {
            itemViews.setViewVisibility(R.id.event_location_text, View.VISIBLE)
            itemViews.setTextViewText(R.id.event_location_text, entry.location)
            itemViews.setTextViewTextSize(R.id.event_location_text, COMPLEX_UNIT_SP, secondaryTextSizeSp)
        }
        if (eventClickTarget.canOpenEventRows) {
            // 클릭은 시간·제목·위치 텍스트에만 건다. 텍스트가 없는 행의 오른쪽 여백은
            // 클릭이 없어 알림 전체 contentIntent(지정 캘린더 앱 열기)로 넘어간다. 캘린더
            // 앱이 없는 기기에서는 걸지 않아 텍스트까지 알림 전체가 contentIntent다.
            val openEventPendingIntent =
                createOpenEventPendingIntent(entry.eventId, eventClickTarget.packageName)
            itemViews.setOnClickPendingIntent(R.id.event_time_text, openEventPendingIntent)
            itemViews.setOnClickPendingIntent(R.id.event_title_text, openEventPendingIntent)
            itemViews.setOnClickPendingIntent(R.id.event_location_text, openEventPendingIntent)
        }
        return itemViews
    }

    /**
     * 알림 카드는 시스템이 그리므로 카드 밝기는 시스템 다크 테마를 따른다. 앱은 uiMode를
     * 강제하지 않아(화면 테마가 라이트로 고정돼 있어도 Configuration은 시스템 값을 그대로
     * 반영한다) 앱 프로세스의 night mask가 곧 카드 테마다.
     */
    private fun isSystemDarkTheme(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** 일정 행 클릭 대상. [packageName]이 null이면 암시적 인텐트로 시스템이 처리 앱을 고른다. */
    private data class EventClickTarget(val packageName: String?, val canOpenEventRows: Boolean)

    /**
     * 지정 캘린더 앱이 일정 상세를 열 수 있으면 그 앱으로 한정하고, 지정이 없거나 이미
     * 지워졌으면 암시적 인텐트로 돌아간다. 어느 쪽도 처리 앱이 없으면 행 클릭을 걸지 않아
     * 알림 전체 contentIntent가 행을 포함해 동작하게 둔다.
     */
    private fun resolveEventClickTarget(calendarAppPackageName: String): EventClickTarget {
        if (calendarAppPackageName != UserPreferences.UNSPECIFIED_CALENDAR_APP_PACKAGE_NAME) {
            val selectedAppIntent =
                CalendarIntents.buildEventViewIntent(CalendarIntents.EVENT_PROBE_ID)
                    .setPackage(calendarAppPackageName)
            if (hasResolvingActivity(selectedAppIntent)) {
                return EventClickTarget(packageName = calendarAppPackageName, canOpenEventRows = true)
            }
        }
        val implicitIntent = CalendarIntents.buildEventViewIntent(CalendarIntents.EVENT_PROBE_ID)
        return EventClickTarget(
            packageName = null,
            canOpenEventRows = hasResolvingActivity(implicitIntent),
        )
    }

    private fun hasResolvingActivity(intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .isNotEmpty()

    /**
     * 뷰의 여백을 dp에서 픽셀로 바꿔 적용한다. setViewPadding은 지정하지 않은 면을
     * 0으로 덮어쓰므로 네 면을 항상 모두 지정한다 — 레이아웃 XML에는 padding이 없다(SSOT).
     */
    private fun RemoteViews.applyItemPadding(
        viewId: Int,
        startPaddingDp: Int,
        topPaddingDp: Int,
        bottomPaddingDp: Int,
        endPaddingDp: Int = ITEM_HORIZONTAL_INSET_DP,
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
        val endPaddingPixels = toPixels(endPaddingDp)
        setViewPadding(
            viewId,
            if (isRtlLayout) endPaddingPixels else startPaddingPixels,
            toPixels(topPaddingDp),
            if (isRtlLayout) startPaddingPixels else endPaddingPixels,
            toPixels(bottomPaddingDp),
        )
    }

    private fun createOpenEventPendingIntent(
        eventId: Long,
        targetPackageName: String?,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            EVENT_CLICK_REQUEST_CODE,
            CalendarIntents.buildEventViewIntent(eventId).apply {
                if (targetPackageName != null) setPackage(targetPackageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * "08.31, 금요일" 형태의 날짜 헤더. 오늘 날짜면 (오늘) 접미사를 붙이고 전체를 볼드로
     * 강조해 목록에서 오늘 그룹을 한눈에 찾게 한다.
     */
    private fun formatDayHeaderText(
        dayStartMilliseconds: Long,
        currentTimeMilliseconds: Long,
    ): CharSequence {
        val headerDay = findLocalDateOf(dayStartMilliseconds)
        val today = findLocalDateOf(currentTimeMilliseconds)
        if (headerDay != today) return headerDay.format(dayHeaderFormatter)
        val todayHeaderText = SpannableStringBuilder(headerDay.format(dayHeaderFormatter))
            .append(RELATIVE_TIME_LABEL_SEPARATOR)
            .append(context.getString(R.string.today_label))
        todayHeaderText.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            todayHeaderText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return todayHeaderText
    }

    private fun formatTimeText(timeMilliseconds: Long): String =
        Instant.ofEpochMilli(timeMilliseconds)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(timeFormatter)

    /**
     * 일정 줄의 제목 텍스트. 여러 날에 걸친 종일 일정이면 제목에 종료일을 붙이고,
     * 제목 뒤에는 상대 시간 라벨을 회색 작은 글씨로 이어 붙인다.
     * 이미 끝난 일정은 제목에 취소선을 긋고 (종료됨) 라벨을 붙인다.
     * 단위는 큰 쪽부터 골라 나머지는 버린다(23시간 59분 = 23시간).
     */
    private fun createEventTitleText(
        entry: AgendaEntry,
        currentTimeMilliseconds: Long,
        secondaryTextSizeSp: Float,
    ): CharSequence {
        // 여러 날에 걸친 종일 일정은 시작일 그룹에 표시하되 제목에 종료일을 붙인다.
        val baseTitle = entry.title.ifEmpty { context.getString(R.string.agenda_untitled_event) }
        val multidayAllDayLastDay = findAllDayLastDayOrNull(entry)
        val titleText =
            if (multidayAllDayLastDay == null) baseTitle
            else context.getString(
                R.string.agenda_multiday_title_format,
                baseTitle,
                multidayAllDayLastDay.format(multidayEndDateFormatter),
            )
        val isEventFinished = findEventFinishTimeMilliseconds(entry) <= currentTimeMilliseconds
        val relativeTimeLabel =
            if (isEventFinished) context.getString(R.string.agenda_finished)
            else formatRelativeTimeLabel(entry, currentTimeMilliseconds)
        val labelTextStartIndex = titleText.length + RELATIVE_TIME_LABEL_SEPARATOR.length
        val titleWithLabel = SpannableStringBuilder(titleText)
            .append(RELATIVE_TIME_LABEL_SEPARATOR)
            .append(relativeTimeLabel)
        if (isEventFinished) {
            // 끝난 일정은 제목에 취소선을 그어 예정·진행 일정과 한눈에 구분한다. 라벨은
            // 상태 표시이므로 취소선 대상에서 뺐다.
            titleWithLabel.setSpan(
                StrikethroughSpan(),
                0,
                titleText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        // 라벨 구간만 시간·위치 텍스트와 같은 secondary 톤(색·크기)으로 낮춘다.
        titleWithLabel.setSpan(
            ForegroundColorSpan(
                ContextCompat.getColor(context, R.color.notification_text_secondary),
            ),
            labelTextStartIndex,
            titleWithLabel.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        titleWithLabel.setSpan(
            AbsoluteSizeSpan(
                TypedValue.applyDimension(
                    COMPLEX_UNIT_SP,
                    secondaryTextSizeSp,
                    context.resources.displayMetrics,
                ).toInt(),
            ),
            labelTextStartIndex,
            titleWithLabel.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return titleWithLabel
    }

    /**
     * 제목 뒤에 붙일 상대 시간 라벨. 시간 있는 일정은 1시간 미만이면 분 단위, 24시간 미만이면
     * 시간 단위로 남은 시간을 계산하고, 그 이상은 남은 시각과 무관하게 달력 날짜 기준으로
     * (N일 뒤)를 붙인다 — 24시간으로 나눈 몫으로 계산하면 시작 전날 오후부터 하루씩 줄어들어
     * 날짜 감각과 어긋난다. 종일 일정은 날짜 단위로 계산한다 — 시작일이 미래면 (N일 뒤),
     * 오늘 시작했거나 여러 날에 걸쳐 진행 중이면 (오늘)을 붙인다. 이미 끝난 일정은 호출부가
     * (종료됨) 라벨로 분기하므로 여기서는 다루지 않는다.
     */
    private fun formatRelativeTimeLabel(
        entry: AgendaEntry,
        currentTimeMilliseconds: Long,
    ): String {
        if (entry.isAllDay) {
            val daysUntilStart = ChronoUnit.DAYS.between(
                findLocalDateOf(currentTimeMilliseconds),
                findAllDayStartDate(entry),
            )
            if (daysUntilStart > 0) {
                return context.getString(R.string.agenda_relative_days_format, daysUntilStart)
            }
            return context.getString(R.string.today_label)
        }
        val remainingMilliseconds = entry.beginTimeMilliseconds - currentTimeMilliseconds
        return when {
            remainingMilliseconds <= 0 -> context.getString(R.string.agenda_in_progress)
            remainingMilliseconds < TimeUnit.MINUTES.toMillis(1) ->
                context.getString(R.string.agenda_relative_soon)
            remainingMilliseconds < TimeUnit.HOURS.toMillis(1) -> context.getString(
                R.string.agenda_relative_minutes_format,
                TimeUnit.MILLISECONDS.toMinutes(remainingMilliseconds),
            )
            remainingMilliseconds < TimeUnit.DAYS.toMillis(1) -> context.getString(
                R.string.agenda_relative_hours_format,
                TimeUnit.MILLISECONDS.toHours(remainingMilliseconds),
            )
            else -> context.getString(
                R.string.agenda_relative_days_format,
                ChronoUnit.DAYS.between(
                    findLocalDateOf(currentTimeMilliseconds),
                    findLocalDateOf(entry.beginTimeMilliseconds),
                ),
            )
        }
    }

    /**
     * 종일 일정의 마지막 날(하루짜리 포함). 종일 일정의 end는 UTC 자정(마지막 날 다음 날,
     * exclusive)으로 저장되므로(AgendaListBuilder의 QUIRK 참조) 하루를 빼 마지막 날을 구한다.
     */
    private fun findAllDayLastDay(entry: AgendaEntry): LocalDate =
        Instant.ofEpochMilli(entry.endTimeMilliseconds)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .minusDays(1)

    /** 여러 날에 걸친 종일 일정의 마지막 날. 하루짜리 종일 일정이거나 시간 있는 일정이면 null. */
    private fun findAllDayLastDayOrNull(entry: AgendaEntry): LocalDate? {
        if (!entry.isAllDay) return null
        return findAllDayLastDay(entry).takeIf { it.isAfter(findAllDayStartDate(entry)) }
    }

    /**
     * 종일 일정의 시작 날짜. 종일 일정의 begin은 UTC 자정으로 저장되므로(AgendaListBuilder의
     * QUIRK 참조) 시스템 표준 시간대가 아니라 UTC 기준으로 날짜를 읽는다.
     */
    private fun findAllDayStartDate(entry: AgendaEntry): LocalDate =
        Instant.ofEpochMilli(entry.beginTimeMilliseconds)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()

    /** 시스템 표준 시간대 기준으로 [timeMilliseconds]가 속한 날짜. */
    private fun findLocalDateOf(timeMilliseconds: Long): LocalDate =
        Instant.ofEpochMilli(timeMilliseconds)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

    /**
     * 일정이 끝난 것으로 판정되는 시각. 시간 있는 일정은 종료 시각 그대로고, 종일 일정은
     * [findAllDayLastDay]로 마지막 날을 구해 그 날이 시스템 표준 시간대 기준으로 완전히 지난
     * 순간으로 바꿔 계산한다. 종일 종료 시각이 UTC 자정으로 저장되는 탓에 이 보정이 없으면
     * UTC보다 뒤인 지역(한국, UTC+9)에서 어제의 종일 일정이 다음 날 오전 내내 끝나지 않은 것으로 판정된다.
     */
    private fun findEventFinishTimeMilliseconds(entry: AgendaEntry): Long {
        if (!entry.isAllDay) return entry.endTimeMilliseconds
        return findAllDayLastDay(entry).plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val COLLAPSED_ITEM_LIMIT = 3

        // 제목과 상대 시간 라벨 사이 구분자. 라벨 span 시작 인덱스 계산에도 쓰므로 상수로 둔다.
        const val RELATIVE_TIME_LABEL_SEPARATOR = " "

        // 항목의 End(오른쪽) 고정 여백과 알림 맨 위/맨 아래 바깥 여백. 사용자가 조절하지 않는
        // 렌더링 상수로, v1.2.4까지 레이아웃 XML에 있던 값을 옮겨온 것과 같다.
        const val ITEM_HORIZONTAL_INSET_DP = 16
        const val FIRST_ITEM_TOP_PADDING_DP = 8
        const val LAST_ITEM_BOTTOM_PADDING_DP = 4

        // 시각·위치·날짜 헤더가 제목 글자보다 작은 정도. 기존 레이아웃의 15/13sp 관계를 유지한다.
        const val SECONDARY_TEXT_SIZE_OFFSET_SP = 2

        // 행 구분은 requestCode가 아니라 인텐트 data(이벤트 URI)가 담당한다. requestCode는
        // 알림 전체 클릭의 CONTENT_REQUEST_CODE(1002), 해제 복구 브로드캐스트의
        // DISMISS_RESTORE_REQUEST_CODE(1003, AgendaNotificationManager)와 겹치지 않는
        // 고정값이고, 접힘·펼침 뷰가 같은 조합을 요청하면 FLAG_UPDATE_CURRENT로
        // 같은 레코드가 갱신 재사용된다.
        const val EVENT_CLICK_REQUEST_CODE = 1004

        val dayHeaderFormatter = DateTimeFormatter.ofPattern("MM.dd, EEEE", Locale.getDefault())

        // 여러 날 종일 일정의 종료일 표기(~08.30까지)에 쓰는 "MM.dd" 포맷.
        val multidayEndDateFormatter = DateTimeFormatter.ofPattern("MM.dd", Locale.getDefault())

        // 시스템의 12/24시간 설정을 자동으로 따른다.
        val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }
}
