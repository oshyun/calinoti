package com.calinoti.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

/** 시스템 캘린더 프로바이더에서 캘린더 목록과 표시 창에 든 일정을 읽어온다. */
class CalendarReader(private val context: Context) {

    fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** 설정 화면의 캘린더 선택 목록에 쓸 캘린더들을 이름 순으로 읽어온다. */
    fun loadCalendars(): List<UserCalendar> {
        if (!hasCalendarPermission()) return emptyList()
        val calendars = mutableListOf<UserCalendar>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
            ),
            /* selection = */ null,
            /* selectionArgs = */ null,
            /* sortOrder = */
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idColumnIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val displayNameColumnIndex =
                cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountNameColumnIndex =
                cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val colorColumnIndex =
                cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
            while (cursor.moveToNext()) {
                calendars.add(
                    UserCalendar(
                        id = cursor.getLong(idColumnIndex),
                        displayName = cursor.getString(displayNameColumnIndex).orEmpty(),
                        accountName = cursor.getString(accountNameColumnIndex).orEmpty(),
                        color = cursor.getInt(colorColumnIndex),
                    ),
                )
            }
        }
        return calendars
    }

    /**
     * 오늘 기준 [windowStartDays]일 뒤(음수면 이전)부터 [windowEndDays]일 뒤까지의 표시 창과
     * 겹치는 일정을 시작 시각 순으로 읽어온다. 예: -3 ~ 7이면 3일 전부터 7일 뒤까지 전부.
     * 범위 쿼리 특성상 창 시작 전에 완전히 끝난 인스턴스도 섞여 나올 수 있어 종료 시각으로 걸러낸다.
     * [selectedCalendarIds]가 null이면 모든 캘린더를, 빈 집합이면 아무 캘린더도 대상으로 삼지 않는다.
     */
    fun loadAgendaEntries(
        selectedCalendarIds: Set<Long>?,
        windowStartDays: Int,
        windowEndDays: Int,
        currentTimeMilliseconds: Long,
    ): List<AgendaEntry> {
        if (!hasCalendarPermission()) return emptyList()

        // 선택된 캘린더가 하나도 없으면 IN () 절을 만들 수 없으므로 여기서 끝낸다.
        if (selectedCalendarIds != null && selectedCalendarIds.isEmpty()) return emptyList()

        // Instances 범위 쿼리: 범위와 겹치는 모든 일정 인스턴스(반복 일정 전개 포함)를 반환한다.
        val searchStartMilliseconds =
            currentTimeMilliseconds + TimeUnit.DAYS.toMillis(windowStartDays.toLong())
        val searchEndMilliseconds =
            currentTimeMilliseconds + TimeUnit.DAYS.toMillis(windowEndDays.toLong())
        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(searchStartMilliseconds.toString())
            .appendPath(searchEndMilliseconds.toString())
            .build()

        // 취소된 일정은 아젠다에서 제외하고, 선택된 캘린더로 한정한다.
        val selectionFilters = mutableListOf(
            CalendarContract.Instances.STATUS + " != ?",
        )
        val selectionValues = mutableListOf(CalendarContract.Instances.STATUS_CANCELED.toString())
        if (selectedCalendarIds != null) {
            selectionFilters.add(
                CalendarContract.Instances.CALENDAR_ID + " IN (" +
                    selectedCalendarIds.joinToString(",") { "?" } + ")",
            )
            selectionValues.addAll(selectedCalendarIds.map(Long::toString))
        }

        val entries = mutableListOf<AgendaEntry>()
        context.contentResolver.query(
            instancesUri,
            arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
            ),
            /* selection = */ selectionFilters.joinToString(" AND "),
            /* selectionArgs = */ selectionValues.toTypedArray(),
            /* sortOrder = */ CalendarContract.Instances.BEGIN + " ASC",
        )?.use { cursor ->
            val eventIdColumnIndex =
                cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleColumnIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginColumnIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endColumnIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayColumnIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val locationColumnIndex =
                cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            while (cursor.moveToNext()) {
                val beginTimeMilliseconds = cursor.getLong(beginColumnIndex)
                val endTimeMilliseconds = cursor.getLong(endColumnIndex)
                // 창 시작 전에 완전히 끝난 일정은 표시 창과 안 겹치므로 제외한다.
                // 창이 지금을 지나면(시작 ≤ now) 이 조건은 진행 중·예정 일정만 남긴다.
                if (endTimeMilliseconds <= searchStartMilliseconds) continue
                entries.add(
                    AgendaEntry(
                        eventId = cursor.getLong(eventIdColumnIndex),
                        title = cursor.getString(titleColumnIndex).orEmpty(),
                        beginTimeMilliseconds = beginTimeMilliseconds,
                        endTimeMilliseconds = endTimeMilliseconds,
                        isAllDay = cursor.getInt(allDayColumnIndex) != 0,
                        location = cursor.getString(locationColumnIndex),
                    ),
                )
            }
        }
        return entries
    }
}
