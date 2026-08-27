package com.calinoti.app

import android.content.Context
import com.calinoti.app.data.AgendaListBuilder
import com.calinoti.app.data.CalendarReader
import com.calinoti.app.data.UserPreferencesRepository
import com.calinoti.app.notification.AgendaNotificationManager
import com.calinoti.app.notification.AgendaRemoteViewsFactory
import com.calinoti.app.scheduling.AgendaRefreshScheduler
import kotlinx.coroutines.flow.first

/** 설정·캘린더·알림을 한 번에 묶어 아젠다 알림을 다시 그리고 다음 갱신을 예약한다. */
class AgendaRefresher(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val calendarReader: CalendarReader,
    private val notificationManager: AgendaNotificationManager,
) {
    suspend fun refreshNow() {
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
