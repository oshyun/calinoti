package com.calinoti.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** 알림에 표시할 일정 한 건. 렌더링과 일정 열기에 필요한 최소 정보만 담는다. */
data class EventEntry(
    /** 캘린더 프로바이더 Events 테이블의 일정 ID(반복 일정은 시리즈 원본). 알림 줄 클릭으로 일정을 열 때 쓴다. */
    val eventId: Long,
    val title: String,
    val beginTimeMilliseconds: Long,
    val endTimeMilliseconds: Long,
    val isAllDay: Boolean,
    val location: String?,
    /** 이 일정이 속한 캘린더의 표시 색(ARGB). 알림 제목 색으로 쓴다. */
    val calendarColor: Int,
    /** 이 일정이 속한 캘린더의 표시 이름. 키워드 감춤 규칙의 캘린더명 매칭에 쓴다. */
    val calendarDisplayName: String,
) {
    /**
     * 일정이 끝난 것으로 판정되는 시각의 단일 출처. 시간 있는 일정은 종료 시각 그대로다.
     * 종일 일정의 end는 UTC 자정(마지막 날 다음 날)으로 저장되므로(calendar-provider-allday-utc
     * QUIRK) 그대로 쓰면 UTC보다 뒤인 지역(한국, UTC+9)에서 어제 종일 일정이 다음 날 오전까지
     * 안 끝난 것으로 판정된다 — end의 UTC 날짜를 현지 자정으로 옮겨 마지막 날이 끝나는
     * 현지 시각으로 보정한다. 표시 창 필터(CalendarReader)와 제목 (종료됨) 표시·감춤
     * 분류(NotificationViewsFactory)가 이 값을 쓴다.
     */
    val finishTimeMilliseconds: Long
        get() {
            if (!isAllDay) return endTimeMilliseconds
            return Instant.ofEpochMilli(endTimeMilliseconds)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

    /**
     * 종일 일정의 기간(일 수, 시작일~마지막날). 하루짜리면 1, 이틀에 걸치면 2다.
     * 종일 일정이 아니면 0이다. 종일 일정의 begin과 end는 UTC 자정으로 저장되므로
     * (calendar-provider-allday-utc QUIRK) UTC 기준으로 날짜 차이를 계산한다.
     */
    val allDayDurationDays: Int
        get() {
            if (!isAllDay) return 0
            val startDate = Instant.ofEpochMilli(beginTimeMilliseconds)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            val endDateExclusive = Instant.ofEpochMilli(endTimeMilliseconds)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            val durationDays = ChronoUnit.DAYS.between(startDate, endDateExclusive)
            return durationDays.coerceAtLeast(1).toInt()
        }
}

/** 일정 목록의 한 줄: 날짜 그룹 헤더 또는 일정 항목. */
sealed interface EventListEntry {
    data class DayHeader(val dayStartMilliseconds: Long) : EventListEntry
    data class Event(val entry: EventEntry) : EventListEntry
}

/** 설정 화면에 보여줄 기기의 캘린더 한 개. */
data class UserCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
)
