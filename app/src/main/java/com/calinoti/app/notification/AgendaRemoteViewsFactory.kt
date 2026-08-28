package com.calinoti.app.notification

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.provider.CalendarContract
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.calinoti.app.R
import com.calinoti.app.data.AgendaEntry
import com.calinoti.app.data.AgendaListEntry
import com.calinoti.app.data.NotificationSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** 아젠다 데이터를 알림용 RemoteViews 레이아웃으로 조립한다. */
class AgendaRemoteViewsFactory(private val context: Context) {

    /** 알림이 접힌 상태에서 보일 요약 뷰. 항목 몇 개만 담는다. */
    fun createCollapsedViews(
        listEntries: List<AgendaListEntry>,
        spacing: NotificationSpacing,
        notificationTextSizeSp: Int,
        currentTimeMilliseconds: Long,
    ): RemoteViews = createAgendaViews(
        listEntries,
        itemLimit = COLLAPSED_ITEM_LIMIT,
        spacing = spacing,
        notificationTextSizeSp = notificationTextSizeSp,
        currentTimeMilliseconds = currentTimeMilliseconds,
    )

    /** 알림을 펼쳤을 때 보일 전체 뷰. [maxVisibleEntries]개까지만 담는다. */
    fun createExpandedViews(
        listEntries: List<AgendaListEntry>,
        maxVisibleEntries: Int,
        spacing: NotificationSpacing,
        notificationTextSizeSp: Int,
        currentTimeMilliseconds: Long,
    ): RemoteViews = createAgendaViews(
        listEntries,
        itemLimit = maxVisibleEntries,
        spacing = spacing,
        notificationTextSizeSp = notificationTextSizeSp,
        currentTimeMilliseconds = currentTimeMilliseconds,
    )

    private fun createAgendaViews(
        listEntries: List<AgendaListEntry>,
        itemLimit: Int,
        spacing: NotificationSpacing,
        notificationTextSizeSp: Int,
        currentTimeMilliseconds: Long,
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
        canOpenEventRows: Boolean,
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
        itemViews.setTextViewText(
            R.id.event_title_text,
            createEventTitleText(entry, currentTimeMilliseconds, secondaryTextSizeSp),
        )
        itemViews.setTextViewTextSize(R.id.event_title_text, COMPLEX_UNIT_SP, titleTextSizeSp)
        // 제목 색은 캘린더 앱과 같은 캘린더 색으로 표시한다. 일정별 개별 색(EVENT_COLOR)은
        // 무시하고 캘린더 색만 따른다. 원본 색이 카드 배경에 묻히는 밝기면 명도만 보정한다.
        itemViews.setTextColor(
            R.id.event_title_text,
            clampLightnessForCardBackground(
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
     * 알림 카드는 시스템이 그리므로 카드 밝기는 시스템 다크 테마를 따른다. 앱은 uiMode를
     * 강제하지 않아(화면 테마가 라이트로 고정돼 있어도 Configuration은 시스템 값을 그대로
     * 반영한다) 앱 프로세스의 night mask가 곧 카드 테마다.
     */
    private fun isSystemDarkTheme(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * 캘린더 색을 알림 카드 배경에서 읽을 수 있게 명도(HSL L)만 조정한다. 색조·채도는
     * 유지해 캘린더 색 사이의 구분이 그대로 남는다. 캘린더 앱이 저장한 색은 대개 라이트
     * 배경 기준이라 진한 색은 다크 카드에, 연한 색은 라이트 카드에 묻힌다.
     * 카드 자체는 앱이 그리지 않아 정확한 배경색을 알 수 없고 테마로 방향만 잡는다.
     */
    private fun clampLightnessForCardBackground(color: Int, isDarkTheme: Boolean): Int {
        val (hue, saturation, lightness) = rgbToHsl(color)
        val clampedLightness = when {
            isDarkTheme && lightness < DARK_CARD_MIN_LIGHTNESS -> DARK_CARD_MIN_LIGHTNESS
            !isDarkTheme && lightness > LIGHT_CARD_MAX_LIGHTNESS -> LIGHT_CARD_MAX_LIGHTNESS
            else -> lightness
        }
        if (clampedLightness == lightness) return color
        return hslToRgb(hue, saturation, clampedLightness, alpha = Color.alpha(color))
    }

    /** 색을 0..1 범위의 색상·채도·명도로 분해한다. 명도만 떼어 조정하기 위한 변환이다. */
    private fun rgbToHsl(color: Int): Triple<Float, Float, Float> {
        val red = Color.red(color) / 255f
        val green = Color.green(color) / 255f
        val blue = Color.blue(color) / 255f
        val maxChannel = maxOf(red, green, blue)
        val minChannel = minOf(red, green, blue)
        val lightness = (maxChannel + minChannel) / 2f
        if (maxChannel == minChannel) return Triple(0f, 0f, lightness)
        val channelRange = maxChannel - minChannel
        val saturation = if (lightness > 0.5f) {
            channelRange / (2f - maxChannel - minChannel)
        } else {
            channelRange / (maxChannel + minChannel)
        }
        val huePortion = when (maxChannel) {
            red -> (green - blue) / channelRange + if (green < blue) 6f else 0f
            green -> (blue - red) / channelRange + 2f
            else -> (red - green) / channelRange + 4f
        }
        return Triple(huePortion / 6f, saturation, lightness)
    }

    /** 0..1 범위의 색상·채도·명도를 알파를 유지한 색으로 되돌린다. */
    private fun hslToRgb(hue: Float, saturation: Float, lightness: Float, alpha: Int): Int {
        if (saturation == 0f) {
            val grayChannel = (lightness * 255f).roundToInt()
            return Color.argb(alpha, grayChannel, grayChannel, grayChannel)
        }
        // 결과 채널 값의 상한·하한. 표준 HSL 변환식의 q·p에 해당한다.
        val resultMaxChannel =
            if (lightness < 0.5f) lightness * (1f + saturation)
            else lightness + saturation - lightness * saturation
        val resultMinChannel = 2f * lightness - resultMaxChannel

        fun channelFromHue(huePortion: Float): Float {
            val wrappedHuePortion = when {
                huePortion < 0f -> huePortion + 1f
                huePortion > 1f -> huePortion - 1f
                else -> huePortion
            }
            return when {
                wrappedHuePortion < 1f / 6f ->
                    resultMinChannel + (resultMaxChannel - resultMinChannel) * 6f * wrappedHuePortion
                wrappedHuePortion < 1f / 2f -> resultMaxChannel
                wrappedHuePortion < 2f / 3f ->
                    resultMinChannel +
                        (resultMaxChannel - resultMinChannel) * (2f / 3f - wrappedHuePortion) * 6f
                else -> resultMinChannel
            }
        }
        return Color.argb(
            alpha,
            (channelFromHue(hue + 1f / 3f) * 255f).roundToInt(),
            (channelFromHue(hue) * 255f).roundToInt(),
            (channelFromHue(hue - 1f / 3f) * 255f).roundToInt(),
        )
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

    /**
     * 일정 줄의 제목 텍스트. 여러 날에 걸친 종일 일정이면 제목에 종료일을 붙이고,
     * 종일 일정이 아니면 제목 뒤에 상대 시간 라벨을 회색 작은 글씨로 이어 붙인다.
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
        val relativeTimeLabel = formatRelativeTimeLabel(entry, currentTimeMilliseconds)
            ?: return titleText
        val labelTextStartIndex = titleText.length + RELATIVE_TIME_LABEL_SEPARATOR.length
        val titleWithLabel = SpannableStringBuilder(titleText)
            .append(RELATIVE_TIME_LABEL_SEPARATOR)
            .append(relativeTimeLabel)
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

    /** 제목 뒤에 붙일 상대 시간 라벨. 종일 일정과 이미 끝난 일정은 표시하지 않는다(null). */
    private fun formatRelativeTimeLabel(
        entry: AgendaEntry,
        currentTimeMilliseconds: Long,
    ): String? {
        if (entry.isAllDay) return null
        // 표시 창이 과거를 포함하면 이미 끝난 일정이 목록에 남으므로 종료 시각으로 걸러낸다.
        if (entry.endTimeMilliseconds <= currentTimeMilliseconds) return null
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
                TimeUnit.MILLISECONDS.toDays(remainingMilliseconds),
            )
        }
    }

    /** 여러 날에 걸친 종일 일정의 마지막 날. 하루짜리 종일 일정이거나 시간 있는 일정이면 null. */
    private fun findAllDayLastDayOrNull(entry: AgendaEntry): LocalDate? {
        if (!entry.isAllDay) return null
        // 종일 일정의 begin/end는 UTC로 저장된다(AgendaListBuilder의 QUIRK 참조).
        // end는 마지막 날 다음 날 자정(exclusive)이므로 하루를 빼 마지막 날을 구한다.
        val firstDay = Instant.ofEpochMilli(entry.beginTimeMilliseconds)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        val lastDay = Instant.ofEpochMilli(entry.endTimeMilliseconds)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .minusDays(1)
        return lastDay.takeIf { it.isAfter(firstDay) }
    }

    private companion object {
        const val COLLAPSED_ITEM_LIMIT = 3

        // 알림 카드 위에서 제목 색이 묻히지 않는 HSL 명도 경계. One UI는 커스텀 뷰 카드를
        // 표준 알림보다 연하게 그려(다크에서도 밝은 회색 계열) 다크 최소 명도에 여유를 둔다.
        const val DARK_CARD_MIN_LIGHTNESS = 0.55f
        const val LIGHT_CARD_MAX_LIGHTNESS = 0.65f

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

        // 일정 열기 capability 확인용 탐침 id. 실제 일정 id가 아니라 인텐트 shape만 맞으면 충분하다.
        const val PROBE_EVENT_ID = 0L

        val dayHeaderFormatter = DateTimeFormatter.ofPattern("MM.dd, EEEE", Locale.getDefault())

        // 여러 날 종일 일정의 종료일 표기(~08.30까지)에 쓰는 "MM.dd" 포맷.
        val multidayEndDateFormatter = DateTimeFormatter.ofPattern("MM.dd", Locale.getDefault())

        // 시스템의 12/24시간 설정을 자동으로 따른다.
        val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }
}
