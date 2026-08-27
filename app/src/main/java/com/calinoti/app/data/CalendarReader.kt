package com.calinoti.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/** 시스템 캘린더 프로바이더에서 캘린더 목록과 다가오는 일정을 읽어온다. */
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
            while (cursor.moveToNext()) {
                calendars.add(
                    UserCalendar(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)),
                        displayName = cursor.getString(
                            cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                        ).orEmpty(),
                        accountName = cursor.getString(
                            cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME),
                        ).orEmpty(),
                        color = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)),
                    ),
                )
            }
        }
        return calendars
    }

    /**
     * [daysToLookAhead]일 안에 시작·진행 중인 일정을 시작 시각 순으로 읽어온다.
     * [selectedCalendarIds]가 비어 있으면 모든 캘린더를 대상으로 한다.
     */
    fun loadUpcomingEntries(
        selectedCalendarIds: Set<Long>,
        daysToLookAhead: Int,
        currentTimeMilliseconds: Long,
    ): List<AgendaEntry> {
        if (!hasCalendarPermission() || daysToLookAhead <= 0) return emptyList()

        // Instances 범위 쿼리: 범위와 겹치는 모든 일정 인스턴스(반복 일정 전개 포함)를 반환한다.
        val searchEndMilliseconds = currentTimeMilliseconds + daysToLookAhead * MILLIS_PER_DAY
        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(currentTimeMilliseconds.toString())
            .appendPath(searchEndMilliseconds.toString())
            .build()

        var selection: String? = null
        var selectionArgs: Array<String>? = null
        // 취소된 일정은 아젠다에서 제외한다.
        val selectionFilters = mutableListOf(
            CalendarContract.Instances.STATUS + " != ?",
        )
        val selectionValues = mutableListOf(CalendarContract.Instances.STATUS_CANCELED.toString())
        if (selectedCalendarIds.isNotEmpty()) {
            selectionFilters.add(
                CalendarContract.Instances.CALENDAR_ID + " IN (" +
                    selectedCalendarIds.joinToString(",") { "?" } + ")",
            )
            selectionValues.addAll(selectedCalendarIds.map(Long::toString))
        }
        selection = selectionFilters.joinToString(" AND ")
        selectionArgs = selectionValues.toTypedArray()

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
            selection,
            selectionArgs,
            /* sortOrder = */ CalendarContract.Instances.BEGIN + " ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                entries.add(
                    AgendaEntry(
                        eventId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)),
                        title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)).orEmpty(),
                        beginTimeMilliseconds = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)),
                        endTimeMilliseconds = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)),
                        isAllDay = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)) != 0,
                        location = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)),
                    ),
                )
            }
        }
        return entries
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
