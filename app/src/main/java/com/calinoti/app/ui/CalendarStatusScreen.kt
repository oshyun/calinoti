package com.calinoti.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.calinoti.app.R
import com.calinoti.app.data.CalendarReader
import com.calinoti.app.data.NotificationClickAction
import com.calinoti.app.data.NotificationSpacing
import com.calinoti.app.data.UserCalendar
import com.calinoti.app.data.UserPreferences
import com.calinoti.app.data.UserPreferencesRepository
import com.calinoti.app.notification.AgendaNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 권한 안내와 설정(캘린더 선택·표시 옵션)으로 이뤄진 앱의 유일한 화면.
 * 각 설정 묶음은 [CollapsibleSection]으로 접히며, 헤더 요약으로 접은 채 현재 상태를 확인할 수 있다.
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

    // 누락된 권한만 골라 안내한다 — 있는 권한까지 "필요"라고 표시하지 않는다.
    val missingPermissionNotices = buildList {
        if (!hasCalendarPermission) {
            add(R.string.calendar_permission_title to R.string.calendar_permission_description)
        }
        if (shouldPromptForNotificationPermission) {
            add(R.string.notification_permission_title to R.string.notification_permission_description)
        }
    }
    val hasMissingPermissions = missingPermissionNotices.isNotEmpty()

    // 섹션 펼침 상태는 회전·프로세스 재시작에도 유지되고, 앱을 다시 열면 기본 접힘으로 돌아온다.
    var isPermissionsSectionExpanded by rememberSaveable { mutableStateOf(hasMissingPermissions) }
    var isCalendarsSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var isDisplaySettingsSectionExpanded by rememberSaveable { mutableStateOf(false) }

    // 권한이 새로 누락되면 접힘을 무시하고 펼친다 — 권한 안내는 경보 성격이라 사용자 조작보다 우선한다.
    // 다시 접는 것은 막지 않는다: 다음 권한 변경이 있기 전까지는 사용자 선택을 존중한다.
    LaunchedEffect(hasMissingPermissions) {
        if (hasMissingPermissions) isPermissionsSectionExpanded = true
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

        CollapsibleSection(
            title = stringResource(R.string.settings_section_permissions),
            summary = stringResource(
                if (hasMissingPermissions) R.string.permissions_summary_needed
                else R.string.permissions_summary_granted,
            ),
            isExpanded = isPermissionsSectionExpanded,
            onToggleExpanded = { isPermissionsSectionExpanded = !isPermissionsSectionExpanded },
        ) {
            if (missingPermissionNotices.isEmpty()) {
                Text(
                    text = stringResource(R.string.all_permissions_granted_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
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

        if (hasCalendarPermission) {
            Spacer(Modifier.height(12.dp))

            // 접힌 헤더에도 현재 선택 규모가 보이게 요약을 계산한다.
            // 로딩 중(null)일 때는 요약을 비운다 — 아직 모르는 값을 지어 보여주지 않는다.
            val loadedCalendars = calendars
            val calendarsSectionSummary = when {
                loadedCalendars == null -> null

                loadedCalendars.isEmpty() -> stringResource(R.string.no_calendars_found)

                userPreferences.selectedCalendarIds == null ->
                    stringResource(R.string.calendars_summary_all)

                else -> {
                    // 위 브랜치에서 null(전체 선택)을 걸렀으므로 orEmpty는 원본 집합 그대로다.
                    val selectedCalendarIds = userPreferences.selectedCalendarIds.orEmpty()
                    stringResource(
                        R.string.calendars_summary_selected_format,
                        loadedCalendars.count { it.id in selectedCalendarIds },
                        loadedCalendars.size,
                    )
                }
            }

            CollapsibleSection(
                title = stringResource(R.string.settings_section_calendars),
                summary = calendarsSectionSummary,
                isExpanded = isCalendarsSectionExpanded,
                onToggleExpanded = { isCalendarsSectionExpanded = !isCalendarsSectionExpanded },
            ) {
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
                        // 계정(종류)별로 묶어 보여준다. 그룹 순서는 계정명 순,
                        // 그룹 안은 로드 시의 이름 순서를 그대로 유지한다.
                        val calendarsGroupedByAccountName = loadedCalendars
                            .groupBy { it.accountName }
                            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                        calendarsGroupedByAccountName.entries
                            .forEachIndexed { accountGroupIndex, (accountName, accountCalendars) ->
                                if (accountGroupIndex > 0) Spacer(Modifier.height(16.dp))
                                Text(
                                    text = accountName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                for (calendar in accountCalendars) {
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
                                        Text(
                                            calendar.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f),
                                        )
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
                                                        allCalendarIds =
                                                            loadedCalendars.map { it.id }.toSet(),
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            CollapsibleSection(
                title = stringResource(R.string.settings_section_display),
                summary = stringResource(
                    R.string.display_settings_summary_format,
                    userPreferences.daysToLookAhead,
                    userPreferences.maxVisibleEntries,
                ),
                isExpanded = isDisplaySettingsSectionExpanded,
                onToggleExpanded = {
                    isDisplaySettingsSectionExpanded = !isDisplaySettingsSectionExpanded
                },
            ) {
                IntegerSettingField(
                    fieldLabelResourceId = R.string.days_to_look_ahead_label,
                    unitSuffixResourceId = R.string.days_unit_suffix,
                    storedValue = userPreferences.daysToLookAhead,
                    guidanceText = stringResource(R.string.days_to_look_ahead_negative_hint),
                    onValidValueChange = { days ->
                        updatePreferences { userPreferencesRepository.updateDaysToLookAhead(days) }
                    },
                )

                Spacer(Modifier.height(12.dp))
                IntegerSettingField(
                    fieldLabelResourceId = R.string.max_visible_entries_label,
                    unitSuffixResourceId = R.string.entries_unit_suffix,
                    storedValue = userPreferences.maxVisibleEntries,
                    invalidValueText = stringResource(R.string.max_visible_entries_invalid_message),
                    isValidValue = { entryCount -> entryCount >= 1 },
                    onValidValueChange = { entryCount ->
                        updatePreferences { userPreferencesRepository.updateMaxVisibleEntries(entryCount) }
                    },
                )

                Spacer(Modifier.height(12.dp))
                IntegerSettingField(
                    fieldLabelResourceId = R.string.notification_text_size_label,
                    unitSuffixResourceId = R.string.text_size_unit_suffix,
                    storedValue = userPreferences.notificationTextSizeSp,
                    invalidValueText = stringResource(
                        R.string.notification_text_size_invalid_message,
                        UserPreferences.NOTIFICATION_TEXT_SIZE_MIN_SP,
                        UserPreferences.NOTIFICATION_TEXT_SIZE_MAX_SP,
                    ),
                    isValidValue = { textSizeSp ->
                        textSizeSp in
                            UserPreferences.NOTIFICATION_TEXT_SIZE_MIN_SP..UserPreferences.NOTIFICATION_TEXT_SIZE_MAX_SP
                    },
                    onValidValueChange = { textSizeSp ->
                        updatePreferences {
                            userPreferencesRepository.updateNotificationTextSize(textSizeSp)
                        }
                    },
                )

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

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_subsection_spacing),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val currentSpacing = userPreferences.notificationSpacing
                SpacingSliderRow(
                    labelResourceId = R.string.day_header_start_padding_label,
                    savedValueDp = currentSpacing.dayHeaderStartPaddingDp,
                ) { newValueDp ->
                    updatePreferences {
                        userPreferencesRepository.updateNotificationSpacing(
                            currentSpacing.copy(dayHeaderStartPaddingDp = newValueDp),
                        )
                    }
                }
                SpacingSliderRow(
                    labelResourceId = R.string.event_start_padding_label,
                    savedValueDp = currentSpacing.eventStartPaddingDp,
                ) { newValueDp ->
                    updatePreferences {
                        userPreferencesRepository.updateNotificationSpacing(
                            currentSpacing.copy(eventStartPaddingDp = newValueDp),
                        )
                    }
                }
                SpacingSliderRow(
                    labelResourceId = R.string.day_header_to_event_spacing_label,
                    savedValueDp = currentSpacing.dayHeaderToEventSpacingDp,
                ) { newValueDp ->
                    updatePreferences {
                        userPreferencesRepository.updateNotificationSpacing(
                            currentSpacing.copy(dayHeaderToEventSpacingDp = newValueDp),
                        )
                    }
                }
                SpacingSliderRow(
                    labelResourceId = R.string.between_events_spacing_label,
                    savedValueDp = currentSpacing.betweenEventsSpacingDp,
                ) { newValueDp ->
                    updatePreferences {
                        userPreferencesRepository.updateNotificationSpacing(
                            currentSpacing.copy(betweenEventsSpacingDp = newValueDp),
                        )
                    }
                }
                SpacingSliderRow(
                    labelResourceId = R.string.between_day_headers_spacing_label,
                    savedValueDp = currentSpacing.betweenDayHeadersSpacingDp,
                ) { newValueDp ->
                    updatePreferences {
                        userPreferencesRepository.updateNotificationSpacing(
                            currentSpacing.copy(betweenDayHeadersSpacingDp = newValueDp),
                        )
                    }
                }
            }

            // 새로고침은 설정 묶음이 아니라 화면 전체에 적용되는 즉시 동작이라 섹션 밖에 둔다.
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

/**
 * 헤더(제목·요약·펼침 화살표)를 눌러 내용을 펼치거나 접는 섹션.
 * [summary]는 접힌 상태에서도 현재 상태를 알 수 있게 하는 한 줄 요약이다 (null이면 표시하지 않는다).
 */
@Composable
private fun CollapsibleSection(
    title: String,
    summary: String?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (summary != null) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(if (isExpanded) 180f else 0f),
                )
            }
            if (isExpanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SpacingSliderRow(
    labelResourceId: Int,
    savedValueDp: Int,
    onDragFinished: (Int) -> Unit,
) {
    // 드래그 중 값은 로컬로 보관한다. Slider 값을 저장값에 직결하면 thumb가 저장값으로
    // 되돌아가 튄다. 키를 savedValueDp로 두면 저장이 반영된 순간 로컬 값도 따라온다.
    var draggedValueDp by remember(savedValueDp) { mutableStateOf(savedValueDp.toFloat()) }
    val adjustableRange = NotificationSpacing.RANGE_DP
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(labelResourceId),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.spacing_dp_format, draggedValueDp.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = draggedValueDp,
            onValueChange = { newValue -> draggedValueDp = newValue },
            // 드래그를 놓을 때만 저장한다. 저장마다 알림 갱신(캘린더 재쿼리)이 따라오므로
            // 드래그 중 저장하면 제스처 하나에 갱신이 수십 번 쌓인다.
            onValueChangeFinished = { onDragFinished(draggedValueDp.roundToInt()) },
            valueRange = adjustableRange.first.toFloat()..adjustableRange.last.toFloat(),
            steps = adjustableRange.last - adjustableRange.first - 1,
        )
    }
}

/**
 * 정수 하나를 저장하는 설정 입력 필드. 숫자(맨 앞 '-' 포함)만 입력받으며,
 * 입력이 유효한 정수이고 저장값과 다를 때만 [onValidValueChange]를 부른다.
 * [isValidValue] 검증에 실패한 값은 [invalidValueText] 오류 문구와 함께 저장하지 않는다.
 * [guidanceText]는 오류가 없을 때 보이는 안내 문구다.
 */
@Composable
private fun IntegerSettingField(
    fieldLabelResourceId: Int,
    unitSuffixResourceId: Int,
    storedValue: Int,
    guidanceText: String? = null,
    invalidValueText: String? = null,
    isValidValue: (Int) -> Boolean = { true },
    onValidValueChange: (Int) -> Unit,
) {
    // 저장값이 바뀌면(직접 입력한 값이 저장된 직후) 필드 텍스트를 그 값으로 맞춘다.
    // 빈 문자열이나 "-"만 남은 상태는 지우는 중의 임시 상태라 저장값을 덮어쓰지 않는다.
    var inputText by remember(storedValue) { mutableStateOf(storedValue.toString()) }
    val parsedInputValue = inputText.toIntOrNull()
    val showsInvalidValueError = parsedInputValue != null && !isValidValue(parsedInputValue)

    OutlinedTextField(
        value = inputText,
        onValueChange = { typedText ->
            val isIntegerText = typedText.toIntOrNull() != null
            if (typedText.isEmpty() || typedText == "-" || isIntegerText) {
                inputText = typedText
                val typedValue = typedText.toIntOrNull()
                if (typedValue != null && isValidValue(typedValue) && typedValue != storedValue) {
                    onValidValueChange(typedValue)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(fieldLabelResourceId)) },
        suffix = { Text(stringResource(unitSuffixResourceId)) },
        isError = showsInvalidValueError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = {
            when {
                showsInvalidValueError && invalidValueText != null -> Text(invalidValueText)
                guidanceText != null -> Text(guidanceText)
            }
        },
    )
}

private fun NotificationClickAction.labelResourceId(): Int = when (this) {
    NotificationClickAction.OPEN_APP -> R.string.click_action_open_app
    NotificationClickAction.CREATE_EVENT -> R.string.click_action_create_event
}
