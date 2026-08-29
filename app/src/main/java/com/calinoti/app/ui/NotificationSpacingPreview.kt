package com.calinoti.app.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.calinoti.app.R
import com.calinoti.app.data.EventEntry
import com.calinoti.app.data.EventListEntry
import com.calinoti.app.data.NotificationSpacing
import com.calinoti.app.notification.NotificationViewsFactory
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * 알림 여백 설정 섹션에 보여줄 미리보기. 실제 알림을 만드는 같은 조립 경로
 * ([NotificationViewsFactory.createPreviewViews])가 그린 뷰를 그대로 인플레이트하므로,
 * 여기서 보이는 배열이 곧 알림의 배열이다. 각 슬라이더 값을 바꾸면 저장과 무관하게
 * [spacing]에 즉시 반영된다.
 */
@Composable
internal fun NotificationSpacingPreview(
    spacing: NotificationSpacing,
    notificationTextSizeSp: Int,
    allDayEventTextSizeSp: Int,
    dayHeaderFormatPattern: String,
    remoteViewsFactory: NotificationViewsFactory,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 화면을 띄워 둔 사이 시각이 흘러도 상대 시간 라벨((2시간 뒤) 등)이 갑자기 바뀌지 않게
    // 컴포지션 시점에 고정한다.
    val previewTimeMilliseconds = remember { System.currentTimeMillis() }
    val sampleEntries = remember(previewTimeMilliseconds) {
        createPreviewSampleEntries(context, previewTimeMilliseconds)
    }
    // NotificationSpacing이 data class라 값이 같으면 재조립되지 않는다 — 슬라이더가 바뀐
    // 값 하나만 넘어와도 나머지 다섯 값이 같으면 같은 인스턴스로 재사용된다.
    val previewViews = remember(
        sampleEntries,
        spacing,
        notificationTextSizeSp,
        allDayEventTextSizeSp,
        dayHeaderFormatPattern,
    ) {
        remoteViewsFactory.createPreviewViews(
            listEntries = sampleEntries,
            spacing = spacing,
            notificationTextSizeSp = notificationTextSizeSp,
            allDayEventTextSizeSp = allDayEventTextSizeSp,
            dayHeaderFormatPattern = dayHeaderFormatPattern,
            currentTimeMilliseconds = previewTimeMilliseconds,
        )
    }
    // 실제 알림은 시스템이 카드 배경을 그린다. 여기서는 그 근사치를 입히는데, 색은 알림
    // 텍스트 색처럼 시스템 night 마스크를 따른다 — 앱 화면은 항상 라이트 스킴(Theme.kt)이라
    // colorScheme을 쓰면 다크 모드에서 흰 글씨가 흰 배경에 묻힌다.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.notification_preview_card_background)),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = {
                // 빈 루트 레이아웃을 딱 한 번 인플레이트한다. 이후 값 변화는 update의
                // reapply가 같은 뷰 트리에 액션만 다시 적용해 처리한다(재인플레이트 없음).
                val inflatedRoot = RemoteViews(context.packageName, R.layout.notification_list)
                    .apply(context, null)
                // apply에 parent를 넘기지 않으면 레이아웃 XML의 폭·높이가 LayoutParams로
                // 만들어지지 않아 뷰가 내용 폭만 차지한다 — 화면 폭을 채우게 직접 지정한다.
                inflatedRoot.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                inflatedRoot
            },
            update = { inflatedRoot ->
                // 재구성이 겹쳐도 같은 RemoteViews를 다시 적용하지 않게 마지막 적용분을
                // tag에 기록해 가른다(재적용 자체는 멱등하지만 액션 적용 비용이 든다).
                if (inflatedRoot.tag !== previewViews) {
                    previewViews.reapply(context, inflatedRoot)
                    inflatedRoot.tag = previewViews
                }
            },
        )
    }
}

/**
 * 여백 다섯 종이 모두 드러나는 표본 일정 목록: 날짜 헤더 세 개(오늘·내일·모레) + 일정 네 개.
 * 시각은 [currentTimeMilliseconds] 기준으로 만들어 상대 시간 라벨도 실제와 같이 붙는다.
 *
 * 행 조합이 담당하는 설정:
 * - 날짜 항목 앞 여백 → 세 그룹 행 전부
 * - 날짜와 일정 사이 여백 → 각 날짜 열과 일정 열 사이 수평 간격
 * - 시간과 제목 사이 여백 → 시간 있는 일정의 시각-제목 간격(종일 일정은 시간 뷰가 숨는다)
 * - 일정 사이 여백 → 오늘의 두 일정 사이 간격
 * - 날짜 사이 여백 → 그룹 사이 간격
 */
private fun createPreviewSampleEntries(
    context: Context,
    currentTimeMilliseconds: Long,
): List<EventListEntry> {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(currentTimeMilliseconds).atZone(zone).toLocalDate()
    val tomorrow = today.plusDays(1)
    val dayAfterTomorrow = today.plusDays(2)
    // 자정 직전 실행이면 now+2h·now+5h가 다음 날로 넘어가 오늘 헤더 아래에 놓이지 않게
    // 되므로, 오늘 일정 시각은 오늘 밤을 넘지 않게 자른다.
    val todayEveningMilliseconds = today.atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
    fun limitToTodayEvening(timeMilliseconds: Long): Long =
        minOf(timeMilliseconds, todayEveningMilliseconds)
    return listOf(
        EventListEntry.DayHeader(
            dayStartMilliseconds = today.atStartOfDay(zone).toInstant().toEpochMilli(),
        ),
        EventListEntry.Event(
            EventEntry(
                eventId = 1L,
                title = context.getString(R.string.preview_sample_title_meeting),
                beginTimeMilliseconds = limitToTodayEvening(
                    currentTimeMilliseconds + TimeUnit.HOURS.toMillis(2),
                ),
                endTimeMilliseconds = limitToTodayEvening(
                    currentTimeMilliseconds + TimeUnit.HOURS.toMillis(3),
                ),
                isAllDay = false,
                location = context.getString(R.string.preview_sample_location_meeting),
                calendarColor = PREVIEW_CALENDAR_COLOR_BLUE,
            ),
        ),
        EventListEntry.Event(
            EventEntry(
                eventId = 2L,
                title = context.getString(R.string.preview_sample_title_walk),
                // 1시간 미만 구간 표본 — 실제 알림처럼 실시간 카운트다운 행이 보인다.
                beginTimeMilliseconds = limitToTodayEvening(
                    currentTimeMilliseconds + TimeUnit.MINUTES.toMillis(30),
                ),
                endTimeMilliseconds = limitToTodayEvening(
                    currentTimeMilliseconds + TimeUnit.MINUTES.toMillis(90),
                ),
                isAllDay = false,
                location = null,
                calendarColor = PREVIEW_CALENDAR_COLOR_GREEN,
            ),
        ),
        EventListEntry.DayHeader(
            dayStartMilliseconds = tomorrow.atStartOfDay(zone).toInstant().toEpochMilli(),
        ),
        EventListEntry.Event(
            EventEntry(
                eventId = 3L,
                title = context.getString(R.string.preview_sample_title_birthday),
                // 종일 일정의 경계는 UTC 자정이다 — 실제 데이터와 같은 규칙
                // (EventListBuilder의 calendar-provider-allday-utc QUIRK 참조)을 따른다.
                beginTimeMilliseconds =
                    tomorrow.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                endTimeMilliseconds =
                    tomorrow.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                isAllDay = true,
                location = null,
                calendarColor = PREVIEW_CALENDAR_COLOR_PURPLE,
            ),
        ),
        EventListEntry.DayHeader(
            dayStartMilliseconds = dayAfterTomorrow.atStartOfDay(zone).toInstant().toEpochMilli(),
        ),
        EventListEntry.Event(
            EventEntry(
                eventId = 4L,
                title = context.getString(R.string.preview_sample_title_movie),
                beginTimeMilliseconds =
                    dayAfterTomorrow.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                endTimeMilliseconds =
                    dayAfterTomorrow.atTime(11, 0).atZone(zone).toInstant().toEpochMilli(),
                isAllDay = false,
                location = context.getString(R.string.preview_sample_location_movie),
                calendarColor = PREVIEW_CALENDAR_COLOR_BLUE,
            ),
        ),
    )
}

// 표본 일정에 입힐 캘린더 색. 실제 알림과 같은 표준 톤 보정(CalendarColorTone)을 거치므로
// 값 자체는 대표색이면 충분하다 — 서로 다른 캘린더가 섞인 모습만 보여주면 된다.
private val PREVIEW_CALENDAR_COLOR_BLUE = 0xFF4285F4.toInt()
private val PREVIEW_CALENDAR_COLOR_GREEN = 0xFF34A853.toInt()
private val PREVIEW_CALENDAR_COLOR_PURPLE = 0xFF9C27B0.toInt()
