package com.calinoti.app.data

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import java.time.Instant

/** 설정 파일(YAML) 직렬화 형식의 단일 출처. 낯선 키(미래 버전이 남긴 것)는 무시하고 읽는다. */
private val userPreferencesBackupYaml = Yaml(
    configuration = YamlConfiguration(
        // 기본값(true)은 낯선 키에서 예외를 던진다. 파일에 미래 버전의 키가 섞여 있어도
        // 가져올 수 있게 해서 이 저장소의 "낯선 저장값은 버린다" 정책과 맞춘다.
        strictMode = false,
        // 시퀀스 항목을 부모 키보다 2칸 들여써 계층이 보이게 한다(기본값 0은 키와 같은 칸).
        sequenceBlockIndent = 2,
    ),
)

/** 설정 전체를 YAML 파일 내용으로 직렬화한다. */
fun encodeUserPreferencesBackup(preferences: ExportedUserPreferences): String =
    userPreferencesBackupYaml.encodeToString(ExportedUserPreferences.serializer(), preferences)

/** YAML 파일 내용을 설정으로 되돌린다. 파일이 깨졌거나 형식이 다르면 YamlException을 던진다. */
fun decodeUserPreferencesBackup(fileContent: String): ExportedUserPreferences =
    userPreferencesBackupYaml.decodeFromString(ExportedUserPreferences.serializer(), fileContent)

/** 알림 화면 여백의 파일 표현. 필드 구조는 [NotificationSpacing]과 같고 기본값도 같은 곳에서 온다. */
@Serializable
data class ExportedNotificationSpacing(
    val outerVerticalPaddingDp: Int = NotificationSpacing.DEFAULTS.outerVerticalPaddingDp,
    val dayHeaderStartPaddingDp: Int = NotificationSpacing.DEFAULTS.dayHeaderStartPaddingDp,
    val dayHeaderToEventSpacingDp: Int = NotificationSpacing.DEFAULTS.dayHeaderToEventSpacingDp,
    val betweenEventsSpacingDp: Int = NotificationSpacing.DEFAULTS.betweenEventsSpacingDp,
    val betweenDayHeadersSpacingDp: Int = NotificationSpacing.DEFAULTS.betweenDayHeadersSpacingDp,
    val timeToTitleSpacingDp: Int = NotificationSpacing.DEFAULTS.timeToTitleSpacingDp,
)

/** 설정 파일의 한 덩어리. 모든 필드에 기본값이 있으므로 일부만 담긴 파일도 읽힌다. */
@Serializable
data class ExportedUserPreferences(
    /** 내보낸 시각(UTC, ISO-8601). 참고용이고 가져오기에서 무시한다. */
    val exportedAtUtc: String = "",
    /** 내보낼 때의 앱 버전 이름. 참고용이고 가져오기에서 무시한다. */
    val appVersionName: String = "",
    /** 표시할 캘린더 ID. null은 모든 캘린더, 빈 목록은 선택 없음. ID는 기기마다 다를 수 있다. */
    val selectedCalendarIds: List<Long>? = null,
    val windowStartDays: Int = UserPreferences.DEFAULTS.windowStartDays,
    val windowEndDays: Int = UserPreferences.DEFAULTS.windowEndDays,
    val maxVisibleEntries: Int = UserPreferences.DEFAULTS.maxVisibleEntries,
    val notificationTextSizeSp: Int = UserPreferences.DEFAULTS.notificationTextSizeSp,
    val allDayEventTextSizeSp: Int = UserPreferences.DEFAULTS.allDayEventTextSizeSp,
    val eventClickTargetPackageName: String = UserPreferences.DEFAULTS.eventClickTargetPackageName,
    val notificationClickTargetPackageName: String = UserPreferences.DEFAULTS.notificationClickTargetPackageName,
    val notificationSpacing: ExportedNotificationSpacing = ExportedNotificationSpacing(),
    val isNotificationPinned: Boolean = UserPreferences.DEFAULTS.isNotificationPinned,
    val isImminentLiveNotificationEnabled: Boolean = UserPreferences.DEFAULTS.isImminentLiveNotificationEnabled,
    /** 감춤 항목은 [HiddenItemType]의 이름 목록으로 담는다. 낯선 이름은 가져올 때 버린다. */
    val collapsedHiddenItemTypes: List<String> = emptyList(),
    val expandedHiddenItemTypes: List<String> = emptyList(),
    /** 단어 감춤 규칙은 저장 모델([KeywordHideRule])을 그대로 담는다. ID는 가져올 때 다시 발급한다. */
    val keywordHideRules: List<KeywordHideRule> = emptyList(),
    val dayHeaderFormatPattern: String = UserPreferences.DEFAULTS.dayHeaderFormatPattern,
    val notificationUpdateIntervalMinutes: Int = UserPreferences.DEFAULTS.notificationUpdateIntervalMinutes,
)

/** 현재 설정을 파일 내용으로 옮긴다. 머리말 두 필드([ExportedUserPreferences.exportedAtUtc]·appVersionName)는 참고용이다. */
fun UserPreferences.toExportedUserPreferences(
    exportedAtUtc: Instant,
    appVersionName: String,
): ExportedUserPreferences =
    ExportedUserPreferences(
        exportedAtUtc = exportedAtUtc.toString(),
        appVersionName = appVersionName,
        // 정렬해 내보내 파일이 집합 순회 순서에 좌우되지 않게 한다.
        selectedCalendarIds = selectedCalendarIds?.sorted(),
        windowStartDays = windowStartDays,
        windowEndDays = windowEndDays,
        maxVisibleEntries = maxVisibleEntries,
        notificationTextSizeSp = notificationTextSizeSp,
        allDayEventTextSizeSp = allDayEventTextSizeSp,
        eventClickTargetPackageName = eventClickTargetPackageName,
        notificationClickTargetPackageName = notificationClickTargetPackageName,
        notificationSpacing = ExportedNotificationSpacing(
            outerVerticalPaddingDp = notificationSpacing.outerVerticalPaddingDp,
            dayHeaderStartPaddingDp = notificationSpacing.dayHeaderStartPaddingDp,
            dayHeaderToEventSpacingDp = notificationSpacing.dayHeaderToEventSpacingDp,
            betweenEventsSpacingDp = notificationSpacing.betweenEventsSpacingDp,
            betweenDayHeadersSpacingDp = notificationSpacing.betweenDayHeadersSpacingDp,
            timeToTitleSpacingDp = notificationSpacing.timeToTitleSpacingDp,
        ),
        isNotificationPinned = isNotificationPinned,
        isImminentLiveNotificationEnabled = isImminentLiveNotificationEnabled,
        collapsedHiddenItemTypes = collapsedHiddenItemTypes.map(HiddenItemType::name),
        expandedHiddenItemTypes = expandedHiddenItemTypes.map(HiddenItemType::name),
        keywordHideRules = keywordHideRules,
        dayHeaderFormatPattern = dayHeaderFormatPattern,
        notificationUpdateIntervalMinutes = notificationUpdateIntervalMinutes,
    )

/**
 * 파일 내용을 설정으로 되돌린다. 조절 범위 밖의 값은 저장소 읽기 경로와 같은 규칙("범위 밖
 * 저장값은 범위 안으로 끌어온다")으로 정상화한다 — 손으로 편집한 파일의 비정상 값이
 * 알림 갱신을 멈추게 두지 않기 위해서다.
 */
fun ExportedUserPreferences.toUserPreferences(): UserPreferences =
    UserPreferences(
        // null 유지가 핵심이다. null(모든 캘린더)과 빈 목록(선택 없음)은 다른 상태다.
        selectedCalendarIds = selectedCalendarIds?.toSet(),
        windowStartDays = windowStartDays,
        windowEndDays = maxOf(windowEndDays, windowStartDays),
        maxVisibleEntries = maxVisibleEntries.coerceAtLeast(1),
        notificationTextSizeSp = notificationTextSizeSp
            .coerceIn(UserPreferences.NOTIFICATION_TEXT_SIZE_RANGE_SP),
        allDayEventTextSizeSp = allDayEventTextSizeSp
            .coerceIn(UserPreferences.NOTIFICATION_TEXT_SIZE_RANGE_SP),
        eventClickTargetPackageName = eventClickTargetPackageName,
        notificationClickTargetPackageName = notificationClickTargetPackageName,
        notificationSpacing = NotificationSpacing(
            outerVerticalPaddingDp = notificationSpacing.outerVerticalPaddingDp
                .coerceIn(NotificationSpacing.RANGE_DP),
            dayHeaderStartPaddingDp = notificationSpacing.dayHeaderStartPaddingDp
                .coerceIn(NotificationSpacing.RANGE_DP),
            dayHeaderToEventSpacingDp = notificationSpacing.dayHeaderToEventSpacingDp
                .coerceIn(NotificationSpacing.RANGE_DP),
            betweenEventsSpacingDp = notificationSpacing.betweenEventsSpacingDp
                .coerceIn(NotificationSpacing.RANGE_DP),
            betweenDayHeadersSpacingDp = notificationSpacing.betweenDayHeadersSpacingDp
                .coerceIn(NotificationSpacing.RANGE_DP),
            timeToTitleSpacingDp = notificationSpacing.timeToTitleSpacingDp
                .coerceIn(NotificationSpacing.RANGE_DP),
        ),
        isNotificationPinned = isNotificationPinned,
        isImminentLiveNotificationEnabled = isImminentLiveNotificationEnabled,
        collapsedHiddenItemTypes = collapsedHiddenItemTypes.mapNotNull(::hiddenItemTypeFromName).toSet(),
        expandedHiddenItemTypes = expandedHiddenItemTypes.mapNotNull(::hiddenItemTypeFromName).toSet(),
        keywordHideRules = keywordHideRules,
        dayHeaderFormatPattern = dayHeaderFormatPattern,
        notificationUpdateIntervalMinutes = notificationUpdateIntervalMinutes
            .coerceIn(UserPreferences.NOTIFICATION_UPDATE_INTERVAL_RANGE_MINUTES),
    )

/** 파일의 감춤 항목 이름을 enum으로 되돌린다. 낯선 이름(미래 버전)은 버린다 — 저장소 읽기 정책과 같다. */
private fun hiddenItemTypeFromName(itemTypeName: String): HiddenItemType? =
    HiddenItemType.entries.firstOrNull { it.name == itemTypeName }
