package com.calinoti.app.data

import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** 일정 목록에 날짜 그룹 헤더를 끼워 넣어 아젠다 목록을 만든다. */
object AgendaListBuilder {

    /** 시작 시각 순으로 정렬된 일정 목록을 날짜별로 묶어 헤더가 섞인 목록으로 만든다. */
    fun buildDayGroupedEntries(entries: List<AgendaEntry>): List<AgendaListEntry> {
        val listEntries = mutableListOf<AgendaListEntry>()
        var currentDayIndex = Long.MIN_VALUE
        for (entry in entries) {
            val dayIndex = epochDay(entry.beginTimeMilliseconds)
            if (dayIndex != currentDayIndex) {
                listEntries.add(AgendaListEntry.DayHeader(dayStartMilliseconds = localMidnightOf(dayIndex)))
                currentDayIndex = dayIndex
            }
            listEntries.add(AgendaListEntry.Event(entry))
        }
        return listEntries
    }

    /** 일정 시작 시각이 속한 날짜(현지 시간대 기준)의 epoch day. */
    private fun epochDay(timeMilliseconds: Long): Long =
        TimeUnit.MILLISECONDS.toDays(
            timeMilliseconds + TimeZone.getDefault().getOffset(timeMilliseconds),
        )

    /** epoch day가 가리키는 날짜의 현지 자정 밀리초. 헤더의 날짜 표시에 쓴다. */
    private fun localMidnightOf(dayIndex: Long): Long =
        dayIndex * MILLIS_PER_DAY - TimeZone.getDefault().getOffset(dayIndex * MILLIS_PER_DAY)

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
}
