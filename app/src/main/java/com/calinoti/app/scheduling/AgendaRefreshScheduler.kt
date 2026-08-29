package com.calinoti.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.calinoti.app.data.AgendaEntry
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 아젠다가 변할 수 있는 다음 시점에 갱신 브로드캐스트를 예약한다.
 * 갱신 시점: 다음 일정 시작·종료 시각, 다음 자정(날짜 헤더 갱신), 그리고 사용자가 정한
 * 안전망 주기([notificationUpdateIntervalMinutes]).
 * 정확한 시각이 필요 없어 시스템 절전 허용 범위(비-exact 알람)로 예약한다.
 */
object AgendaRefreshScheduler {

    fun scheduleNextRefresh(
        context: Context,
        entries: List<AgendaEntry>,
        notificationUpdateIntervalMinutes: Int,
        currentTimeMilliseconds: Long,
    ) {
        val notificationUpdateIntervalMilliseconds =
            TimeUnit.MINUTES.toMillis(notificationUpdateIntervalMinutes.toLong())
        val refreshTimes = buildList {
            add(currentTimeMilliseconds + notificationUpdateIntervalMilliseconds)
            add(nextLocalMidnight(currentTimeMilliseconds))
            for (entry in entries) {
                if (entry.beginTimeMilliseconds > currentTimeMilliseconds) {
                    add(entry.beginTimeMilliseconds)
                    // 카운트다운 전환 경계: 시작 1시간 전까지는 (N시간 뒤) 정적 라벨이고,
                    // 그 이후부터는 실시간 카운트다운을 보여준다. 경계에도 갱신을 걸어
                    // 전환 시점을 정확히 맞춘다.
                    val countdownSwitchMilliseconds =
                        entry.beginTimeMilliseconds - TimeUnit.HOURS.toMillis(1)
                    if (countdownSwitchMilliseconds > currentTimeMilliseconds) {
                        add(countdownSwitchMilliseconds)
                    }
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

    private const val REFRESH_REQUEST_CODE = 2001
}
