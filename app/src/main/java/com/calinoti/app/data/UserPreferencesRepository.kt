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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * 알림에서 감출 종일 일정의 상태. 네 상태는 서로 겹치지 않고(완전분할) 종일 일정 하나를
 * 정확히 하나로 분류하므로, 상태별 감춤 토글은 서로 간섭하지 않는다. 시간 있는 일정은
 * 대상이 아니다. 항목을 늘릴 때는 분류 규칙(NotificationViewsFactory.findHiddenItemTypeOf)과
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

/** 일정 알림과 설정 화면이 공유하는 사용자 설정 묶음. */
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
    /** 임박 일정 실시간 알림. 시작 1시간 전부터 별도 카운트다운 알림을 게시한다. */
    val isImminentLiveNotificationEnabled: Boolean,
    /** 접힌(닫힌) 알림에서 감출 항목. 빈 집합이면 아무것도 감추지 않는다. */
    val collapsedHiddenItemTypes: Set<HiddenItemType>,
    /**
     * 펼친(열린) 알림에서 감출 항목. [collapsedHiddenItemTypes]와 독립적이다 —
     * 접힌 알림에 적용한 규칙을 펼친 알림이 따라오지 않는다.
     */
    val expandedHiddenItemTypes: Set<HiddenItemType>,
    /**
     * 제목·캘린더명 단어로 일정을 감추는 규칙 목록. 규칙 안 조건은 모두(AND) 충족돼야 하고
     * 규칙 간은 OR다. 매칭 판정은 [KeywordHideRule]을, 접힌·펼친 적용은
     * [hidesEventInCollapsedView]·[hidesEventInExpandedView]·[hidesEventAnywhere]를 본다.
     */
    val keywordHideRules: List<KeywordHideRule>,
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

        /** 날짜 헤더 형식의 기본 패턴("08.29 (금)"). 설정 화면 프리셋의 "MM.dd (E)"와 같은 값이다. */
        const val DEFAULT_DAY_HEADER_FORMAT_PATTERN = "MM.dd (E)"

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

        // 필드 기본값은 사용자가 설정 화면에서 고른 값이다 (2026-09 설정 파일 기준).
        val DEFAULTS = UserPreferences(
            selectedCalendarIds = null,
            windowStartDays = 0,
            windowEndDays = 90,
            maxVisibleEntries = 100,
            notificationTextSizeSp = 8,
            allDayEventTextSizeSp = 8,
            eventClickTargetPackageName = UNSPECIFIED_CLICK_TARGET_PACKAGE_NAME,
            notificationClickTargetPackageName = UNSPECIFIED_CLICK_TARGET_PACKAGE_NAME,
            notificationSpacing = NotificationSpacing.DEFAULTS,
            isNotificationPinned = true,
            isImminentLiveNotificationEnabled = false,
            collapsedHiddenItemTypes = setOf(
                HiddenItemType.ALL_DAY_STARTED_TODAY,
                HiddenItemType.ALL_DAY_IN_PROGRESS,
                HiddenItemType.ALL_DAY_UPCOMING,
                HiddenItemType.ALL_DAY_FINISHED,
            ),
            expandedHiddenItemTypes = setOf(
                HiddenItemType.ALL_DAY_UPCOMING,
                HiddenItemType.ALL_DAY_FINISHED,
            ),
            keywordHideRules = emptyList(),
            dayHeaderFormatPattern = DEFAULT_DAY_HEADER_FORMAT_PATTERN,
            notificationUpdateIntervalMinutes = 10,
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
private val IMMINENT_LIVE_NOTIFICATION_KEY = booleanPreferencesKey("imminent_live_notification_enabled")
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
// 키워드 감춤 규칙은 사용자 자유 텍스트(단어)를 담으므로 구분자 직렬화 대신 JSON으로 저장한다 —
// 단어에 구분자 문자가 들어오면 escaping이 필요해지기 때문이다. 확장 절차: 모델 필드 추가 후
// 수정 메서드는 반드시 editKeywordHideRules 한 경로로만 쓴다(원자성·ID 발급 단일화).
// 규칙 ID와 조건 ID는 전역 공간을 공유한다(findNextKeywordHideIdentifier) — 종별로 나눠
// 발급하면 ID가 겹쳐 Compose key·편집 매핑이 어긋난다. 낯선 JSON·손상은 규칙 전체를
// 버린다(빈 목록) — 이 저장소의 "낯선 저장값은 버린다" 정책과 같다.
private val KEYWORD_HIDE_RULES_KEY = stringPreferencesKey("keyword_hide_rules")
private val DAY_HEADER_FORMAT_PATTERN_KEY = stringPreferencesKey("day_header_format_pattern")
private val NOTIFICATION_UPDATE_INTERVAL_MINUTES_KEY =
    intPreferencesKey("notification_update_interval_minutes")
private val OUTER_VERTICAL_PADDING_KEY = intPreferencesKey("outer_vertical_padding_dp")
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

/**
 * 저장된 감춤 항목 이름 집합을 enum 집합으로 되돌린다. 낯선 이름은 건너뛴다. 키가 없으면
 * [defaultHiddenItemTypes](기본값)를 반환한다 — 감춤 집합의 기본값은 DEFAULTS가 단일 출처다.
 */
private fun Preferences.parseHiddenItemTypes(
    hiddenItemTypesKey: Preferences.Key<Set<String>>,
    defaultHiddenItemTypes: Set<HiddenItemType>,
): Set<HiddenItemType> =
    this[hiddenItemTypesKey]
        ?.mapNotNull { storedName ->
            // 저장값이 미래 버전에서 오래된 이름이어도 나머지 항목은 살려 쓴다.
            HiddenItemType.entries.firstOrNull { it.name == storedName }
        }
        ?.toSet()
        ?: defaultHiddenItemTypes

/** 여백 키 조회. 없으면 기본값, 있으면 조절 범위 밖의 오래된 저장값도 범위 안으로 끌어온다. */
private fun Preferences.readSpacingDp(key: Preferences.Key<Int>, defaultDp: Int): Int =
    this[key]?.coerceIn(NotificationSpacing.RANGE_DP) ?: defaultDp

/** 규칙 JSON 직렬화 형식의 단일 출처. 낯선 필드(미래 버전이 남긴 것)는 읽을 때 버린다. */
private val keywordHideRulesJsonFormat = Json { ignoreUnknownKeys = true }

/** 저장된 규칙 JSON을 규칙 목록으로 되돌린다. 키가 없거나 손상돼 있으면 빈 목록이다. */
private fun Preferences.parseKeywordHideRules(): List<KeywordHideRule> =
    this[KEYWORD_HIDE_RULES_KEY]
        ?.let { storedJson ->
            runCatching {
                keywordHideRulesJsonFormat.decodeFromString<List<KeywordHideRule>>(storedJson)
            }.getOrDefault(emptyList())
        }
        ?: emptyList()

/** 규칙 목록을 저장용 JSON으로 직렬화한다. 규칙 쓰기 경로 전부가 이 한 곳을 쓴다. */
private fun encodeKeywordHideRules(keywordHideRules: List<KeywordHideRule>): String =
    keywordHideRulesJsonFormat.encodeToString(keywordHideRules)

/**
 * 규칙 ID와 조건 ID가 공유하는 다음 식별자(전역 max + 1). 두 종의 ID가 겹치면 Compose
 * key 충돌·편집 매핑 오류가 나므로 한 공간에서 발급한다.
 */
private fun List<KeywordHideRule>.findNextKeywordHideIdentifier(): Long {
    val maxRuleIdentifier = maxOfOrNull { it.id } ?: 0L
    val maxConditionIdentifier =
        maxOfOrNull { rule -> rule.conditions.maxOfOrNull { it.id } ?: 0L } ?: 0L
    return maxOf(maxRuleIdentifier, maxConditionIdentifier) + 1
}

/**
 * 가져온 규칙의 ID를 새로 발급한다. 규칙과 조건이 한 공간을 공유하므로 발급 규칙
 * (findNextKeywordHideIdentifier)을 그대로 따른다 — 두 종의 ID가 겹치면 Compose key
 * 충돌·편집 매핑 오류가 난다. 규칙 목록을 통째로 교체하는 가져오기에서만 쓴다.
 */
private fun reassignKeywordHideIdentifiers(
    importedRules: List<KeywordHideRule>,
): List<KeywordHideRule> =
    importedRules.fold(emptyList()) { renumberedRules, importedRule ->
        val newRuleIdentifier = renumberedRules.findNextKeywordHideIdentifier()
        renumberedRules + KeywordHideRule(
            id = newRuleIdentifier,
            conditions = importedRule.conditions.mapIndexed { conditionIndex, importedCondition ->
                KeywordHideCondition(
                    id = newRuleIdentifier + 1 + conditionIndex,
                    keyword = importedCondition.keyword,
                )
            },
            isHiddenWhenCollapsed = importedRule.isHiddenWhenCollapsed,
            isHiddenWhenExpanded = importedRule.isHiddenWhenExpanded,
        )
    }

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
                            outerVerticalPaddingDp = storedPreferences.readSpacingDp(
                                OUTER_VERTICAL_PADDING_KEY,
                                NotificationSpacing.DEFAULTS.outerVerticalPaddingDp,
                            ),
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
                    isImminentLiveNotificationEnabled =
                        storedPreferences[IMMINENT_LIVE_NOTIFICATION_KEY]
                            ?: UserPreferences.DEFAULTS.isImminentLiveNotificationEnabled,
                    collapsedHiddenItemTypes = storedPreferences.parseHiddenItemTypes(
                        HIDDEN_ITEM_TYPES_COLLAPSED_KEY,
                        UserPreferences.DEFAULTS.collapsedHiddenItemTypes,
                    ),
                    expandedHiddenItemTypes = storedPreferences.parseHiddenItemTypes(
                        HIDDEN_ITEM_TYPES_EXPANDED_KEY,
                        UserPreferences.DEFAULTS.expandedHiddenItemTypes,
                    ),
                    keywordHideRules = storedPreferences.parseKeywordHideRules(),
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
        toggleHiddenItemTypes(
            hiddenItemTypesKey = HIDDEN_ITEM_TYPES_COLLAPSED_KEY,
            defaultHiddenItemTypes = UserPreferences.DEFAULTS.collapsedHiddenItemTypes,
            itemType = itemType,
            isChecked = isChecked,
        )
    }

    /** 펼친 알림 감춤 항목 토글. [toggleCollapsedHiddenItemType]과 같은 규칙으로 반영한다. */
    suspend fun toggleExpandedHiddenItemType(
        itemType: HiddenItemType,
        isChecked: Boolean,
    ) {
        toggleHiddenItemTypes(
            hiddenItemTypesKey = HIDDEN_ITEM_TYPES_EXPANDED_KEY,
            defaultHiddenItemTypes = UserPreferences.DEFAULTS.expandedHiddenItemTypes,
            itemType = itemType,
            isChecked = isChecked,
        )
    }

    private suspend fun toggleHiddenItemTypes(
        hiddenItemTypesKey: Preferences.Key<Set<String>>,
        defaultHiddenItemTypes: Set<HiddenItemType>,
        itemType: HiddenItemType,
        isChecked: Boolean,
    ) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            // 키가 없는(기본값 표시 중) 첫 토글도 UI가 본 값과 같은 기준으로 계산되게
            // 기본값을 출발점으로 쓴다 — 그렇지 않으면 기본값 항목이 체크돼 보이는데
            // 해제해도 집합에 없어 반영이 어긋난다.
            val currentHiddenItemTypes = storedPreferences.parseHiddenItemTypes(
                hiddenItemTypesKey,
                defaultHiddenItemTypes,
            )
            val nextHiddenItemTypes =
                if (isChecked) currentHiddenItemTypes + itemType else currentHiddenItemTypes - itemType
            storedPreferences[hiddenItemTypesKey] =
                nextHiddenItemTypes.map(HiddenItemType::name).toSet()
        }
    }

    /**
     * 키워드 감춤 규칙 전체를 저장값 기준으로 원자적으로 반영한다. 모든 규칙 수정 메서드가
     * 거는 유일한 쓰기 경로다 — 키 입력마다 저장돼도 연속 변경이 서로를 덮어쓰지 않는다.
     */
    private suspend fun editKeywordHideRules(
        transform: (currentRules: List<KeywordHideRule>) -> List<KeywordHideRule>,
    ) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[KEYWORD_HIDE_RULES_KEY] =
                encodeKeywordHideRules(transform(storedPreferences.parseKeywordHideRules()))
        }
    }

    /** 새 감춤 규칙을 추가한다. 빈 단어 조건 하나를 시드한다 — 빈 단어는 매칭되지 않아 안전하다. */
    suspend fun addKeywordHideRule() = editKeywordHideRules { currentRules ->
        // 새 규칙과 그 시드 조건에 ID를 이어서 발급한다 — 두 종은 한 공간을 공유하므로
        // 전역 max+1과 max+2로 서로 겹치지 않게 뽑는다.
        val newRuleIdentifier = currentRules.findNextKeywordHideIdentifier()
        currentRules + KeywordHideRule(
            id = newRuleIdentifier,
            conditions = listOf(
                KeywordHideCondition(id = newRuleIdentifier + 1, keyword = ""),
            ),
            isHiddenWhenCollapsed = false,
            isHiddenWhenExpanded = false,
        )
    }

    /** 감춤 규칙 하나를 지운다. 조건은 규칙에 중첩돼 저장되므로 함께 사라진다. */
    suspend fun removeKeywordHideRule(ruleId: Long) = editKeywordHideRules { currentRules ->
        currentRules.filterNot { it.id == ruleId }
    }

    /** 규칙의 접힌 알림 적용 토글을 저장값 기준으로 원자적으로 반영한다. */
    suspend fun toggleKeywordHideRuleCollapsed(ruleId: Long, isChecked: Boolean) =
        toggleKeywordHideRule(ruleId, isCollapsedToggle = true, isChecked = isChecked)

    /** 규칙의 펼친 알림 적용 토글. [toggleKeywordHideRuleCollapsed]와 같은 규칙으로 반영한다. */
    suspend fun toggleKeywordHideRuleExpanded(ruleId: Long, isChecked: Boolean) =
        toggleKeywordHideRule(ruleId, isCollapsedToggle = false, isChecked = isChecked)

    private suspend fun toggleKeywordHideRule(
        ruleId: Long,
        isCollapsedToggle: Boolean,
        isChecked: Boolean,
    ) = editKeywordHideRules { currentRules ->
        currentRules.map { rule ->
            when {
                rule.id != ruleId -> rule
                isCollapsedToggle -> rule.copy(isHiddenWhenCollapsed = isChecked)
                else -> rule.copy(isHiddenWhenExpanded = isChecked)
            }
        }
    }

    /** 규칙에 빈 단어 조건을 하나 추가한다. */
    suspend fun addKeywordHideRuleCondition(ruleId: Long) = editKeywordHideRules { currentRules ->
        val newConditionIdentifier = currentRules.findNextKeywordHideIdentifier()
        currentRules.map { rule ->
            if (rule.id != ruleId) {
                rule
            } else {
                rule.copy(
                    conditions =
                        rule.conditions + KeywordHideCondition(id = newConditionIdentifier, keyword = ""),
                )
            }
        }
    }

    /** 규칙에서 조건 하나를 지운다. */
    suspend fun removeKeywordHideRuleCondition(ruleId: Long, conditionId: Long) =
        editKeywordHideRules { currentRules ->
            currentRules.map { rule ->
                if (rule.id != ruleId) {
                    rule
                } else {
                    rule.copy(
                        conditions = rule.conditions.filterNot { it.id == conditionId },
                    )
                }
            }
        }

    /**
     * 조건의 단어를 바꾼다. 입력 중 임시값(공백 포함)도 그대로 저장해 라운드트립이
     * 무손실이다 — trim은 매칭 판정 시점(KeywordHideRule.matchesEvent)에만 한다.
     */
    suspend fun updateKeywordHideRuleConditionKeyword(
        ruleId: Long,
        conditionId: Long,
        keyword: String,
    ) = editKeywordHideRules { currentRules ->
        currentRules.map { rule ->
            if (rule.id != ruleId) {
                rule
            } else {
                rule.copy(
                    conditions = rule.conditions.map { condition ->
                        if (condition.id != conditionId) {
                            condition
                        } else {
                            condition.copy(keyword = keyword)
                        }
                    },
                )
            }
        }
    }

    suspend fun updateWindowStartDays(windowStartDays: Int) =
        updateStoredValue(WINDOW_START_DAYS_KEY, windowStartDays)

    suspend fun updateWindowEndDays(windowEndDays: Int) =
        updateStoredValue(WINDOW_END_DAYS_KEY, windowEndDays)

    suspend fun updateMaxVisibleEntries(maxVisibleEntries: Int) =
        updateStoredValue(MAX_VISIBLE_ENTRIES_KEY, maxVisibleEntries)

    suspend fun updateNotificationTextSize(textSizeSp: Int) =
        updateStoredValue(NOTIFICATION_TEXT_SIZE_KEY, textSizeSp)

    suspend fun updateAllDayEventTextSize(textSizeSp: Int) =
        updateStoredValue(ALL_DAY_EVENT_TEXT_SIZE_KEY, textSizeSp)

    suspend fun updateEventClickTargetPackageName(packageName: String) =
        updateStoredValue(EVENT_CLICK_TARGET_PACKAGE_NAME_KEY, packageName)

    suspend fun updateNotificationClickTargetPackageName(packageName: String) =
        updateStoredValue(NOTIFICATION_CLICK_TARGET_PACKAGE_NAME_KEY, packageName)

    suspend fun updateNotificationPinned(isPinned: Boolean) =
        updateStoredValue(NOTIFICATION_PINNED_KEY, isPinned)

    suspend fun updateImminentLiveNotificationEnabled(isEnabled: Boolean) =
        updateStoredValue(IMMINENT_LIVE_NOTIFICATION_KEY, isEnabled)

    suspend fun updateDayHeaderFormatPattern(formatPattern: String) =
        updateStoredValue(DAY_HEADER_FORMAT_PATTERN_KEY, formatPattern)

    suspend fun updateNotificationUpdateIntervalMinutes(intervalMinutes: Int) =
        updateStoredValue(NOTIFICATION_UPDATE_INTERVAL_MINUTES_KEY, intervalMinutes)

    /** 단일 키 설정값 하나를 저장한다. 위 갱신 메서드들이 공유하는 쓰기 한 줄이다. */
    private suspend fun <T> updateStoredValue(key: Preferences.Key<T>, value: T) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[key] = value
        }
    }

    /** 알림 여백 전체를 한 번의 쓰기에 반영한다. UI는 변경할 한 필드만 바꿔 넘긴다. */
    suspend fun updateNotificationSpacing(spacing: NotificationSpacing) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[OUTER_VERTICAL_PADDING_KEY] = spacing.outerVerticalPaddingDp
            storedPreferences[DAY_HEADER_START_PADDING_KEY] = spacing.dayHeaderStartPaddingDp
            storedPreferences[DAY_HEADER_TO_EVENT_SPACING_KEY] = spacing.dayHeaderToEventSpacingDp
            storedPreferences[BETWEEN_EVENTS_SPACING_KEY] = spacing.betweenEventsSpacingDp
            storedPreferences[BETWEEN_DAY_HEADERS_SPACING_KEY] = spacing.betweenDayHeadersSpacingDp
            storedPreferences[TIME_TO_TITLE_SPACING_KEY] = spacing.timeToTitleSpacingDp
        }
    }

    /**
     * 모든 설정을 한 번의 쓰기로 교체한다. 설정 파일 가져오기(스냅샷 복원)에서만 쓴다.
     * 규칙 JSON을 여기서 직접 쓰는 것은 editKeywordHideRules의 유일한 예외다 — 규칙과
     * 나머지 설정이 같은 트랜잭션에 담겨야 복원이 원자적이고, ID 재발급도 이 한 곳에서 끝난다.
     */
    suspend fun importPreferences(preferences: UserPreferences) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            // 캘린더 선택은 "키가 없으면 모든 캘린더"라서 null일 때는 키를 지운다.
            // 빈 집합은 ""(선택 없음)으로 쓴다 — 두 상태의 구분을 저장까지 그대로 옮긴다.
            val importedCalendarIds = preferences.selectedCalendarIds
            if (importedCalendarIds == null) {
                storedPreferences.remove(SELECTED_CALENDAR_IDS_KEY)
            } else {
                storedPreferences[SELECTED_CALENDAR_IDS_KEY] =
                    importedCalendarIds.joinToString(SELECTED_CALENDAR_IDS_SEPARATOR)
            }
            storedPreferences[WINDOW_START_DAYS_KEY] = preferences.windowStartDays
            storedPreferences[WINDOW_END_DAYS_KEY] = preferences.windowEndDays
            storedPreferences[MAX_VISIBLE_ENTRIES_KEY] = preferences.maxVisibleEntries
            storedPreferences[NOTIFICATION_TEXT_SIZE_KEY] = preferences.notificationTextSizeSp
            storedPreferences[ALL_DAY_EVENT_TEXT_SIZE_KEY] = preferences.allDayEventTextSizeSp
            storedPreferences[EVENT_CLICK_TARGET_PACKAGE_NAME_KEY] =
                preferences.eventClickTargetPackageName
            storedPreferences[NOTIFICATION_CLICK_TARGET_PACKAGE_NAME_KEY] =
                preferences.notificationClickTargetPackageName
            storedPreferences[NOTIFICATION_PINNED_KEY] = preferences.isNotificationPinned
            storedPreferences[IMMINENT_LIVE_NOTIFICATION_KEY] =
                preferences.isImminentLiveNotificationEnabled
            storedPreferences[HIDDEN_ITEM_TYPES_COLLAPSED_KEY] =
                preferences.collapsedHiddenItemTypes.map(HiddenItemType::name).toSet()
            storedPreferences[HIDDEN_ITEM_TYPES_EXPANDED_KEY] =
                preferences.expandedHiddenItemTypes.map(HiddenItemType::name).toSet()
            storedPreferences[DAY_HEADER_FORMAT_PATTERN_KEY] = preferences.dayHeaderFormatPattern
            storedPreferences[NOTIFICATION_UPDATE_INTERVAL_MINUTES_KEY] =
                preferences.notificationUpdateIntervalMinutes
            storedPreferences[OUTER_VERTICAL_PADDING_KEY] =
                preferences.notificationSpacing.outerVerticalPaddingDp
            storedPreferences[DAY_HEADER_START_PADDING_KEY] =
                preferences.notificationSpacing.dayHeaderStartPaddingDp
            storedPreferences[DAY_HEADER_TO_EVENT_SPACING_KEY] =
                preferences.notificationSpacing.dayHeaderToEventSpacingDp
            storedPreferences[BETWEEN_EVENTS_SPACING_KEY] =
                preferences.notificationSpacing.betweenEventsSpacingDp
            storedPreferences[BETWEEN_DAY_HEADERS_SPACING_KEY] =
                preferences.notificationSpacing.betweenDayHeadersSpacingDp
            storedPreferences[TIME_TO_TITLE_SPACING_KEY] =
                preferences.notificationSpacing.timeToTitleSpacingDp
            storedPreferences[KEYWORD_HIDE_RULES_KEY] =
                encodeKeywordHideRules(reassignKeywordHideIdentifiers(preferences.keywordHideRules))
        }
    }
}
