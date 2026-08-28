package com.calinoti.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.calinoti.app.MainActivity
import com.calinoti.app.R
import com.calinoti.app.data.AgendaEntry
import com.calinoti.app.data.AgendaListEntry
import com.calinoti.app.data.NotificationClickAction
import com.calinoti.app.data.NotificationSpacing
import com.calinoti.app.scheduling.AgendaRefreshReceiver

/** 아젠다 지속 알림의 채널 생성·발행과 알림 권한 확인을 담당한다. */
class AgendaNotificationManager(
    private val context: Context,
    private val remoteViewsFactory: AgendaRemoteViewsFactory,
) {

    fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            AGENDA_CHANNEL_ID,
            context.getString(R.string.notification_channel_agenda_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_agenda_description)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** 알림 권한 상태의 단일 출처. 발행 가드와 UI 확인이 이 함수 하나를 쓴다. */
    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /**
     * 권한 안내 카드를 보여줘야 하는지. API 33 미만은 알림 권한의 런타임 요청 UI가 없어
     * 앱 안에서 해결할 수 없으므로 카드 대상에서 제외한다.
     */
    fun shouldPromptForNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()

    fun publishAgendaNotification(
        listEntries: List<AgendaListEntry>,
        maxVisibleEntries: Int,
        notificationTextSizeSp: Int,
        clickAction: NotificationClickAction,
        spacing: NotificationSpacing,
        isNotificationPinned: Boolean,
        currentTimeMilliseconds: Long,
    ) {
        if (!hasNotificationPermission()) return
        val notification = buildAgendaNotification(
            listEntries,
            maxVisibleEntries,
            notificationTextSizeSp,
            clickAction,
            spacing,
            isNotificationPinned,
            currentTimeMilliseconds,
        )
        NotificationManagerCompat.from(context).notify(AGENDA_NOTIFICATION_ID, notification)
    }

    private fun buildAgendaNotification(
        listEntries: List<AgendaListEntry>,
        maxVisibleEntries: Int,
        notificationTextSizeSp: Int,
        clickAction: NotificationClickAction,
        spacing: NotificationSpacing,
        isNotificationPinned: Boolean,
        currentTimeMilliseconds: Long,
    ): Notification =
        NotificationCompat.Builder(context, AGENDA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .applyHeaderContent(findNextUpcomingEvent(listEntries))
            .setCustomContentView(
                remoteViewsFactory.createCollapsedViews(
                    listEntries,
                    spacing,
                    notificationTextSizeSp,
                    currentTimeMilliseconds,
                ),
            )
            .setCustomBigContentView(
                remoteViewsFactory.createExpandedViews(
                    listEntries,
                    maxVisibleEntries,
                    spacing,
                    notificationTextSizeSp,
                    currentTimeMilliseconds,
                ),
            )
            // Android 14부터는 ongoing 알림도 스와이프로 지워진다. 고정이 켜져 있으면
            // dismiss(deleteIntent)로 감지해 갱신 리시버로 되돌려 다시 게시한다.
            .setOngoing(isNotificationPinned)
            .setDeleteIntent(
                if (isNotificationPinned) buildDismissRestorePendingIntent() else null,
            )
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(buildContentPendingIntent(clickAction))
            .build()

    /**
     * 시스템 헤더를 다음 일정 정보로 채운다. 헤더는 시스템이 그리는 영역이라 없앨 수는
     * 없고, 시간 자리를 남은 시간 카운트다운(setChronometerCountDown)으로, subText를
     * 일정 제목으로 바꾸는 것만 가능하다. 카운트다운이 0에 닿으면 AgendaRefreshScheduler가
     * 일정 시작 시각에 알림을 다시 게시해 다음 일정 기준으로 넘어간다.
     */
    private fun NotificationCompat.Builder.applyHeaderContent(
        nextUpcomingEvent: AgendaEntry?,
    ): NotificationCompat.Builder {
        if (nextUpcomingEvent == null) {
            setShowWhen(false)
            return this
        }
        setWhen(nextUpcomingEvent.beginTimeMilliseconds)
        setUsesChronometer(true)
        setChronometerCountDown(true)
        setSubText(
            nextUpcomingEvent.title.ifEmpty { context.getString(R.string.agenda_untitled_event) },
        )
        return this
    }

    /** 아직 시작하지 않은 첫 일정. 종일 일정은 시작이 UTC 자정이라 카운트다운 대상에서 뺀다. */
    private fun findNextUpcomingEvent(listEntries: List<AgendaListEntry>): AgendaEntry? =
        listEntries
            .filterIsInstance<AgendaListEntry.Event>()
            .map { it.entry }
            .firstOrNull { entry ->
                !entry.isAllDay && entry.beginTimeMilliseconds > System.currentTimeMillis()
            }

    private fun buildContentPendingIntent(clickAction: NotificationClickAction): PendingIntent {
        val contentIntent = when (clickAction) {
            NotificationClickAction.OPEN_APP -> Intent(context, MainActivity::class.java)
            // 캘린더 앱의 새 일정 화면을 띄운다.
            NotificationClickAction.CREATE_EVENT ->
                Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        }
        return PendingIntent.getActivity(
            context,
            CONTENT_REQUEST_CODE,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 알림이 스와이프로 지워졌을 때 갱신 리시버로 되돌려 알림을 다시 게시하는 인텐트. */
    private fun buildDismissRestorePendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            DISMISS_RESTORE_REQUEST_CODE,
            Intent(context, AgendaRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val AGENDA_CHANNEL_ID = "agenda"
        const val AGENDA_NOTIFICATION_ID = 1001
        const val CONTENT_REQUEST_CODE = 1002
        const val DISMISS_RESTORE_REQUEST_CODE = 1003
    }
}
