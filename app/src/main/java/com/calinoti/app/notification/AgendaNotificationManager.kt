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
import com.calinoti.app.data.AgendaListEntry
import com.calinoti.app.data.NotificationClickAction
import com.calinoti.app.data.NotificationSpacing

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
        clickAction: NotificationClickAction,
        spacing: NotificationSpacing,
    ) {
        if (!hasNotificationPermission()) return
        val notification = buildAgendaNotification(listEntries, maxVisibleEntries, clickAction, spacing)
        NotificationManagerCompat.from(context).notify(AGENDA_NOTIFICATION_ID, notification)
    }

    private fun buildAgendaNotification(
        listEntries: List<AgendaListEntry>,
        maxVisibleEntries: Int,
        clickAction: NotificationClickAction,
        spacing: NotificationSpacing,
    ): Notification =
        NotificationCompat.Builder(context, AGENDA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(remoteViewsFactory.createCollapsedViews(listEntries, spacing))
            .setCustomBigContentView(
                remoteViewsFactory.createExpandedViews(listEntries, maxVisibleEntries, spacing),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(buildContentPendingIntent(clickAction))
            .build()

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

    private companion object {
        const val AGENDA_CHANNEL_ID = "agenda"
        const val AGENDA_NOTIFICATION_ID = 1001
        const val CONTENT_REQUEST_CODE = 1002
    }
}
