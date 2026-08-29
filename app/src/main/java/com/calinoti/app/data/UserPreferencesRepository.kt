package com.calinoti.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 알림에서 감출 종일 일정의 상태. 네 상태는 서로 겹치지 않고(완전분할) 종일 일정 하나를
 * 정확히 하나로 분류하므로, 상태별 감춤 토글은 서로 간섭하지 않는다. 시간 있는 일정은
 * 대상이 아니다. 항목을 늘릴 때는 분류 규칙(AgendaRemoteViewsFactory.findHiddenItemTypeOf)과
 * 문자열 2개를 함께 맞춰야 완전분할이 깨지지 않는다.
 */
enum class HiddenItemType {
    /** 오늘(시스템 표준 시간대 기준) 시작한 종일 일정. 여러 날 일정도 포함된다. */
    ALL_DAY_STARTED_TODAY,

    /** 이전에 시작해 아직 이어지는 종일 일정. */
    ALL_DAY_IN_PROGRESS,

    /** 내일 이후 시작하는 종일 일정. */
    ALL_DAY_UPCOMING,

    /** 이미 끝난 종일 일정. 표시 창에 과거가 포함될 때만 목록에 나타난다. */
    ALL_DAY_FINISHED,
}

/** 아젠다 알림과 설정 화면이 공유하는 사용자 설정 묶음. */
data class UserPreferences(
    /**
     * 표시할 캘린더. null이면 모든 캘린더(초기 상태), 빈 집합이면 선택된 캘린더 없음,
     * 그 외는 해당 캘린더만 표시한다.
     */
    val selectedCalendarIds: Set<Long>?,
    /**
     * 표시 창의 양끝(일 단위, 오늘 기준 상대 오프셋). [windowStartDays]는 창의 시작,
     * [windowEndDays]는 끝으로 항상 [windowStartDays] 이하다. 예: -3 ~ 7이면
     * 3일 전부터 7일 뒤까지, -7 ~ -1이면 지난주만, 0 ~ 0이면 오늘 겹치는 일정만.
     */
    val windowStartDays: Int,
    val windowEndDays: Int,
    /** 알림에 표시할 최대 항목 수. 항상 1 이상이다. */
    val maxVisibleEntries: Int,
    /** 시간 있는 일정 제목의 글자 크기(sp). 시각·위치·날짜 헤더는 이보다 2sp 작게 표시된다. */
    val notificationTextSizeSp: Int,
    /** 종일 일정 제목의 글자 크기(sp). [notificationTextSizeSp]와 독립적으로 조절한다. */
    val allDayEventTextSizeSp: Int,
    /**
     * 일정 항목을 눌렀을 때 열 앱의 패키지 이름. 빈 문자열이면 미지정(시스템이 처리 앱을
     * 고름), Calinoti 자신의 패키지 이름이면 Calinoti를 연다.
     */
    val eventClickTargetPackageName: String,
    /**
     * 알림에서 일정 항목 외의 공간을 눌렀을 때 열 앱의 패키지 이름.
     * [eventClickTargetPackageName]과 같은 저장 규칙을 따른다.
     */
    val notificationClickTargetPackageName: String,
    val notificationSpacing: NotificationSpacing,
    /** 알림 고정. 켜면 스와이프로 밀어도 dismiss를 감지해 즉시 다시 게시한다. */
    val isNotificationPinned: Boolean,
    /** 접힌(닫힌) 알림에서 감출 항목. 빈 집합이면 아무것도 감추지 않는다. */
    val collapsedHiddenItemTypes: Set<HiddenItemType>,
    /**
     * 펼친(열린) 알림에서 감출 항목. [collapsedHiddenItemTypes]와 독립적이다 —
     * 접힌 알림에 적용한 규칙을 펼친 알림이 따라오지 않는다.
     */
    val expandedHiddenItemTypes: Set<HiddenItemType>,
    /**
     * 날짜 헤더의 표시 형식(DateTimeFormatter 패턴 문법). 예: "MM.dd (E)" → "08.29 (금)".
     * 저장은 설정 화면에서만 일어나며 유효성 검사를 거치므로 항상 유효한 패턴이다.
     */
    val dayHeaderFormatPattern: String,
    /**
     * 안전망 알림 갱신 주기(분). 일정 시작·종료와 자정 사이에 이 주기로 알림을 다시 그려
     * 프로세스가 죽어 있는 동안 다른 앱에서 바꾼 일정도 따라잡는다. 짧으면 반응이 빠르고,
     * 길면 알람·갱신이 줄어 배터리를 아낀다.
     */
    val notificationUpdateIntervalMinutes: Int,
) {
    companion object {
        // 이보다 작으면 글자가 눈에 들어오지 않고, 크면 알림 창 높이를 넘친다.
        const val NOTIFICATION_TEXT_SIZE_MIN_SP = 8
        const val NOTIFICATION_TEXT_SIZE_MAX_SP = 32

        /** 날짜 헤더 형식의 기본 패턴. 초기 버전부터 쓰던 표시("08.29, 금요일")다. */
        const val DEFAULT_DAY_HEADER_FORMAT_PATTERN = "MM.dd, EEEE"

        // 안전망 갱신 주기의 조절 범위(분). 10분은 활성 사용 중에도 의미 있는 최솟값이다.
        // Doze(절전)에서는 allow-while-idle 알람이 앱당 15분 간격으로 스로틀되므로 그보다
        // 짧은 주기를 정해도 절전 중엔 15분 간격으로 늘어진다.
        const val NOTIFICATION_UPDATE_INTERVAL_MIN_MINUTES = 10
        const val NOTIFICATION_UPDATE_INTERVAL_MAX_MINUTES = 1440

        /** 알림 갱신 주기의 조절 범위. 설정 화면의 입력 검증이 이를 참조한다. */
        val NOTIFICATION_UPDATE_INTERVAL_RANGE_MINUTES =
            NOTIFICATION_UPDATE_INTERVAL_MIN_MINUTES..NOTIFICATION_UPDATE_INTERVAL_MAX_MINUTES

        /** 글자 크기 슬라이더의 조절 범위. 단일 객체로 둬 슬라이더가 이를 안정적으로 참조한다. */
        val NOTIFICATION_TEXT_SIZE_RANGE_SP =
            NOTIFICATION_TEXT_SIZE_MIN_SP..NOTIFICATION_TEXT_SIZE_MAX_SP

        /** 클릭 동작 미지정을 뜻하는 저장값. 빈 문자열이면 시스템이 처리 앱을 고른다. */
        const val UNSPECIFIED_CLICK_TARGET_PACKAGE_NAME = ""

        val DEFAULTS = UserPreferences(
            selectedCalendarIds = null,
            windowStartDays = 0,
            windowEndDays = 7,
            maxVisibleEntries = 10,
            notificationTextSizeSp = 12,
            allDayEventTextSizeSp = 10,
            eventClickTargetPackageName = UNSPECIFIED_CLICK_TARGET_PACKAGE_NAME,
            notificationClickTargetPackageName = UNSPECIFIED_CLICK_TARGET_PACKAGE_NAME,
            notificationSpacing = NotificationSpacing.DEFAULTS,
            isNotificationPinned = true,
            collapsedHiddenItemTypes = emptySet(),
            expandedHiddenItemTypes = emptySet(),
            dayHeaderFormatPattern = DEFAULT_DAY_HEADER_FORMAT_PATTERN,
            notificationUpdateIntervalMinutes = 360,
        )
    }
}

/**
 * 캘린더 선택 토글의 상태 전이를 계산한다.
 * [selectedCalendarIds]가 null(모든 캘린더)이면 전체 선택에서 시작하고, 기기에서 사라진
 * 캘린더 ID는 결과에서 버린다. 마지막 캘린더를 끄면 빈 집합(선택 없음)을 그대로 반환한다.
 */
fun toggledCalendarSelection(
    selectedCalendarIds: Set<Long>?,
    calendarId: Long,
    isChecked: Boolean,
    allCalendarIds: Set<Long>,
): Set<Long> {
    val currentlySelectedIds = (selectedCalendarIds ?: allCalendarIds).intersect(allCalendarIds)
    return if (isChecked) currentlySelectedIds + calendarId else currentlySelectedIds - calendarId
}

/** 선택된 캘린더 ID 집합을 한 문자열로 저장할 때의 구분자. */
private const val SELECTED_CALENDAR_IDS_SEPARATOR = ","

// 선택 집합은 쉼표 목록으로 직렬화한다. 키가 없으면 null(모든 캘린더), 빈 집합은 ""(선택 없음)이다.
// 참고: v1.2.1까지 ""는 "모든 캘린더"를 뜻했지만 초기 단계라 마이그레이션 없이 새 해석만 쓴다.
private val SELECTED_CALENDAR_IDS_KEY = stringPreferencesKey("selected_calendar_ids")
// 참고: v1.2.8까지 단일 days_to_look_ahead 키를 썼다. 범위 모델로 바뀌며 마이그레이션 없이
// 새 키로 갈아탔다(초기 단계라 기존 저장값은 버려도 이롭지 않다).
private val WINDOW_START_DAYS_KEY = intPreferencesKey("window_start_days")
private val WINDOW_END_DAYS_KEY = intPreferencesKey("window_end_days")
private val MAX_VISIBLE_ENTRIES_KEY = intPreferencesKey("max_visible_entries")
private val NOTIFICATION_TEXT_SIZE_KEY = intPreferencesKey("notification_text_size_sp")
private val ALL_DAY_EVENT_TEXT_SIZE_KEY = intPreferencesKey("all_day_event_text_size_sp")
// 참고: 이전 버전까지 notification_click_action, calendar_app_package_name 키를 썼다. 클릭 동작이
// 일정 클릭·알림 클릭 두 설정으로 나뉘며 마이그레이션 없이 폐기했다(초기 단계라 기존 저장값은
// 버려도 이롭지 않다).
private val EVENT_CLICK_TARGET_PACKAGE_NAME_KEY =
    stringPreferencesKey("event_click_target_package_name")
private val NOTIFICATION_CLICK_TARGET_PACKAGE_NAME_KEY =
    stringPreferencesKey("notification_click_target_package_name")
private val NOTIFICATION_PINNED_KEY = booleanPreferencesKey("notification_pinned")
// 감춤 항목은 enum name의 집합이다. 낯선 이름(미래 버전이 남긴 값)은 읽을 때 버린다.
// 접힌 알림과 펼친 알림이 각자의 집합을 저장한다 — 두 감춤 규칙은 서로 독립이다.
// 참고: v1.2.260828까지 기간 기반(하루/여러 날)의 collapsed_hidden_item_types 키를 썼다.
//   상태 기반(오늘 시작/진행 중/예정/종료됨) 모델로 바뀌며 마이그레이션 없이 폐기했다
//   (초기 단계라 기존 저장값은 버려도 이롭지 않다).
// 참고: v1.2.260829까지 hidden_item_types 집합 하나와 hidden_items_apply_to_expanded
//   불리언(펼친 알림에 접힌 규칙을 통째로 적용)으로 감춤을 관리했다. 항목별 독립
//   제어 모델로 바뀌며 마이그레이션 없이 폐기했다(초기 단계라 기존 저장값은 버려도
//   이롭지 않다).
private val HIDDEN_ITEM_TYPES_COLLAPSED_KEY =
    stringSetPreferencesKey("hidden_item_types_collapsed")
private val HIDDEN_ITEM_TYPES_EXPANDED_KEY =
    stringSetPreferencesKey("hidden_item_types_expanded")
private val DAY_HEADER_FORMAT_PATTERN_KEY = stringPreferencesKey("day_header_format_pattern")
private val NOTIFICATION_UPDATE_INTERVAL_MINUTES_KEY =
    intPreferencesKey("notification_update_interval_minutes")
private val DAY_HEADER_START_PADDING_KEY = intPreferencesKey("day_header_start_padding_dp")
private val DAY_HEADER_TO_EVENT_SPACING_KEY = intPreferencesKey("day_header_to_event_spacing_dp")
private val BETWEEN_EVENTS_SPACING_KEY = intPreferencesKey("between_events_spacing_dp")
private val BETWEEN_DAY_HEADERS_SPACING_KEY = intPreferencesKey("between_day_headers_spacing_dp")
private val TIME_TO_TITLE_SPACING_KEY = intPreferencesKey("time_to_title_spacing_dp")

private fun Preferences.parseSelectedCalendarIds(): Set<Long>? =
    this[SELECTED_CALENDAR_IDS_KEY]
        ?.split(SELECTED_CALENDAR_IDS_SEPARATOR)
        ?.filter { it.isNotBlank() }
        // 손상된 토큰 하나가 앱 시작을 죽이지 않게 잘못된 토큰만 건너뛴다.
        ?.mapNotNull { it.toLongOrNull() }
        ?.toSet()

/** 저장된 감춤 항목 이름 집합을 enum 집합으로 되돌린다. 낯선 이름은 건너뛴다. */
private fun Preferences.parseHiddenItemTypes(
    hiddenItemTypesKey: Preferences.Key<Set<String>>,
): Set<HiddenItemType> =
    this[hiddenItemTypesKey]
        ?.mapNotNull { storedName ->
            // 저장값이 미래 버전에서 오래된 이름이어도 나머지 항목은 살려 쓴다.
            HiddenItemType.entries.firstOrNull { it.name == storedName }
        }
        ?.toSet()
        ?: emptySet()

/** 여백 키 조회. 없으면 기본값, 있으면 조절 범위 밖의 오래된 저장값도 범위 안으로 끌어온다. */
private fun Preferences.readSpacingDp(key: Preferences.Key<Int>, defaultDp: Int): Int =
    this[key]?.coerceIn(NotificationSpacing.RANGE_DP) ?: defaultDp

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
)

/** 사용자 설정을 DataStore에 저장·조회한다. */
class UserPreferencesRepository(private val context: Context) {

    // 일시적 읽기 오류(IOException)에 빈 설정을 내보내 수집자가 죽지 않게 한다 (DataStore 공식 패턴).
    // 그 외 오류는 그대로 던져 실제 손상이 숨겨지지 않게 한다.
    val userPreferences: Flow<UserPreferences> =
        context.userPreferencesDataStore.data
            .catch { storageError ->
                if (storageError is IOException) emit(emptyPreferences()) else throw storageError
            }
            .map { storedPreferences ->
                UserPreferences(
                    selectedCalendarIds = storedPreferences.parseSelectedCalendarIds(),
                    windowStartDays =
                        storedPreferences[WINDOW_START_DAYS_KEY]
                            ?: UserPreferences.DEFAULTS.windowStartDays,
                    windowEndDays =
                        storedPreferences[WINDOW_END_DAYS_KEY]
                            ?: UserPreferences.DEFAULTS.windowEndDays,
                    maxVisibleEntries =
                        storedPreferences[MAX_VISIBLE_ENTRIES_KEY]
                            ?: UserPreferences.DEFAULTS.maxVisibleEntries,
                    notificationTextSizeSp =
                        storedPreferences[NOTIFICATION_TEXT_SIZE_KEY]
                            ?: UserPreferences.DEFAULTS.notificationTextSizeSp,
                    allDayEventTextSizeSp =
                        storedPreferences[ALL_DAY_EVENT_TEXT_SIZE_KEY]
                            ?: UserPreferences.DEFAULTS.allDayEventTextSizeSp,
                    eventClickTargetPackageName =
                        storedPreferences[EVENT_CLICK_TARGET_PACKAGE_NAME_KEY]
                            ?: UserPreferences.DEFAULTS.eventClickTargetPackageName,
                    notificationClickTargetPackageName =
                        storedPreferences[NOTIFICATION_CLICK_TARGET_PACKAGE_NAME_KEY]
                            ?: UserPreferences.DEFAULTS.notificationClickTargetPackageName,
                    notificationSpacing =
                        NotificationSpacing.DEFAULTS.copy(
                            dayHeaderStartPaddingDp = storedPreferences.readSpacingDp(
                                DAY_HEADER_START_PADDING_KEY,
                                NotificationSpacing.DEFAULTS.dayHeaderStartPaddingDp,
                            ),
                            dayHeaderToEventSpacingDp = storedPreferences.readSpacingDp(
                                DAY_HEADER_TO_EVENT_SPACING_KEY,
                                NotificationSpacing.DEFAULTS.dayHeaderToEventSpacingDp,
                            ),
                            betweenEventsSpacingDp = storedPreferences.readSpacingDp(
                                BETWEEN_EVENTS_SPACING_KEY,
                                NotificationSpacing.DEFAULTS.betweenEventsSpacingDp,
                            ),
                            betweenDayHeadersSpacingDp = storedPreferences.readSpacingDp(
                                BETWEEN_DAY_HEADERS_SPACING_KEY,
                                NotificationSpacing.DEFAULTS.betweenDayHeadersSpacingDp,
                            ),
                            timeToTitleSpacingDp = storedPreferences.readSpacingDp(
                                TIME_TO_TITLE_SPACING_KEY,
                                NotificationSpacing.DEFAULTS.timeToTitleSpacingDp,
                            ),
                        ),
                    isNotificationPinned =
                        storedPreferences[NOTIFICATION_PINNED_KEY]
                            ?: UserPreferences.DEFAULTS.isNotificationPinned,
                    collapsedHiddenItemTypes =
                        storedPreferences.parseHiddenItemTypes(HIDDEN_ITEM_TYPES_COLLAPSED_KEY),
                    expandedHiddenItemTypes =
                        storedPreferences.parseHiddenItemTypes(HIDDEN_ITEM_TYPES_EXPANDED_KEY),
                    dayHeaderFormatPattern =
                        storedPreferences[DAY_HEADER_FORMAT_PATTERN_KEY]
                            ?: UserPreferences.DEFAULTS.dayHeaderFormatPattern,
                    // 저장값이 조절 범위 밖이어도 여백(readSpacingDp)처럼 범위 안으로 끌어온다.
                    notificationUpdateIntervalMinutes =
                        storedPreferences[NOTIFICATION_UPDATE_INTERVAL_MINUTES_KEY]
                            ?.coerceIn(UserPreferences.NOTIFICATION_UPDATE_INTERVAL_RANGE_MINUTES)
                            ?: UserPreferences.DEFAULTS.notificationUpdateIntervalMinutes,
                )
            }

    /**
     * 캘린더 선택 토글을 저장값 기준으로 원자적으로 반영한다.
     * UI의 상태 스냅샷이 늦더라도 연속 토글이 서로를 덮어쓰지 않는다.
     */
    suspend fun toggleCalendarSelection(
        calendarId: Long,
        isChecked: Boolean,
        allCalendarIds: Set<Long>,
    ) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            val nextSelectedIds = toggledCalendarSelection(
                selectedCalendarIds = storedPreferences.parseSelectedCalendarIds(),
                calendarId = calendarId,
                isChecked = isChecked,
                allCalendarIds = allCalendarIds,
            )
            storedPreferences[SELECTED_CALENDAR_IDS_KEY] =
                nextSelectedIds.joinToString(SELECTED_CALENDAR_IDS_SEPARATOR)
        }
    }

    /**
     * 접힌 알림 감춤 항목 토글을 저장값 기준으로 원자적으로 반영한다.
     * 캘린더 선택 토글과 같은 이유로 UI의 상태 스냅샷이 늦더라도 연속 토글이
     * 서로를 덮어쓰지 않는다.
     */
    suspend fun toggleCollapsedHiddenItemType(
        itemType: HiddenItemType,
        isChecked: Boolean,
    ) {
        toggleHiddenItemTypes(HIDDEN_ITEM_TYPES_COLLAPSED_KEY, itemType, isChecked)
    }

    /** 펼친 알림 감춤 항목 토글. [toggleCollapsedHiddenItemType]과 같은 규칙으로 반영한다. */
    suspend fun toggleExpandedHiddenItemType(
        itemType: HiddenItemType,
        isChecked: Boolean,
    ) {
        toggleHiddenItemTypes(HIDDEN_ITEM_TYPES_EXPANDED_KEY, itemType, isChecked)
    }

    private suspend fun toggleHiddenItemTypes(
        hiddenItemTypesKey: Preferences.Key<Set<String>>,
        itemType: HiddenItemType,
        isChecked: Boolean,
    ) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            val currentHiddenItemTypes = storedPreferences.parseHiddenItemTypes(hiddenItemTypesKey)
            val nextHiddenItemTypes =
                if (isChecked) currentHiddenItemTypes + itemType else currentHiddenItemTypes - itemType
            storedPreferences[hiddenItemTypesKey] =
                nextHiddenItemTypes.map(HiddenItemType::name).toSet()
        }
    }

    suspend fun updateWindowStartDays(windowStartDays: Int) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[WINDOW_START_DAYS_KEY] = windowStartDays
        }
    }

    suspend fun updateWindowEndDays(windowEndDays: Int) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[WINDOW_END_DAYS_KEY] = windowEndDays
        }
    }

    suspend fun updateMaxVisibleEntries(maxVisibleEntries: Int) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[MAX_VISIBLE_ENTRIES_KEY] = maxVisibleEntries
        }
    }

    suspend fun updateNotificationTextSize(textSizeSp: Int) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[NOTIFICATION_TEXT_SIZE_KEY] = textSizeSp
        }
    }

    suspend fun updateAllDayEventTextSize(textSizeSp: Int) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[ALL_DAY_EVENT_TEXT_SIZE_KEY] = textSizeSp
        }
    }

    suspend fun updateEventClickTargetPackageName(packageName: String) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[EVENT_CLICK_TARGET_PACKAGE_NAME_KEY] = packageName
        }
    }

    suspend fun updateNotificationClickTargetPackageName(packageName: String) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[NOTIFICATION_CLICK_TARGET_PACKAGE_NAME_KEY] = packageName
        }
    }

    suspend fun updateNotificationPinned(isPinned: Boolean) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[NOTIFICATION_PINNED_KEY] = isPinned
        }
    }

    suspend fun updateDayHeaderFormatPattern(formatPattern: String) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[DAY_HEADER_FORMAT_PATTERN_KEY] = formatPattern
        }
    }

    suspend fun updateNotificationUpdateIntervalMinutes(intervalMinutes: Int) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[NOTIFICATION_UPDATE_INTERVAL_MINUTES_KEY] = intervalMinutes
        }
    }

    /** 알림 여백 전체를 한 번의 쓰기에 반영한다. UI는 변경할 한 필드만 바꿔 넘긴다. */
    suspend fun updateNotificationSpacing(spacing: NotificationSpacing) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[DAY_HEADER_START_PADDING_KEY] = spacing.dayHeaderStartPaddingDp
            storedPreferences[DAY_HEADER_TO_EVENT_SPACING_KEY] = spacing.dayHeaderToEventSpacingDp
            storedPreferences[BETWEEN_EVENTS_SPACING_KEY] = spacing.betweenEventsSpacingDp
            storedPreferences[BETWEEN_DAY_HEADERS_SPACING_KEY] = spacing.betweenDayHeadersSpacingDp
            storedPreferences[TIME_TO_TITLE_SPACING_KEY] = spacing.timeToTitleSpacingDp
        }
    }
}
