package com.calinoti.app.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.calinoti.app.R
import com.calinoti.app.data.EventEntry
import com.calinoti.app.data.EventListEntry
import com.calinoti.app.data.KeywordHideRule
import com.calinoti.app.data.UserPreferences

/**
 * 시작이 임박한(1시간 미만으로 남은) 일정을 아젠다 알림과 별도로 게시한다. Android 16
 * Live Updates 승격을 요청해 One UI 8(Android 16) 이상에서는 실시간 알림(Now Bar)·
 * 상태바 칩으로 노출되고, 미지원·미승격 기기에서는 해제 버튼이 있는 상단 고정 알림으로
 * 표시된다. Live Updates 승격 규칙에 따라 커스텀 RemoteViews·colorized·그룹 요약은
 * 사용하지 않고 BigTextStyle과 Chronometer 카운트다운만 사용한다. 사용자가 스와이프하거나
 * 해제 버튼으로 닫으면 해당 시작 시각 전까지는 다시 게시하지 않는다.
 */
class ImminentEventNotifier(
    private val context: Context,
    private val remoteViewsFactory: NotificationViewsFactory,
    private val notificationPublisher: NotificationPublisher,
) {

    private val dismissedEventsLock = Any()

    /**
     * 사용자가 스와이프·해제 버튼으로 지운 일정(이벤트 ID → 시작 시각). 프로세스 종료 뒤에도
     * 알림 갱신 알람으로 다시 살아나지 않도록 SharedPreferences에 함께 보관하고, 시작 시각이
     * 지나면 정리한다.
     */
    private val dismissedEventsByEventId = HashMap<Long, Long>()
    private var hasLoadedDismissedEvents = false
    private val dismissedPreferences by lazy {
        context.getSharedPreferences(DISMISSED_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 임박 알림을 최신 상태로 맞춘다. 옵션이 꺼졌거나 임박 일정이 없거나(시작됨) 사용자가
     * 해제한 일정이면 게시분을 취소한다. 알림 ID가 하나라 어떤 트리거가 몇 번 겹쳐도
     * 마지막 상태 하나만 남는다.
     */
    fun refresh(
        listEntries: List<EventListEntry>,
        isEnabled: Boolean,
        keywordHideRules: List<KeywordHideRule>,
        eventClickTargetPackageName: String = UserPreferences.UNSPECIFIED_CLICK_TARGET_PACKAGE_NAME,
        currentTimeMilliseconds: Long,
    ) {
        pruneDismissedEvents(currentTimeMilliseconds)
        val imminentEvent = remoteViewsFactory.findImminentEventOrNull(
            listEntries,
            keywordHideRules,
            currentTimeMilliseconds,
        )
        when {
            imminentEvent == null || !isEnabled || isDismissed(imminentEvent) -> cancelNotification()
            notificationPublisher.hasNotificationPermission() ->
                NotificationManagerCompat.from(context).notify(
                    IMMINENT_NOTIFICATION_ID,
                    buildNotification(
                        event = imminentEvent,
                        eventClickTargetPackageName = eventClickTargetPackageName,
                        currentTimeMilliseconds = currentTimeMilliseconds,
                    ),
                )
        }
    }

    /** 사용자가 임박 알림을 스와이프하거나 '해제' 버튼으로 지웠다. 해당 시작 전까지는 재게시하지 않는다. */
    fun onDismissed(eventId: Long, beginTimeMilliseconds: Long) {
        synchronized(dismissedEventsLock) {
            ensureDismissedEventsLoaded()
            dismissedEventsByEventId[eventId] = beginTimeMilliseconds
            dismissedPreferences.edit {
                putLong(eventId.toString(), beginTimeMilliseconds)
            }
        }
        // '해제' 액션 버튼은 스와이프와 달리 시스템이 알림을 자동으로 지우지 않으므로 즉시 취소한다.
        cancelNotification()
    }

    private fun ensureDismissedEventsLoaded() {
        if (hasLoadedDismissedEvents) return
        for ((key, value) in dismissedPreferences.all) {
            val eventId = key.toLongOrNull() ?: continue
            val beginTimeMilliseconds = value as? Long ?: continue
            dismissedEventsByEventId[eventId] = beginTimeMilliseconds
        }
        hasLoadedDismissedEvents = true
    }

    private fun isDismissed(event: EventEntry): Boolean =
        synchronized(dismissedEventsLock) {
            ensureDismissedEventsLoaded()
            dismissedEventsByEventId[event.eventId] == event.beginTimeMilliseconds
        }

    /** 시작이 지난 일정은 더 이상 재게시 금지 대상이 아니므로 메모리·저장소에서 함께 비운다. */
    private fun pruneDismissedEvents(currentTimeMilliseconds: Long) {
        synchronized(dismissedEventsLock) {
            ensureDismissedEventsLoaded()
            val expiredEventIds = dismissedEventsByEventId.entries
                .filter { (_, beginTimeMilliseconds) ->
                    beginTimeMilliseconds <= currentTimeMilliseconds
                }
                .map { it.key }
            if (expiredEventIds.isEmpty()) return
            dismissedPreferences.edit {
                for (eventId in expiredEventIds) {
                    dismissedEventsByEventId.remove(eventId)
                    remove(eventId.toString())
                }
            }
        }
    }

    private fun cancelNotification() {
        NotificationManagerCompat.from(context).cancel(IMMINENT_NOTIFICATION_ID)
    }

    private fun buildNotification(
        event: EventEntry,
        eventClickTargetPackageName: String,
        currentTimeMilliseconds: Long,
    ): Notification {
        val startTimeLine = context.getString(
            R.string.imminent_notification_time_format,
            remoteViewsFactory.formatTimeText(event.beginTimeMilliseconds),
        )
        val location = event.location?.trim().orEmpty()
        val contentText = if (location.isNotEmpty()) {
            context.getString(
                R.string.imminent_notification_time_and_location_format,
                startTimeLine,
                location,
            )
        } else {
            startTimeLine
        }
        val eventTitle = event.title.ifEmpty { context.getString(R.string.untitled_event) }
        return NotificationCompat.Builder(context, IMMINENT_CHANNEL_ID)
            .setSmallIcon(notificationPublisher.smallIconResourceIdFor(currentTimeMilliseconds))
            .setContentTitle(eventTitle)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            // Android 16 Live Updates에서는 colorized가 금지되므로 setColorized를 쓰지 않는다.
            // setShortCriticalText를 비워 두어 상태바 칩·실시간 카드가 정적 문구 대신 Chronometer
            // 초 단위 카운트다운으로 갱신되도록 한다.
            .setShowWhen(true)
            .setWhen(event.beginTimeMilliseconds)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setGroup(IMMINENT_GROUP_KEY)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                remoteViewsFactory.createEventClickPendingIntent(
                    eventId = event.eventId,
                    eventClickTargetPackageName = eventClickTargetPackageName,
                    requestCode = IMMINENT_CONTENT_REQUEST_CODE,
                ),
            )
            .setDeleteIntent(
                buildDismissPendingIntent(event, IMMINENT_DISMISS_REQUEST_CODE),
            )
            .addAction(
                0,
                context.getString(R.string.imminent_notification_dismiss_action),
                buildDismissPendingIntent(event, IMMINENT_DISMISS_ACTION_REQUEST_CODE),
            )
            .build()
    }

    private fun buildDismissPendingIntent(event: EventEntry, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ImminentEventDismissReceiver::class.java)
                .putExtra(ImminentEventDismissReceiver.EXTRA_EVENT_ID, event.eventId)
                .putExtra(
                    ImminentEventDismissReceiver.EXTRA_EVENT_BEGIN_TIME_MILLISECONDS,
                    event.beginTimeMilliseconds,
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    internal companion object {
        const val IMMINENT_CHANNEL_ID = "events_imminent"
        const val IMMINENT_NOTIFICATION_ID = 1005
        private const val IMMINENT_GROUP_KEY = "events_imminent"
        private const val DISMISSED_PREFERENCES_NAME = "imminent_event_dismissals"
        private const val IMMINENT_CONTENT_REQUEST_CODE = 1006
        private const val IMMINENT_DISMISS_REQUEST_CODE = 1007
        private const val IMMINENT_DISMISS_ACTION_REQUEST_CODE = 1008
    }
}
