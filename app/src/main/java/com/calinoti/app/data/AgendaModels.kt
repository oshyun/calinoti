package com.calinoti.app.data

/** 알림에 표시할 일정 한 건. 렌더링에 필요한 최소 정보만 담는다. */
data class AgendaEntry(
    val title: String,
    val beginTimeMilliseconds: Long,
    val endTimeMilliseconds: Long,
    val isAllDay: Boolean,
    val location: String?,
)

/** 아젠다 목록의 한 줄: 날짜 그룹 헤더 또는 일정 항목. */
sealed interface AgendaListEntry {
    data class DayHeader(val dayStartMilliseconds: Long) : AgendaListEntry
    data class Event(val entry: AgendaEntry) : AgendaListEntry
}

/** 설정 화면에 보여줄 기기의 캘린더 한 개. */
data class UserCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
)
