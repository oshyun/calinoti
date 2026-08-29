package com.calinoti.app.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.calinoti.app.R
import com.calinoti.app.data.CalendarIntents
import com.calinoti.app.data.EventEntry
import com.calinoti.app.data.EventListEntry

/**
 * 시작이 임박한(1시간 미만으로 남은) 일정을 아젠다 알림과 별도로 게시한다. Live Updates
 * 승격을 요청해 One UI 8(Android 16) 이상에서는 상태바 칩·실시간 영역으로 노출되고, 미지원
 * 기기에서는 일반 ongoing 알림으로 내려온다. 승격 알림은 커스텀 RemoteViews·colorized·
 * 그룹 요약이 금지되므로(시스템 승격 조건) 이 알림은 시스템 표준 스타일로만 만든다. 사용자가
 * 알림을 지우면 그 일정이 시작할 때까지 재게시하지 않는다 — 반복해서 뜨는 알림은 사용자가
 * 게시 권한을 뺏을 동기가 된다.
 */
class ImminentEventNotifier(
    private val context: Context,
    private val remoteViewsFactory: NotificationViewsFactory,
    private val notificationPublisher: NotificationPublisher,
) {

    /**
     * 사용자가 스와이프로 지운 일정(이벤트 ID → 시작 시각). 프로세스 수명 동안만 유지되며,
     * 시작 시각이 지난 뒤 정리된다. 프로세스가 죽었다 살어나면 다시 게시되는 것은 수용한다.
     */
    private val dismissedEventsByEventId = HashMap<Long, Long>()

    /**
     * 임박 알림을 최신 상태로 맞춘다. 옵션이 꺼졌거나 임박 일정이 없거나(시작됨) 사용자가
     * 지운 일정이면 게시분을 취소한다. 알림 ID가 하나라 어떤 트리거가 몇 번 겹쳐도
     * 마지막 상태 하나만 남는다.
     */
    fun refresh(
        listEntries: List<EventListEntry>,
        isEnabled: Boolean,
        currentTimeMilliseconds: Long,
    ) {
        pruneDismissedEvents(currentTimeMilliseconds)
        val imminentEvent =
            remoteViewsFactory.findImminentEventOrNull(listEntries, currentTimeMilliseconds)
        when {
            imminentEvent == null || !isEnabled || isDismissed(imminentEvent) -> cancelNotification()
            notificationPublisher.hasNotificationPermission() ->
                NotificationManagerCompat.from(context).notify(
                    IMMINENT_NOTIFICATION_ID,
                    buildNotification(imminentEvent, currentTimeMilliseconds),
                )
        }
    }

    /** 사용자가 임박 알림을 스와이프로 지웠다. 시작 전까지는 다시 게시하지 않는다. */
    fun onDismissed(eventId: Long, beginTimeMilliseconds: Long) {
        synchronized(dismissedEventsByEventId) {
            dismissedEventsByEventId[eventId] = beginTimeMilliseconds
        }
    }

    private fun isDismissed(event: EventEntry): Boolean =
        synchronized(dismissedEventsByEventId) {
            dismissedEventsByEventId.containsKey(event.eventId)
        }

    /** 시작이 지난 일정은 더 이상 재게시 금지 대상이 아니므로 목록에서 비운다. */
    private fun pruneDismissedEvents(currentTimeMilliseconds: Long) {
        synchronized(dismissedEventsByEventId) {
            dismissedEventsByEventId.values.removeIf { it <= currentTimeMilliseconds }
        }
    }

    private fun cancelNotification() {
        NotificationManagerCompat.from(context).cancel(IMMINENT_NOTIFICATION_ID)
    }

    private fun buildNotification(
        event: EventEntry,
        currentTimeMilliseconds: Long,
    ): Notification {
        // 위치가 없으면 시작 시각으로 본문을 채운다. 남은 시간은 헤더 카운트다운이 담당하므로
        // 본문 텍스트는 시각만 짧게 안내한다.
        val contentText = event.location?.takeIf { it.isNotEmpty() }
            ?: context.getString(
                R.string.imminent_notification_time_format,
                remoteViewsFactory.formatTimeText(event.beginTimeMilliseconds),
            )
        val eventViewIntent = CalendarIntents.buildEventViewIntent(event.eventId)
        return NotificationCompat.Builder(context, IMMINENT_CHANNEL_ID)
            .setSmallIcon(notificationPublisher.smallIconResourceIdFor(currentTimeMilliseconds))
            .setContentTitle(event.title.ifEmpty { context.getString(R.string.untitled_event) })
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setShowWhen(true)
            .setWhen(event.beginTimeMilliseconds)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    IMMINENT_CONTENT_REQUEST_CODE,
                    eventViewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    context,
                    IMMINENT_DISMISS_REQUEST_CODE,
                    Intent(context, ImminentEventDismissReceiver::class.java)
                        .putExtra(ImminentEventDismissReceiver.EXTRA_EVENT_ID, event.eventId)
                        .putExtra(
                            ImminentEventDismissReceiver.EXTRA_EVENT_BEGIN_TIME_MILLISECONDS,
                            event.beginTimeMilliseconds,
                        ),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    internal companion object {
        const val IMMINENT_CHANNEL_ID = "events_imminent"
        const val IMMINENT_NOTIFICATION_ID = 1005
        private const val IMMINENT_CONTENT_REQUEST_CODE = 1006
        private const val IMMINENT_DISMISS_REQUEST_CODE = 1007
    }
}
