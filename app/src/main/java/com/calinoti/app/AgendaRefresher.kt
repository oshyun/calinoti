package com.calinoti.app

import android.content.Context
import com.calinoti.app.data.AgendaListBuilder
import com.calinoti.app.data.CalendarReader
import com.calinoti.app.data.UserPreferencesRepository
import com.calinoti.app.notification.AgendaNotificationManager
import com.calinoti.app.scheduling.AgendaRefreshScheduler
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
            val preferences = userPreferencesRepository.userPreferences.first()
            val currentTimeMilliseconds = System.currentTimeMillis()
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
            )
            AgendaRefreshScheduler.scheduleNextRefresh(context, upcomingEntries, currentTimeMilliseconds)
        }
    }
}
