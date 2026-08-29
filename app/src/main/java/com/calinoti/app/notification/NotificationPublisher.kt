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
import com.calinoti.app.data.EventEntry
import com.calinoti.app.data.EventListEntry
import com.calinoti.app.data.CalendarIntents
import com.calinoti.app.data.HiddenItemType
import com.calinoti.app.data.NotificationSpacing
import com.calinoti.app.data.UserPreferences
import com.calinoti.app.scheduling.NotificationRefreshReceiver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 일정 지속 알림의 채널 생성·발행과 알림 권한 확인을 담당한다. */
class NotificationPublisher(
    private val context: Context,
    private val remoteViewsFactory: NotificationViewsFactory,
) {

    fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            EVENTS_CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
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

    fun publishEventListNotification(
        listEntries: List<EventListEntry>,
        collapsedHiddenItemTypes: Set<HiddenItemType>,
        expandedHiddenItemTypes: Set<HiddenItemType>,
        maxVisibleEntries: Int,
        notificationTextSizeSp: Int,
        allDayEventTextSizeSp: Int,
        eventClickTargetPackageName: String,
        notificationClickTargetPackageName: String,
        spacing: NotificationSpacing,
        isNotificationPinned: Boolean,
        dayHeaderFormatPattern: String,
        currentTimeMilliseconds: Long,
    ) {
        if (!hasNotificationPermission()) return
        val notification = buildEventListNotification(
            listEntries,
            collapsedHiddenItemTypes,
            expandedHiddenItemTypes,
            maxVisibleEntries,
            notificationTextSizeSp,
            allDayEventTextSizeSp,
            eventClickTargetPackageName,
            notificationClickTargetPackageName,
            spacing,
            isNotificationPinned,
            dayHeaderFormatPattern,
            currentTimeMilliseconds,
        )
        NotificationManagerCompat.from(context).notify(EVENT_LIST_NOTIFICATION_ID, notification)
    }

    private fun buildEventListNotification(
        listEntries: List<EventListEntry>,
        collapsedHiddenItemTypes: Set<HiddenItemType>,
        expandedHiddenItemTypes: Set<HiddenItemType>,
        maxVisibleEntries: Int,
        notificationTextSizeSp: Int,
        allDayEventTextSizeSp: Int,
        eventClickTargetPackageName: String,
        notificationClickTargetPackageName: String,
        spacing: NotificationSpacing,
        isNotificationPinned: Boolean,
        dayHeaderFormatPattern: String,
        currentTimeMilliseconds: Long,
    ): Notification {
        // 접힌 뷰와 펼친 뷰는 각자의 감춤 규칙 집합을 받는다 — 두 규칙은 서로 독립이며,
        // 어느 쪽이든 빈 집합이면 감춤 없이 전부 표시한다(필터는 빈 집합에서 멱등하다).
        return NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(smallIconResourceIdFor(currentTimeMilliseconds))
            .applyHeaderContent(findNextUpcomingEvent(listEntries, currentTimeMilliseconds))
            .setCustomContentView(
                remoteViewsFactory.createCollapsedViews(
                    listEntries,
                    collapsedHiddenItemTypes,
                    eventClickTargetPackageName,
                    spacing,
                    notificationTextSizeSp,
                    allDayEventTextSizeSp,
                    dayHeaderFormatPattern,
                    currentTimeMilliseconds,
                ),
            )
            .setCustomBigContentView(
                remoteViewsFactory.createExpandedViews(
                    listEntries,
                    expandedHiddenItemTypes,
                    maxVisibleEntries,
                    eventClickTargetPackageName,
                    spacing,
                    notificationTextSizeSp,
                    allDayEventTextSizeSp,
                    dayHeaderFormatPattern,
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
            .setContentIntent(
                buildContentPendingIntent(notificationClickTargetPackageName, currentTimeMilliseconds),
            )
            .build()
    }

    /**
     * 시스템 헤더를 다음 일정 정보로 채운다. 헤더는 시스템이 그리는 영역이라 없앨 수는
     * 없고, 시간 자리를 남은 시간 카운트다운(setChronometerCountDown)으로, subText를
     * 일정 제목으로 바꾸는 것만 가능하다. 카운트다운이 0에 닿으면 NotificationRefreshScheduler가
     * 일정 시작 시각에 알림을 다시 게시해 다음 일정 기준으로 넘어간다.
     */
    private fun NotificationCompat.Builder.applyHeaderContent(
        nextUpcomingEvent: EventEntry?,
    ): NotificationCompat.Builder {
        if (nextUpcomingEvent == null) {
            setShowWhen(false)
            return this
        }
        setWhen(nextUpcomingEvent.beginTimeMilliseconds)
        setUsesChronometer(true)
        setChronometerCountDown(true)
        setSubText(
            nextUpcomingEvent.title.ifEmpty { context.getString(R.string.untitled_event) },
        )
        return this
    }

    /**
     * 아직 시작하지 않은 첫 일정. 종일 일정은 시작이 UTC 자정이라 카운트다운 대상에서 뺀다.
     * 접힌 뷰 감춤 설정과 무관하게 항상 원본 목록에서 찾는다 — 종일 일정이 애초 대상이 아니라
     * 감춰진 종일 일정이 시스템 헤더(subText·카운트다운)로 새어 나갈 일도 없다.
     */
    private fun findNextUpcomingEvent(
        listEntries: List<EventListEntry>,
        currentTimeMilliseconds: Long,
    ): EventEntry? =
        listEntries
            .filterIsInstance<EventListEntry.Event>()
            .map { it.entry }
            .firstOrNull { entry ->
                !entry.isAllDay && entry.beginTimeMilliseconds > currentTimeMilliseconds
            }

    /**
     * 알림에서 일정 행을 제외한 나머지 공간(여백)을 눌렀을 때 지정된 앱을 띄운다.
     * 지정 앱이 있으면 그 앱의 메인 화면(런처 인텐트)을 — 지정이 Calinoti 자신이면 Calinoti를
     * — 없거나 이미 지워졌으면 시스템이 고른 캘린더 앱을 오늘 시점으로 연다.
     */
    private fun buildContentPendingIntent(
        notificationClickTargetPackageName: String,
        currentTimeMilliseconds: Long,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        CONTENT_REQUEST_CODE,
        buildOpenClickTargetIntent(notificationClickTargetPackageName, currentTimeMilliseconds),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildOpenClickTargetIntent(
        notificationClickTargetPackageName: String,
        currentTimeMilliseconds: Long,
    ): Intent {
        // 지정 앱이 지워진 뒤 첫 갱신 전까지 남는 구값을 위한 폴백. null이면 암시 인텐트로 돌아간다.
        if (notificationClickTargetPackageName !=
            UserPreferences.UNSPECIFIED_CLICK_TARGET_PACKAGE_NAME
        ) {
            context.packageManager.getLaunchIntentForPackage(notificationClickTargetPackageName)
                ?.let { return it }
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
            Intent(context, NotificationRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * 발행 시각이 속한 날짜의 상태바 아이콘. 날짜 숫자를 담은 캘린더 벡터를 써서
     * 상태바에 떠 있는 동안 오늘이 며칠인지 바로 보인다. 알림은 자정 갱신과 일정
     * 변경마다 다시 발행되므로 날짜가 바뀌면 아이콘도 따라 바뀐다.
     */
    private fun smallIconResourceIdFor(currentTimeMilliseconds: Long): Int {
        val dayOfMonth = LocalDate.ofInstant(
            Instant.ofEpochMilli(currentTimeMilliseconds),
            ZoneId.systemDefault(),
        ).dayOfMonth
        return smallIconResourceIdsByDayOfMonth[dayOfMonth - 1]
    }

    private companion object {
        const val EVENTS_CHANNEL_ID = "events"
        const val EVENT_LIST_NOTIFICATION_ID = 1001
        const val CONTENT_REQUEST_CODE = 1002
        const val DISMISS_RESTORE_REQUEST_CODE = 1003

        // 인덱스는 dayOfMonth - 1. 파일들은 생성물이므로
        // tools/generate_notification_icons.py로 다시 만든다.
        val smallIconResourceIdsByDayOfMonth = listOf(
            R.drawable.ic_notification_day_1,
            R.drawable.ic_notification_day_2,
            R.drawable.ic_notification_day_3,
            R.drawable.ic_notification_day_4,
            R.drawable.ic_notification_day_5,
            R.drawable.ic_notification_day_6,
            R.drawable.ic_notification_day_7,
            R.drawable.ic_notification_day_8,
            R.drawable.ic_notification_day_9,
            R.drawable.ic_notification_day_10,
            R.drawable.ic_notification_day_11,
            R.drawable.ic_notification_day_12,
            R.drawable.ic_notification_day_13,
            R.drawable.ic_notification_day_14,
            R.drawable.ic_notification_day_15,
            R.drawable.ic_notification_day_16,
            R.drawable.ic_notification_day_17,
            R.drawable.ic_notification_day_18,
            R.drawable.ic_notification_day_19,
            R.drawable.ic_notification_day_20,
            R.drawable.ic_notification_day_21,
            R.drawable.ic_notification_day_22,
            R.drawable.ic_notification_day_23,
            R.drawable.ic_notification_day_24,
            R.drawable.ic_notification_day_25,
            R.drawable.ic_notification_day_26,
            R.drawable.ic_notification_day_27,
            R.drawable.ic_notification_day_28,
            R.drawable.ic_notification_day_29,
            R.drawable.ic_notification_day_30,
            R.drawable.ic_notification_day_31,
        )
    }
}
