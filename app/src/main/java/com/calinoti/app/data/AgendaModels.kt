package com.calinoti.app.data

/** 알림에 표시할 일정 한 건. 렌더링과 일정 열기에 필요한 최소 정보만 담는다. */
data class AgendaEntry(
    /** 캘린더 프로바이더 Events 테이블의 일정 ID(반복 일정은 시리즈 원본). 알림 줄 클릭으로 일정을 열 때 쓴다. */
    val eventId: Long,
    val title: String,
    val beginTimeMilliseconds: Long,
    val endTimeMilliseconds: Long,
    val isAllDay: Boolean,
    val location: String?,
    /** 이 일정이 속한 캘린더의 표시 색(ARGB). 알림 제목 색으로 쓴다. */
    val calendarColor: Int,
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
