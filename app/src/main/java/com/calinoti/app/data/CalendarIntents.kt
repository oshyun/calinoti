package com.calinoti.app.data

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract

/** 캘린더 앱을 여는 데 쓰는 인텐트. 조립·행 클릭·설치 앱 목록 조회가 같은 shape을 공유한다. */
object CalendarIntents {

    /** 일정 상세 열기 capability 탐침용 가짜 일정 id. 인텐트 shape만 같으면 충분하다. */
    const val EVENT_PROBE_ID = 0L

    /** 캘린더 앱의 일정 상세를 여는 인텐트. (공식 문서: ACTION_VIEW + content://com.android.calendar/events/<id>) */
    fun buildEventViewIntent(eventId: Long): Intent =
        Intent(Intent.ACTION_VIEW)
            .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))

    /** 캘린더 앱을 지정 시각으로 여는 인텐트. (공식 문서: ACTION_VIEW + content://com.android.calendar/time/<ms>) */
    fun buildCalendarTimeViewIntent(timeMilliseconds: Long): Intent {
        val timeUriBuilder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        ContentUris.appendId(timeUriBuilder, timeMilliseconds)
        return Intent(Intent.ACTION_VIEW).setData(timeUriBuilder.build())
    }
}
