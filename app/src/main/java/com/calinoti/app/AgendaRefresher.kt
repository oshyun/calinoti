package com.calinoti.app

import android.content.Context
import android.util.Log
import com.calinoti.app.data.AgendaListBuilder
import com.calinoti.app.data.CalendarReader
import com.calinoti.app.data.UserPreferencesRepository
import com.calinoti.app.notification.AgendaNotificationManager
import com.calinoti.app.scheduling.AgendaRefreshScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 설정·캘린더·알림을 한 번에 묶어 아젠다 알림을 다시 그리고 다음 갱신을 예약한다.
 * 어느 트리거 경로(병합 루프, 재부팅 리시버)에서 불리든 실행이 겹치지 않게 Mutex로 직렬화한다.
 */
class AgendaRefresher(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val calendarReader: CalendarReader,
    private val notificationManager: AgendaNotificationManager,
) {
    private val refreshMutex = Mutex()

    suspend fun refreshNow() {
        refreshMutex.withLock {
            val currentTimeMilliseconds = System.currentTimeMillis()
            try {
                val preferences = userPreferencesRepository.userPreferences.first()
                val upcomingEntries = calendarReader.loadUpcomingEntries(
                    selectedCalendarIds = preferences.selectedCalendarIds,
                    daysToLookAhead = preferences.daysToLookAhead,
                    currentTimeMilliseconds = currentTimeMilliseconds,
                )
                val listEntries = AgendaListBuilder.buildDayGroupedEntries(upcomingEntries)
                notificationManager.publishAgendaNotification(
                    listEntries = listEntries,
                    maxVisibleEntries = preferences.maxVisibleEntries,
                    clickAction = preferences.notificationClickAction,
                    spacing = preferences.notificationSpacing,
                    isNotificationPinned = preferences.isNotificationPinned,
                )
                AgendaRefreshScheduler.scheduleNextRefresh(context, upcomingEntries, currentTimeMilliseconds)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (runtimeError: Exception) {
                Log.e(TAG, "아젠다 갱신에 실패했다", runtimeError)
                // 방금 소비된 알람을 대체하지 않으면 갱신 체인이 끊겨 알림이 멈춘다.
                // 일정 목록 없이 예약하면 6시간 안전망과 자정 알람만 걸린다.
                AgendaRefreshScheduler.scheduleNextRefresh(
                    context,
                    emptyList(),
                    currentTimeMilliseconds,
                )
            }
        }
    }

    private companion object {
        const val TAG = "AgendaRefresher"
    }
}
