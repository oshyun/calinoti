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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.calinoti.app.R
import com.calinoti.app.data.AgendaEntry
import com.calinoti.app.data.AgendaListEntry
import com.calinoti.app.data.CalendarIntents
import com.calinoti.app.data.NotificationSpacing
import com.calinoti.app.data.UserPreferences
import com.calinoti.app.scheduling.AgendaRefreshReceiver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
        allDayEventTextSizeSp: Int,
        calendarAppPackageName: String,
        spacing: NotificationSpacing,
        isNotificationPinned: Boolean,
        currentTimeMilliseconds: Long,
    ) {
        if (!hasNotificationPermission()) return
        val notification = buildAgendaNotification(
            listEntries,
            maxVisibleEntries,
            notificationTextSizeSp,
            allDayEventTextSizeSp,
            calendarAppPackageName,
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
        allDayEventTextSizeSp: Int,
        calendarAppPackageName: String,
        spacing: NotificationSpacing,
        isNotificationPinned: Boolean,
        currentTimeMilliseconds: Long,
    ): Notification =
        NotificationCompat.Builder(context, AGENDA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .applyHeaderContent(findNextUpcomingEvent(listEntries, currentTimeMilliseconds))
            .setCustomContentView(
                remoteViewsFactory.createCollapsedViews(
                    listEntries,
                    calendarAppPackageName,
                    spacing,
                    notificationTextSizeSp,
                    allDayEventTextSizeSp,
                    currentTimeMilliseconds,
                ),
            )
            .setCustomBigContentView(
                remoteViewsFactory.createExpandedViews(
                    listEntries,
                    maxVisibleEntries,
                    calendarAppPackageName,
                    spacing,
                    notificationTextSizeSp,
                    allDayEventTextSizeSp,
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
            .setContentIntent(buildContentPendingIntent(calendarAppPackageName, currentTimeMilliseconds))
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
    private fun findNextUpcomingEvent(
        listEntries: List<AgendaListEntry>,
        currentTimeMilliseconds: Long,
    ): AgendaEntry? =
        listEntries
            .filterIsInstance<AgendaListEntry.Event>()
            .map { it.entry }
            .firstOrNull { entry ->
                !entry.isAllDay && entry.beginTimeMilliseconds > currentTimeMilliseconds
            }

    /**
     * 알림에서 일정 행을 제외한 나머지 공간을 눌렀을 때 캘린더 앱을 띄운다.
     * 지정 앱이 있으면 그 앱의 메인 화면(런처 인텐트)을, 없거나 이미 지워졌으면
     * 시스템이 고른 캘린더 앱을 오늘 시점으로 연다.
     */
    private fun buildContentPendingIntent(
        calendarAppPackageName: String,
        currentTimeMilliseconds: Long,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        CONTENT_REQUEST_CODE,
        buildOpenCalendarAppIntent(calendarAppPackageName, currentTimeMilliseconds),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildOpenCalendarAppIntent(
        calendarAppPackageName: String,
        currentTimeMilliseconds: Long,
    ): Intent {
        // 지정 앱이 지워진 뒤 첫 갱신 전까지 남는 구값을 위한 폴백. null이면 암시 인텐트로 돌아간다.
        if (calendarAppPackageName != UserPreferences.UNSPECIFIED_CALENDAR_APP_PACKAGE_NAME) {
            context.packageManager.getLaunchIntentForPackage(calendarAppPackageName)?.let { return it }
        }
        // 시각은 일 시작으로 절단한다. 같은 날에는 인텐트 data가 변하지 않아 갱신마다
        // PendingIntent 레코드가 새로 생기지 않고, "오늘로 열기"라는 의미도 명확해진다.
        val startOfDayMilliseconds =
            LocalDate.ofInstant(Instant.ofEpochMilli(currentTimeMilliseconds), ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        return CalendarIntents.buildCalendarTimeViewIntent(startOfDayMilliseconds)
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
