package com.calinoti.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 일정 목록에 날짜 그룹 헤더를 끼워 넣어 아젠다 목록을 만든다. */
object AgendaListBuilder {

    /** 시작 시각 순으로 정렬된 일정 목록을 날짜별로 묶어 헤더가 섞인 목록으로 만든다. */
    fun buildDayGroupedEntries(entries: List<AgendaEntry>): List<AgendaListEntry> {
        val zoneId = ZoneId.systemDefault()
        val listEntries = mutableListOf<AgendaListEntry>()
        var currentDayIndex = Long.MIN_VALUE
        for (entry in entries) {
            val dayIndex =
                Instant.ofEpochMilli(entry.beginTimeMilliseconds).atZone(zoneId).toLocalDate().toEpochDay()
            if (dayIndex != currentDayIndex) {
                listEntries.add(
                    AgendaListEntry.DayHeader(
                        dayStartMilliseconds = LocalDate.ofEpochDay(dayIndex)
                            .atStartOfDay(zoneId)
                            .toInstant()
                            .toEpochMilli(),
                    ),
                )
                currentDayIndex = dayIndex
            }
            listEntries.add(AgendaListEntry.Event(entry))
        }
        return listEntries
    }
}
