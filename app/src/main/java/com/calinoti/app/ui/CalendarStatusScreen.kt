@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.calinoti.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.calinoti.app.R
import com.calinoti.app.data.CalendarReader
import com.calinoti.app.data.NotificationClickAction
import com.calinoti.app.data.UserCalendar
import com.calinoti.app.data.UserPreferences
import com.calinoti.app.data.UserPreferencesRepository
import com.calinoti.app.notification.AgendaNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 권한 안내와 설정(캘린더 선택·표시 옵션)으로 이뤄진 앱의 유일한 화면.
 * 설정 변경은 저장만 담당한다 — 알림 갱신은 AgendaApplication의 설정 감시가 자동으로 한다.
 */
@Composable
fun CalendarStatusScreen(
    calendarReader: CalendarReader,
    notificationManager: AgendaNotificationManager,
    userPreferencesRepository: UserPreferencesRepository,
    versionLabel: String,
    refreshAgenda: () -> Unit,
    openAppSettings: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val userPreferences by userPreferencesRepository.userPreferences
        .collectAsState(initial = UserPreferences.DEFAULTS)

    var hasCalendarPermission by remember {
        mutableStateOf(calendarReader.hasCalendarPermission())
    }
    var shouldPromptForNotificationPermission by remember {
        mutableStateOf(notificationManager.shouldPromptForNotificationPermission())
    }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.READ_CALENDAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    // 권한 상태를 다시 읽어 반영하고, 실제로 변했을 때만 알림을 다시 그린다.
    // 권한 다이얼로그 콜백과 ON_RESUME 복귀가 같은 규칙을 쓴다.
    fun recheckPermissionsAndRefreshIfChanged() {
        val calendarPermissionNow = calendarReader.hasCalendarPermission()
        val notificationPromptNow = notificationManager.shouldPromptForNotificationPermission()
        val permissionStateChanged =
            calendarPermissionNow != hasCalendarPermission ||
                notificationPromptNow != shouldPromptForNotificationPermission
        hasCalendarPermission = calendarPermissionNow
        shouldPromptForNotificationPermission = notificationPromptNow
        if (permissionStateChanged) refreshAgenda()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        recheckPermissionsAndRefreshIfChanged()
    }

    // 시스템 설정에서 권한을 바꾸고 돌아와도 카드와 알림이 즉시 따라오게 한다.
    // ON_RESUME이 "권한이 바뀌었을 수 있는" 단일 체크포인트다 (돌아오는 시점에만 상태를 다시 읽는다).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val resumeObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recheckPermissionsAndRefreshIfChanged()
        }
        lifecycleOwner.lifecycle.addObserver(resumeObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(resumeObserver) }
    }

    // 프로바이더 쿼리는 IPC라서 컴포지션(메인 스레드)에서 직접 돌리지 않는다.
    // null은 아직 불러오는 중임을 뜻한다 — "캘린더 없음"과 구분해 로딩 중 깜빡임을 막는다.
    val calendars by produceState<List<UserCalendar>?>(null, hasCalendarPermission) {
        if (!hasCalendarPermission) {
            value = emptyList()
        } else {
            // 권한이 새로 생겨 이 프로듀서가 재시작될 때 이전 빈 목록이 남아
            // "캘린더 없음"이 잘못 깜빡이지 않게 로딩 상태로 되돌린다.
            value = null
            value = withContext(Dispatchers.IO) { calendarReader.loadCalendars() }
        }
    }

    val updatePreferences: (suspend () -> Unit) -> Unit = { update ->
        coroutineScope.launch { update() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))

        // 누락된 권한만 골라 안내한다 — 있는 권한까지 "필요"라고 표시하지 않는다.
        val missingPermissionNotices = buildList {
            if (!hasCalendarPermission) {
                add(R.string.calendar_permission_title to R.string.calendar_permission_description)
            }
            if (shouldPromptForNotificationPermission) {
                add(R.string.notification_permission_title to R.string.notification_permission_description)
            }
        }
        if (missingPermissionNotices.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    missingPermissionNotices.forEachIndexed { noticeIndex, (titleResourceId, descriptionResourceId) ->
                        if (noticeIndex > 0) Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(titleResourceId),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(descriptionResourceId),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                        Text(stringResource(R.string.calendar_permission_button))
                    }
                    // 알림 권한을 영구 거부하면 시스템 다이얼로그가 아예 뜨지 않는다 —
                    // 그때 유일한 출구인 시스템 설정으로 보내는 탈출구.
                    if (shouldPromptForNotificationPermission) {
                        TextButton(onClick = openAppSettings) {
                            Text(stringResource(R.string.open_app_settings_button))
                        }
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.all_permissions_granted_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (hasCalendarPermission) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_section_calendars),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            val loadedCalendars = calendars
            when {
                // 아직 목록을 불러오는 중이다 — "캘린더 없음"을 대신 보여주지 않는다.
                loadedCalendars == null -> Unit

                loadedCalendars.isEmpty() -> Text(
                    text = stringResource(R.string.no_calendars_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> {
                    // null은 "모든 캘린더 표시"고, 빈 집합은 사용자가 직접 전부 끈 상태다.
                    if (userPreferences.selectedCalendarIds == null) {
                        Text(
                            text = stringResource(R.string.calendars_all_selected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    for (calendar in loadedCalendars) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .background(Color(calendar.color), CircleShape),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
                                if (calendar.accountName != calendar.displayName) {
                                    Text(
                                        text = calendar.accountName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Checkbox(
                                checked = userPreferences.selectedCalendarIds
                                    ?.contains(calendar.id) ?: true,
                                onCheckedChange = { isChecked ->
                                    // 저장값 기준으로 원자적으로 반영되므로 빠르게
                                    // 연속 토글해도 이전 변경이 덮어쓰이지 않는다.
                                    updatePreferences {
                                        userPreferencesRepository.toggleCalendarSelection(
                                            calendarId = calendar.id,
                                            isChecked = isChecked,
                                            allCalendarIds = loadedCalendars.map { it.id }.toSet(),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_section_display),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.days_to_look_ahead_label),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            FilterChipRow(
                choices = UserPreferences.DAYS_TO_LOOK_AHEAD_CHOICES,
                selectedValue = userPreferences.daysToLookAhead,
                labelResourceId = R.string.days_format,
            ) { days ->
                updatePreferences { userPreferencesRepository.updateDaysToLookAhead(days) }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.max_visible_entries_label),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            FilterChipRow(
                choices = UserPreferences.MAX_VISIBLE_ENTRIES_CHOICES,
                selectedValue = userPreferences.maxVisibleEntries,
                labelResourceId = R.string.entries_format,
            ) { entryCount ->
                updatePreferences { userPreferencesRepository.updateMaxVisibleEntries(entryCount) }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.click_action_label),
                style = MaterialTheme.typography.bodyMedium,
            )
            for (clickAction in NotificationClickAction.entries) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = userPreferences.notificationClickAction == clickAction,
                        onClick = {
                            updatePreferences {
                                userPreferencesRepository.updateNotificationClickAction(clickAction)
                            }
                        },
                    )
                    Text(stringResource(clickAction.labelResourceId()))
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = refreshAgenda) {
                Text(stringResource(R.string.refresh_now_button))
            }
        }

        // 권한 여부와 무관하게 설치된 빌드를 확인할 수 있게 한다.
        Spacer(Modifier.height(32.dp))
        Text(
            text = versionLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterChipRow(
    choices: List<Int>,
    selectedValue: Int,
    labelResourceId: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (choice in choices) {
            FilterChip(
                selected = selectedValue == choice,
                onClick = { onSelect(choice) },
                label = { Text(stringResource(labelResourceId, choice)) },
            )
        }
    }
}

private fun NotificationClickAction.labelResourceId(): Int = when (this) {
    NotificationClickAction.OPEN_APP -> R.string.click_action_open_app
    NotificationClickAction.CREATE_EVENT -> R.string.click_action_create_event
}
