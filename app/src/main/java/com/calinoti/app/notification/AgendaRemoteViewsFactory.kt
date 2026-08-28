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
import com.calinoti.app.data.HiddenItemType
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

    /** 알림이 접힌 상태에서 보일 요약 뷰. 감춤 설정을 적용한 뒤 항목 몇 개만 담는다. */
    fun createCollapsedViews(
        listEntries: List<AgendaListEntry>,
        hiddenItemTypes: Set<HiddenItemType>,
        eventClickTargetPackageName: String,
        spacing: NotificationSpacing,
        notificationTextSizeSp: Int,
        allDayEventTextSizeSp: Int,
        currentTimeMilliseconds: Long,
    ): RemoteViews = createAgendaViews(
        filterHiddenEntries(listEntries, hiddenItemTypes, currentTimeMilliseconds),
        itemLimit = COLLAPSED_ITEM_LIMIT,
        eventRowClickTarget = resolveEventRowClickTarget(eventClickTargetPackageName),
        spacing = spacing,
        notificationTextSizeSp = notificationTextSizeSp,
        allDayEventTextSizeSp = allDayEventTextSizeSp,
        currentTimeMilliseconds = currentTimeMilliseconds,
    )

    /**
     * 설정 화면 미리보기용 뷰. 실제 알림과 같은 조립 경로를 쓰되 행 클릭은 걸지 않는다 —
     * 설정 창에서 PendingIntent를 만들어 두면 쓸모 없는 시스템 등록만 남는다. 탐침이
     * 필요 없는 [EventRowClickTarget.None]을 넘기므로 PackageManager 쿼리도 일어나지
     * 않는다. 항목 수 제한(maxVisibleEntries)과 감춤 규칙도 반영하지 않는다. 표본 목록의
     * 모든 항목을 보여줘야 각 여백 설정이 어느 부분인지 드러나기 때문이다.
     */
    fun createPreviewViews(
        listEntries: List<AgendaListEntry>,
        spacing: NotificationSpacing,
        notificationTextSizeSp: Int,
        allDayEventTextSizeSp: Int,
        currentTimeMilliseconds: Long,
    ): RemoteViews = createAgendaViews(
        listEntries,
        itemLimit = Int.MAX_VALUE,
        eventRowClickTarget = EventRowClickTarget.None,
        spacing = spacing,
        notificationTextSizeSp = notificationTextSizeSp,
        allDayEventTextSizeSp = allDayEventTextSizeSp,
        currentTimeMilliseconds = currentTimeMilliseconds,
    )

    /**
     * 알림을 펼쳤을 때 보일 전체 뷰. [maxVisibleEntries]개까지만 담는다. [hiddenItemTypes]는
     * 펼친 뷰에도 감춤을 적용할 때만 비지 않은 채로 들어온다(AgendaNotificationManager 참조).
     */
    fun createExpandedViews(
        listEntries: List<AgendaListEntry>,
        hiddenItemTypes: Set<HiddenItemType>,
        maxVisibleEntries: Int,
        eventClickTargetPackageName: String,
        spacing: NotificationSpacing,
        notificationTextSizeSp: Int,
        allDayEventTextSizeSp: Int,
        currentTimeMilliseconds: Long,
    ): RemoteViews = createAgendaViews(
        filterHiddenEntries(listEntries, hiddenItemTypes, currentTimeMilliseconds),
        itemLimit = maxVisibleEntries,
        eventRowClickTarget = resolveEventRowClickTarget(eventClickTargetPackageName),
        spacing = spacing,
        notificationTextSizeSp = notificationTextSizeSp,
        allDayEventTextSizeSp = allDayEventTextSizeSp,
        currentTimeMilliseconds = currentTimeMilliseconds,
    )

    /**
     * 감춤 규칙에 걸린 일정을 버리고, 일정이 모두 사라진 날짜 그룹의 헤더도 함께 버린다.
     * 접힌 뷰와 펼친 뷰가 같은 필터를 쓴다. [hiddenItemTypes]가 비면 목록을 그대로 돌려준다 —
     * 감춤을 끈 기본 상태에서는 기존 목록이 그대로 유지돼야 한다.
     */
    private fun filterHiddenEntries(
        listEntries: List<AgendaListEntry>,
        hiddenItemTypes: Set<HiddenItemType>,
        currentTimeMilliseconds: Long,
    ): List<AgendaListEntry> {
        if (hiddenItemTypes.isEmpty()) return listEntries
        val today = findLocalDateOf(currentTimeMilliseconds)
        // AgendaListBuilder가 헤더를 일정 직전에만 추가하므로(첫 항목은 항상 헤더) 헤더 뒤에
        // 일정이 곧바로 오지 않으면 그 그룹은 감춤으로 비어 있는 것이다. 헤더를 잠시 들어뒀다가
        // 일정이 올 때만 내보내면 빈 그룹의 헤더가 자연히 사라진다.
        return buildList {
            var pendingDayHeader: AgendaListEntry.DayHeader? = null
            for (listEntry in listEntries) {
                when (listEntry) {
                    is AgendaListEntry.DayHeader -> pendingDayHeader = listEntry
                    is AgendaListEntry.Event -> {
                        val hiddenItemType =
                            findHiddenItemTypeOf(listEntry.entry, today, currentTimeMilliseconds)
                        if (hiddenItemType in hiddenItemTypes) continue
                        pendingDayHeader?.let { dayHeader -> add(dayHeader) }
                        pendingDayHeader = null
                        add(listEntry)
                    }
                }
            }
        }
    }

    /**
     * 이 일정의 감춤 규칙 상태. 시간 있는 일정은 감춤 대상이 아니므로 null이다. 분기 순서가
     * 곧 분류기다 — 오늘 시작 → 예정 → 종료 → 진행 중의 네 가지가 서로 겹치지 않고 모든
     * 종일 일정을 정확히 하나로 분류한다(HiddenItemType의 완전분할).
     */
    private fun findHiddenItemTypeOf(
        entry: AgendaEntry,
        today: LocalDate,
        currentTimeMilliseconds: Long,
    ): HiddenItemType? {
        if (!entry.isAllDay) return null
        // 종일 일정은 시작이 UTC 자정으로 저장된다(calendar-provider-allday-utc QUIRK 참조) —
        // 날짜 판정도 findAllDayStartDate처럼 UTC 기준으로 해야 한다. 오늘 비교는 날짜 헤더의
        // (오늘) 표시와 같은 기준(findLocalDateOf)이라 표시하기로 한 종일 일정은 항상
        // (오늘) 헤더 아래에 놓인다. 시작 시각을 시스템 표준 시간대로 해석하면 UTC보다 뒤인
        // 지역(한국, UTC+9)에서 헤더 표시와 감춤 판정이 어긋난다.
        val startDate = findAllDayStartDate(entry)
        return when {
            startDate == today -> HiddenItemType.ALL_DAY_STARTED_TODAY
            startDate.isAfter(today) -> HiddenItemType.ALL_DAY_UPCOMING
            // 종료 판정은 제목의 (종료됨) 취소선과 같은 출처(AgendaEntry.finishTimeMilliseconds)를
            // 쓴다 — UTC 자정 종료 보정이 포함돼 있어 한국에서 어제 종일 일정이 오전 내내
            // 진행 중으로 분류되는 일이 없다.
            entry.finishTimeMilliseconds <= currentTimeMilliseconds ->
                HiddenItemType.ALL_DAY_FINISHED
            else -> HiddenItemType.ALL_DAY_IN_PROGRESS
        }
    }

    private fun createAgendaViews(
        listEntries: List<AgendaListEntry>,
        itemLimit: Int,
        /**
         * 일정 행 클릭 대상. [EventRowClickTarget.None]이면 행 클릭 없이 조립한다(설정
         * 화면 미리보기). 탐침(PackageManager 쿼리)은 행마다가 아니라 뷰 조립당 한 번만
         * 하므로 호출부가 넘긴다.
         */
        eventRowClickTarget: EventRowClickTarget,
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
                        eventRowClickTarget,
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
        eventRowClickTarget: EventRowClickTarget,
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
        // 클릭은 시간·제목·위치 텍스트에만 건다. 텍스트가 없는 행의 오른쪽 여백은
        // 클릭이 없어 알림 전체 contentIntent(알림 클릭 동작)로 넘어간다. 행 클릭 대상이
        // 없는(None) 기기에서는 걸지 않아 텍스트까지 알림 전체가 contentIntent다.
        when (eventRowClickTarget) {
            EventRowClickTarget.None -> Unit
            EventRowClickTarget.SelfApp -> {
                val openSelfAppPendingIntent = createOpenSelfAppPendingIntent()
                itemViews.setOnClickPendingIntent(R.id.event_time_text, openSelfAppPendingIntent)
                itemViews.setOnClickPendingIntent(R.id.event_title_text, openSelfAppPendingIntent)
                itemViews.setOnClickPendingIntent(R.id.event_location_text, openSelfAppPendingIntent)
            }
            is EventRowClickTarget.CalendarEventDetail -> {
                val openEventPendingIntent =
                    createOpenEventPendingIntent(entry.eventId, eventRowClickTarget.packageName)
                itemViews.setOnClickPendingIntent(R.id.event_time_text, openEventPendingIntent)
                itemViews.setOnClickPendingIntent(R.id.event_title_text, openEventPendingIntent)
                itemViews.setOnClickPendingIntent(R.id.event_location_text, openEventPendingIntent)
            }
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

    /** 일정 행 클릭 대상. 행을 눌렀을 때 무엇을 여는지 세 갈래로 나뉜다. */
    private sealed interface EventRowClickTarget {

        /** 처리 앱이 없어 행 클릭을 걸지 않는다 — 클릭은 알림 전체 contentIntent가 받는다. */
        data object None : EventRowClickTarget

        /** Calinoti 자신을 연다. 일정 상세 화면이 없어 이 값이 지정되면 행 클릭도 앱을 연다. */
        data object SelfApp : EventRowClickTarget

        /** 캘린더 앱의 일정 상세. [packageName]이 null이면 암시적 인텐트로 시스템이 고른다. */
        data class CalendarEventDetail(val packageName: String?) : EventRowClickTarget
    }

    /**
     * 일정 클릭 동작 설정값을 행 클릭 대상으로 바꾼다. Calinoti 자신이 지정되면 SelfApp으로
     * 연다(자기 런처 인텐트는 항상 존재해 탐침이 없다). 캘린더 앱이 지정되면 그 앱이 일정
     * 상세를 열 수 있는지 탐친다. 지정이 없거나 이미 지워졌으면 암시적 인텐트로 돌아가고,
     * 어느 쪽도 처리 앱이 없으면 행 클릭을 걸지 않아 알림 전체 contentIntent가 행을 포함해
     * 동작하게 둔다.
     */
    private fun resolveEventRowClickTarget(
        eventClickTargetPackageName: String,
    ): EventRowClickTarget {
        if (eventClickTargetPackageName == context.packageName) {
            return EventRowClickTarget.SelfApp
        }
        if (eventClickTargetPackageName != UserPreferences.UNSPECIFIED_CLICK_TARGET_PACKAGE_NAME) {
            val selectedAppIntent =
                CalendarIntents.buildEventViewIntent(CalendarIntents.EVENT_PROBE_ID)
                    .setPackage(eventClickTargetPackageName)
            if (hasResolvingActivity(selectedAppIntent)) {
                return EventRowClickTarget.CalendarEventDetail(
                    packageName = eventClickTargetPackageName,
                )
            }
        }
        val implicitIntent = CalendarIntents.buildEventViewIntent(CalendarIntents.EVENT_PROBE_ID)
        return if (hasResolvingActivity(implicitIntent)) {
            EventRowClickTarget.CalendarEventDetail(packageName = null)
        } else {
            EventRowClickTarget.None
        }
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
     * 행 클릭에 Calinoti 자신을 여는 PendingIntent. 대상이 자기 패키지라 런처 인텐트는
     * 항상 존재해 null 검사가 없다. 일정별 인텐트가 아니므로 모든 행이 하나의 레코드를
     * 갱신 재사용한다.
     */
    private fun createOpenSelfAppPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            EVENT_CLICK_REQUEST_CODE,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
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
        val isEventFinished = entry.finishTimeMilliseconds <= currentTimeMilliseconds
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
