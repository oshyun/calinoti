package com.calinoti.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Duration
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
    fun loadEventEntries(
        selectedCalendarIds: Set<Long>?,
        windowStartDays: Int,
        windowEndDays: Int,
        currentTimeMilliseconds: Long,
    ): List<EventEntry> {
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

        // 취소된 일정은 일정 목록에서 제외하고, 선택된 캘린더로 한정한다.
        // QUIRK(calendar-provider-instances-status): Instances 조인은 view_events 위에 있고 status
        //   컬럼명은 eventStatus다. CalendarContract.Instances.STATUS("status")를 selection에 쓰면
        //   컬럼이 없어 쿼리가 실패해(null 반환) 동기화 켜진 캘린더 전체가 조용히 누락된다.
        //   또 CalDAV 동기화 앱은 STATUS 속성 없는 일정을 NULL로 저장하므로 NULL도 명시적으로 허용한다.
        // QUIRK-REMOVE-WHEN: CalendarProvider가 Instances selection에 "status" 별칭을 지원하고
        //   NULL status를 취소가 아닌 것으로 취급하기 시작하면 이 조건을 단순화한다.
        val selectionFilters = mutableListOf(
            "(" + CalendarContract.Events.STATUS + " IS NULL OR " +
                CalendarContract.Events.STATUS + " != ?)",
        )
        val selectionValues = mutableListOf(CalendarContract.Instances.STATUS_CANCELED.toString())
        if (selectedCalendarIds != null) {
            selectionFilters.add(
                CalendarContract.Instances.CALENDAR_ID + " IN (" +
                    selectedCalendarIds.joinToString(",") { "?" } + ")",
            )
            selectionValues.addAll(selectedCalendarIds.map(Long::toString))
        }

        val entries = mutableListOf<EventEntry>()
        context.contentResolver.query(
            instancesUri,
            arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.CALENDAR_COLOR,
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
            val calendarColorColumnIndex =
                cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_COLOR)
            while (cursor.moveToNext()) {
                val beginTimeMilliseconds = cursor.getLong(beginColumnIndex)
                val endTimeMilliseconds = cursor.getLong(endColumnIndex)
                val entry = EventEntry(
                    eventId = cursor.getLong(eventIdColumnIndex),
                    title = cursor.getString(titleColumnIndex).orEmpty(),
                    beginTimeMilliseconds = beginTimeMilliseconds,
                    endTimeMilliseconds = endTimeMilliseconds,
                    isAllDay = cursor.getInt(allDayColumnIndex) != 0,
                    location = cursor.getString(locationColumnIndex),
                    calendarColor = cursor.getInt(calendarColorColumnIndex),
                )
                // 창 시작 전에 완전히 끝난 일정은 표시 창과 안 겹치므로 제외한다.
                // 창이 지금을 지나면(시작 ≤ now) 이 조건은 진행 중·예정 일정만 남긴다.
                // 원시 종료 시각이 아니라 보정된 종료 판정 시각으로 비교한다 — 종일 일정의
                // end는 UTC 자정이라 그대로 비교하면 UTC보다 뒤인 지역(한국, UTC+9)에서
                // 어제 종일 일정이 다음 날 오전 9시까지 창에 새어 나온다
                // (EventEntry.finishTimeMilliseconds 주석 참조).
                if (entry.finishTimeMilliseconds <= searchStartMilliseconds) continue
                entries.add(entry)
            }
        }

        // QUIRK(calendar-provider-unsynced): sync_events=0인 캘린더의 일정은 Instances 테이블에
        //   전개되지 않는다 (AOSP CalendarInstancesHelper가 SYNC_EVENTS != 0 조건으로 전개 대상에서
        //   제외). 그런 캘린더의 일정을 Events 테이블에서 직접 읽어 보충한다. 반복 일정은 공개 API로
        //   회차를 전개할 수 없어 첫 회차만 나온다.
        // QUIRK-REMOVE-WHEN: CalendarProvider가 동기화 꺼진 캘린더도 Instances에 전개하도록 바뀌면
        //   이 보충 경로를 제거한다.
        val unsyncedCalendarIds = loadUnsyncedCalendarIds(selectedCalendarIds)
        if (unsyncedCalendarIds.isNotEmpty()) {
            entries.addAll(
                loadUnsyncedCalendarEventEntries(
                    unsyncedCalendarIds,
                    searchStartMilliseconds,
                    searchEndMilliseconds,
                ),
            )
        }
        // 보충분을 섞었으므로 EventListBuilder가 요구하는 시작 시각 순을 다시 맞춘다.
        return entries.sortedBy { eventEntry -> eventEntry.beginTimeMilliseconds }
    }

    /** Instances에 전개되지 않는 sync_events=0 캘린더의 ID 목록을 [selectedCalendarIds] 한정으로 읽어온다. */
    private fun loadUnsyncedCalendarIds(selectedCalendarIds: Set<Long>?): Set<Long> {
        val unsyncedCalendarIds = mutableSetOf<Long>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            /* selection = */ CalendarContract.Calendars.SYNC_EVENTS + " = 0",
            /* selectionArgs = */ null,
            /* sortOrder = */ null,
        )?.use { cursor ->
            val idColumnIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            while (cursor.moveToNext()) {
                unsyncedCalendarIds.add(cursor.getLong(idColumnIndex))
            }
        }
        return if (selectedCalendarIds == null) {
            unsyncedCalendarIds
        } else {
            unsyncedCalendarIds.intersect(selectedCalendarIds)
        }
    }

    /** sync 꺼진 캘린더의 일정을 Events 테이블에서 직접 읽어온다. 창 시작 전에 끝난 일정은 여기서 걸러낸다. */
    private fun loadUnsyncedCalendarEventEntries(
        calendarIds: Set<Long>,
        searchStartMilliseconds: Long,
        searchEndMilliseconds: Long,
    ): List<EventEntry> {
        val selectionFilters = mutableListOf(
            CalendarContract.Events.CALENDAR_ID + " IN (" + calendarIds.joinToString(",") { "?" } + ")",
            CalendarContract.Events.DELETED + " = 0",
            // STATUS가 없는 일정(CalDAV 동기화 앱이 STATUS 속성 없는 VEVENT를 NULL로 저장)도
            // 취소가 아닌 이상 표시해야 하므로 NULL을 명시적으로 허용한다.
            // SQLite에서 "status != 2"는 NULL 행을 거짓 취급해 제외시킨다.
            "(" + CalendarContract.Events.STATUS + " IS NULL OR " +
                CalendarContract.Events.STATUS + " != ?)",
            CalendarContract.Events.LAST_SYNCED + " = 0",
            CalendarContract.Events.DTSTART + " <= ?",
        )
        val selectionValues = mutableListOf(
            *calendarIds.map(Long::toString).toTypedArray(),
            CalendarContract.Events.STATUS_CANCELED.toString(),
            searchEndMilliseconds.toString(),
        )
        val entries = mutableListOf<EventEntry>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.DURATION,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.CALENDAR_COLOR,
            ),
            /* selection = */ selectionFilters.joinToString(" AND "),
            /* selectionArgs = */ selectionValues.toTypedArray(),
            /* sortOrder = */ null,
        )?.use { cursor ->
            val eventIdColumnIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val titleColumnIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val beginColumnIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endColumnIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val durationColumnIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DURATION)
            val allDayColumnIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
            val locationColumnIndex =
                cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val calendarColorColumnIndex =
                cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_COLOR)
            while (cursor.moveToNext()) {
                val beginTimeMilliseconds = cursor.getLong(beginColumnIndex)
                val endTimeMilliseconds = resolveUnsyncedEventEndTime(
                    declaredEndMilliseconds = cursor.getLong(endColumnIndex),
                    durationText = cursor.getString(durationColumnIndex),
                    beginTimeMilliseconds = beginTimeMilliseconds,
                )
                val entry = EventEntry(
                    eventId = cursor.getLong(eventIdColumnIndex),
                    title = cursor.getString(titleColumnIndex).orEmpty(),
                    beginTimeMilliseconds = beginTimeMilliseconds,
                    endTimeMilliseconds = endTimeMilliseconds,
                    isAllDay = cursor.getInt(allDayColumnIndex) != 0,
                    location = cursor.getString(locationColumnIndex),
                    calendarColor = cursor.getInt(calendarColorColumnIndex),
                )
                // Instances 경로와 같은 이유로 보정된 종료 판정 시각으로 비교한다.
                if (entry.finishTimeMilliseconds <= searchStartMilliseconds) continue
                entries.add(entry)
            }
        }
        return entries
    }

    /**
     * Events 테이블의 종료 시각은 단발 일정은 dtend에, 반복 일정은 duration(RFC 5545 형식)에 들어있다.
     * 둘 다 없는 불완전한 일정은 시작에 곧바로 끝나는 것으로 본다.
     */
    private fun resolveUnsyncedEventEndTime(
        declaredEndMilliseconds: Long,
        durationText: String?,
        beginTimeMilliseconds: Long,
    ): Long {
        if (declaredEndMilliseconds != 0L) return declaredEndMilliseconds
        if (durationText == null) return beginTimeMilliseconds
        return try {
            beginTimeMilliseconds + Duration.parse(durationText).toMillis()
        } catch (malformedDuration: RuntimeException) {
            beginTimeMilliseconds
        }
    }
}
