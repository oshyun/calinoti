package com.calinoti.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 알림을 눌렀을 때 할 동작. */
enum class NotificationClickAction { OPEN_APP, CREATE_EVENT }

/** 아젠다 알림과 설정 화면이 공유하는 사용자 설정 묶음. */
data class UserPreferences(
    val selectedCalendarIds: Set<Long>,
    val daysToLookAhead: Int,
    val maxVisibleEntries: Int,
    val notificationClickAction: NotificationClickAction,
) {
    companion object {
        val DAYS_TO_LOOK_AHEAD_CHOICES = listOf(3, 7, 14, 30)
        val MAX_VISIBLE_ENTRIES_CHOICES = listOf(5, 10, 15)

        val DEFAULTS = UserPreferences(
            selectedCalendarIds = emptySet(),
            daysToLookAhead = 7,
            maxVisibleEntries = 10,
            notificationClickAction = NotificationClickAction.OPEN_APP,
        )
    }
}

/**
 * 캘린더 선택 토글의 상태 전이를 계산한다.
 * 선택 집합이 비어 있으면 "모든 캘린더 표시"를 뜻하며, 전체가 선택되면 다시 빈 집합으로
 * 단순화해 규칙을 하나로 유지한다.
 */
fun toggledCalendarSelection(
    selectedCalendarIds: Set<Long>,
    calendarId: Long,
    isChecked: Boolean,
    allCalendarIds: Set<Long>,
): Set<Long> {
    val currentlySelectedIds = if (selectedCalendarIds.isEmpty()) allCalendarIds else selectedCalendarIds
    val nextSelectedIds =
        if (isChecked) currentlySelectedIds + calendarId else currentlySelectedIds - calendarId
    return if (nextSelectedIds == allCalendarIds) emptySet() else nextSelectedIds
}

/** 선택된 캘린더 ID 집합을 한 문자열로 저장할 때의 구분자. */
private const val SELECTED_CALENDAR_IDS_SEPARATOR = ","

// 빈 선택은 ""로 직렬화되며, 읽을 때는 "모든 캘린더 표시"가 된다.
private val SELECTED_CALENDAR_IDS_KEY = stringPreferencesKey("selected_calendar_ids")
private val DAYS_TO_LOOK_AHEAD_KEY = intPreferencesKey("days_to_look_ahead")
private val MAX_VISIBLE_ENTRIES_KEY = intPreferencesKey("max_visible_entries")
private val NOTIFICATION_CLICK_ACTION_KEY = stringPreferencesKey("notification_click_action")

private fun Preferences.parseSelectedCalendarIds(): Set<Long>? =
    this[SELECTED_CALENDAR_IDS_KEY]
        ?.split(SELECTED_CALENDAR_IDS_SEPARATOR)
        ?.filter { it.isNotBlank() }
        ?.map { it.toLong() }
        ?.toSet()

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
)

/** 사용자 설정을 DataStore에 저장·조회한다. */
class UserPreferencesRepository(private val context: Context) {

    val userPreferences: Flow<UserPreferences> =
        context.userPreferencesDataStore.data.map { storedPreferences ->
            UserPreferences(
                selectedCalendarIds =
                    storedPreferences.parseSelectedCalendarIds()
                        ?: UserPreferences.DEFAULTS.selectedCalendarIds,
                daysToLookAhead =
                    storedPreferences[DAYS_TO_LOOK_AHEAD_KEY]
                        ?: UserPreferences.DEFAULTS.daysToLookAhead,
                maxVisibleEntries =
                    storedPreferences[MAX_VISIBLE_ENTRIES_KEY]
                        ?: UserPreferences.DEFAULTS.maxVisibleEntries,
                notificationClickAction =
                    storedPreferences[NOTIFICATION_CLICK_ACTION_KEY]
                        ?.let(NotificationClickAction::valueOf)
                        ?: UserPreferences.DEFAULTS.notificationClickAction,
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
            val currentSelectedIds = storedPreferences.parseSelectedCalendarIds()
                ?: UserPreferences.DEFAULTS.selectedCalendarIds
            val nextSelectedIds = toggledCalendarSelection(
                selectedCalendarIds = currentSelectedIds,
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
}
