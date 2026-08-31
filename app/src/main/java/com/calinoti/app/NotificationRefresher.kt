package com.calinoti.app

import android.content.Context
import android.util.Log
import com.calinoti.app.data.EventListBuilder
import com.calinoti.app.data.CalendarReader
import com.calinoti.app.data.UserPreferences
import com.calinoti.app.data.UserPreferencesRepository
import com.calinoti.app.notification.ImminentEventNotifier
import com.calinoti.app.notification.NotificationPublisher
import com.calinoti.app.scheduling.NotificationRefreshScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 설정·캘린더·알림을 한 번에 묶어 일정 알림을 다시 그리고 다음 갱신을 예약한다.
 * 어느 트리거 경로(병합 루프, 재부팅 리시버)에서 불리든 실행이 겹치지 않게 Mutex로 직렬화한다.
 */
class NotificationRefresher(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val calendarReader: CalendarReader,
    private val notificationPublisher: NotificationPublisher,
    private val imminentEventNotifier: ImminentEventNotifier,
) {
    private val refreshMutex = Mutex()

    suspend fun refreshNow() {
        refreshMutex.withLock {
            val currentTimeMilliseconds = System.currentTimeMillis()
            try {
                val preferences = userPreferencesRepository.userPreferences.first()
                val eventEntries = calendarReader.loadEventEntries(
                    selectedCalendarIds = preferences.selectedCalendarIds,
                    windowStartDays = preferences.windowStartDays,
                    windowEndDays = preferences.windowEndDays,
                    currentTimeMilliseconds = currentTimeMilliseconds,
                )
                val listEntries = EventListBuilder.buildDayGroupedEntries(eventEntries)
                notificationPublisher.publishEventListNotification(
                    listEntries = listEntries,
                    collapsedHiddenItemTypes = preferences.collapsedHiddenItemTypes,
                    expandedHiddenItemTypes = preferences.expandedHiddenItemTypes,
                    maxVisibleEntries = preferences.maxVisibleEntries,
                    notificationTextSizeSp = preferences.notificationTextSizeSp,
                    allDayEventTextSizeSp = preferences.allDayEventTextSizeSp,
                    eventClickTargetPackageName = preferences.eventClickTargetPackageName,
                    notificationClickTargetPackageName =
                        preferences.notificationClickTargetPackageName,
                    spacing = preferences.notificationSpacing,
                    isNotificationPinned = preferences.isNotificationPinned,
                    dayHeaderFormatPattern = preferences.dayHeaderFormatPattern,
                    currentTimeMilliseconds = currentTimeMilliseconds,
                )
                imminentEventNotifier.refresh(
                    listEntries = listEntries,
                    isEnabled = preferences.isImminentLiveNotificationEnabled,
                    currentTimeMilliseconds = currentTimeMilliseconds,
                )
                NotificationRefreshScheduler.scheduleNextRefresh(
                    context,
                    eventEntries,
                    preferences.notificationUpdateIntervalMinutes,
                    currentTimeMilliseconds,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (runtimeError: Exception) {
                Log.e(TAG, "일정 알림 갱신에 실패했다", runtimeError)
                // 방금 소비된 알람을 대체하지 않으면 갱신 체인이 끊겨 알림이 멈춘다.
                // 설정을 읽지 못했을 수 있으므로 일정 목록 없이 기본 갱신 주기와
                // 자정 알람만 걸어 체인을 이어준다.
                NotificationRefreshScheduler.scheduleNextRefresh(
                    context,
                    emptyList(),
                    UserPreferences.DEFAULTS.notificationUpdateIntervalMinutes,
                    currentTimeMilliseconds,
                )
            }
        }
    }

    private companion object {
        const val TAG = "NotificationRefresher"
    }
}
