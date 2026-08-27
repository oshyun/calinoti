package com.calinoti.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** 알림을 눌렀을 때 할 동작. */
enum class NotificationClickAction { OPEN_APP, CREATE_EVENT }

/** 아젠다 알림과 설정 화면이 공유하는 사용자 설정 묶음. */
data class UserPreferences(
    /**
     * 표시할 캘린더. null이면 모든 캘린더(초기 상태), 빈 집합이면 선택된 캘린더 없음,
     * 그 외는 해당 캘린더만 표시한다.
     */
    val selectedCalendarIds: Set<Long>?,
    val daysToLookAhead: Int,
    val maxVisibleEntries: Int,
    val notificationClickAction: NotificationClickAction,
    val notificationSpacing: NotificationSpacing,
) {
    companion object {
        val DAYS_TO_LOOK_AHEAD_CHOICES = listOf(3, 7, 14, 30)
        val MAX_VISIBLE_ENTRIES_CHOICES = listOf(5, 10, 15)

        val DEFAULTS = UserPreferences(
            selectedCalendarIds = null,
            daysToLookAhead = 7,
            maxVisibleEntries = 10,
            notificationClickAction = NotificationClickAction.OPEN_APP,
            notificationSpacing = NotificationSpacing.DEFAULTS,
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
private val DAYS_TO_LOOK_AHEAD_KEY = intPreferencesKey("days_to_look_ahead")
private val MAX_VISIBLE_ENTRIES_KEY = intPreferencesKey("max_visible_entries")
private val NOTIFICATION_CLICK_ACTION_KEY = stringPreferencesKey("notification_click_action")
private val DAY_HEADER_START_PADDING_KEY = intPreferencesKey("day_header_start_padding_dp")
private val EVENT_START_PADDING_KEY = intPreferencesKey("event_start_padding_dp")
private val DAY_HEADER_TO_EVENT_SPACING_KEY = intPreferencesKey("day_header_to_event_spacing_dp")
private val BETWEEN_EVENTS_SPACING_KEY = intPreferencesKey("between_events_spacing_dp")
private val BETWEEN_DAY_HEADERS_SPACING_KEY = intPreferencesKey("between_day_headers_spacing_dp")

private fun Preferences.parseSelectedCalendarIds(): Set<Long>? =
    this[SELECTED_CALENDAR_IDS_KEY]
        ?.split(SELECTED_CALENDAR_IDS_SEPARATOR)
        ?.filter { it.isNotBlank() }
        // 손상된 토큰 하나가 앱 시작을 죽이지 않게 잘못된 토큰만 건너뛴다.
        ?.mapNotNull { it.toLongOrNull() }
        ?.toSet()

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
                    daysToLookAhead =
                        storedPreferences[DAYS_TO_LOOK_AHEAD_KEY]
                            ?: UserPreferences.DEFAULTS.daysToLookAhead,
                    maxVisibleEntries =
                        storedPreferences[MAX_VISIBLE_ENTRIES_KEY]
                            ?: UserPreferences.DEFAULTS.maxVisibleEntries,
                    notificationClickAction =
                        storedPreferences[NOTIFICATION_CLICK_ACTION_KEY]
                            ?.let { stored ->
                                // 저장값이 미래 버전에서 오래된 이름이어도 죽지 않게 기본값으로 돌아간다.
                                NotificationClickAction.entries.firstOrNull { it.name == stored }
                            }
                            ?: UserPreferences.DEFAULTS.notificationClickAction,
                    notificationSpacing =
                        NotificationSpacing.DEFAULTS.copy(
                            dayHeaderStartPaddingDp = storedPreferences.readSpacingDp(
                                DAY_HEADER_START_PADDING_KEY,
                                NotificationSpacing.DEFAULTS.dayHeaderStartPaddingDp,
                            ),
                            eventStartPaddingDp = storedPreferences.readSpacingDp(
                                EVENT_START_PADDING_KEY,
                                NotificationSpacing.DEFAULTS.eventStartPaddingDp,
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
                        ),
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

    suspend fun updateDaysToLookAhead(daysToLookAhead: Int) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[DAYS_TO_LOOK_AHEAD_KEY] = daysToLookAhead
        }
    }

    suspend fun updateMaxVisibleEntries(maxVisibleEntries: Int) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[MAX_VISIBLE_ENTRIES_KEY] = maxVisibleEntries
        }
    }

    suspend fun updateNotificationClickAction(clickAction: NotificationClickAction) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[NOTIFICATION_CLICK_ACTION_KEY] = clickAction.name
        }
    }

    /** 알림 여백 전체를 한 번의 쓰기에 반영한다. UI는 변경할 한 필드만 바꿔 넘긴다. */
    suspend fun updateNotificationSpacing(spacing: NotificationSpacing) {
        context.userPreferencesDataStore.edit { storedPreferences ->
            storedPreferences[DAY_HEADER_START_PADDING_KEY] = spacing.dayHeaderStartPaddingDp
            storedPreferences[EVENT_START_PADDING_KEY] = spacing.eventStartPaddingDp
            storedPreferences[DAY_HEADER_TO_EVENT_SPACING_KEY] = spacing.dayHeaderToEventSpacingDp
            storedPreferences[BETWEEN_EVENTS_SPACING_KEY] = spacing.betweenEventsSpacingDp
            storedPreferences[BETWEEN_DAY_HEADERS_SPACING_KEY] = spacing.betweenDayHeadersSpacingDp
        }
    }
}
