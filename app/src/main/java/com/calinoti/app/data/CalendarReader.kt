package com.calinoti.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/** 시스템 캘린더 프로바이더에서 캘린더 목록과 표시 기간에 든 일정을 읽어온다. */
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
     * [daysToLookAhead]가 양수면 앞으로 그 날수까지의 일정(진행 중 포함)을, 음수면 그 날수 이전부터
     * 지금까지의 이미 끝난 일정을 시작 시각 순으로 읽어온다. 0이면 진행 중인 일정만 해당한다.
     * 범위 쿼리 특성상 창 반대편으로 삐져나간 인스턴스도 섞여 나올 수 있어 종료 시각으로 걸러낸다.
     * [selectedCalendarIds]가 null이면 모든 캘린더를, 빈 집합이면 아무 캘린더도 대상으로 삼지 않는다.
     */
    fun loadAgendaEntries(
        selectedCalendarIds: Set<Long>?,
        daysToLookAhead: Int,
        currentTimeMilliseconds: Long,
    ): List<AgendaEntry> {
        if (!hasCalendarPermission()) return emptyList()

        // 선택된 캘린더가 하나도 없으면 IN () 절을 만들 수 없으므로 여기서 끝낸다.
        if (selectedCalendarIds != null && selectedCalendarIds.isEmpty()) return emptyList()

        // Instances 범위 쿼리: 범위와 겹치는 모든 일정 인스턴스(반복 일정 전개 포함)를 반환한다.
        // 표시 기간이 음수면 창 방향을 반대로 뒤집어 [그 날수 이전, 지금]으로 잡는다.
        val windowSpanMilliseconds = TimeUnit.DAYS.toMillis(daysToLookAhead.toLong())
        val searchStartMilliseconds =
            min(currentTimeMilliseconds, currentTimeMilliseconds + windowSpanMilliseconds)
        val searchEndMilliseconds =
            max(currentTimeMilliseconds, currentTimeMilliseconds + windowSpanMilliseconds)
        val isPastWindow = daysToLookAhead < 0
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
                // 과거 창에서는 아직 끝나지 않은 일정을, 미래 창에서는 이미 끝난 일정을
                // 아젠다에서 제외한다. 미래 창은 진행 중인 일정을 유지한다.
                if (isPastWindow) {
                    if (currentTimeMilliseconds < endTimeMilliseconds) continue
                } else {
                    if (endTimeMilliseconds <= currentTimeMilliseconds) continue
                }
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
