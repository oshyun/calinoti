package com.calinoti.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** 일정 목록에 날짜 그룹 헤더를 끼워 넣어 일정 목록을 만든다. */
object EventListBuilder {

    /** 시작 시각 순으로 정렬된 일정 목록을 날짜별로 묶어 헤더가 섞인 목록으로 만든다. */
    fun buildDayGroupedEntries(entries: List<EventEntry>): List<EventListEntry> {
        val zoneId = ZoneId.systemDefault()
        val listEntries = mutableListOf<EventListEntry>()
        var currentDayIndex = Long.MIN_VALUE
        for (entry in entries) {
            // QUIRK(calendar-provider-allday-utc): CalendarContract는 종일 일정의 시작을 UTC 자정으로
            //   저장한다. 시스템 표준 시간대로 바꾸면 UTC보다 빠른 지역에서 전날 날짜로 묶이므로
            //   종일 일정만 UTC 기준으로 로컬 날짜를 구한다.
            // QUIRK-REMOVE-WHEN: CalendarContract가 종일 시작 시각을 현지 자정으로 반환하도록
            //   스펙이 바뀌는 일은 사실상 없으므로 사실상 영구 보정이다.
            val entryZoneId = if (entry.isAllDay) ZoneOffset.UTC else zoneId
            val dayIndex =
                Instant.ofEpochMilli(entry.beginTimeMilliseconds).atZone(entryZoneId).toLocalDate().toEpochDay()
            if (dayIndex != currentDayIndex) {
                listEntries.add(
                    EventListEntry.DayHeader(
                        dayStartMilliseconds = LocalDate.ofEpochDay(dayIndex)
                            .atStartOfDay(zoneId)
                            .toInstant()
                            .toEpochMilli(),
                    ),
                )
                currentDayIndex = dayIndex
            }
            listEntries.add(EventListEntry.Event(entry))
        }
        return listEntries
    }
}
