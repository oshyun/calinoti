package com.calinoti.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.calinoti.app.data.AgendaEntry
import java.time.Instant
import java.time.ZoneId

/**
 * 아젠다가 변할 수 있는 다음 시점에 갱신 브로드캐스트를 예약한다.
 * 갱신 시점: 다음 일정 시작·종료 시각, 다음 자정(날짜 헤더 갱신), 그리고 안전망 주기.
 * 정확한 시각이 필요 없어 시스템 절전 허용 범위(비-exact 알람)로 예약한다.
 */
object AgendaRefreshScheduler {

    fun scheduleNextRefresh(context: Context, entries: List<AgendaEntry>, currentTimeMilliseconds: Long) {
        val refreshTimes = buildList {
            add(currentTimeMilliseconds + FALLBACK_REFRESH_INTERVAL_MILLIS)
            add(nextLocalMidnight(currentTimeMilliseconds))
            for (entry in entries) {
                if (entry.beginTimeMilliseconds > currentTimeMilliseconds) {
                    add(entry.beginTimeMilliseconds)
                }
                if (entry.endTimeMilliseconds > currentTimeMilliseconds) {
                    add(entry.endTimeMilliseconds)
                }
            }
        }
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            refreshTimes.min(),
            buildRefreshPendingIntent(context),
        )
    }

    fun cancelScheduledRefresh(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(buildRefreshPendingIntent(context))
    }

    private fun buildRefreshPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REFRESH_REQUEST_CODE,
            Intent(context, AgendaRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun nextLocalMidnight(currentTimeMilliseconds: Long): Long {
        val zoneId = ZoneId.systemDefault()
        val tomorrow = Instant.ofEpochMilli(currentTimeMilliseconds)
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(1)
        return tomorrow.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private const val FALLBACK_REFRESH_INTERVAL_MILLIS = 6L * 60 * 60 * 1000
    private const val REFRESH_REQUEST_CODE = 2001
}
