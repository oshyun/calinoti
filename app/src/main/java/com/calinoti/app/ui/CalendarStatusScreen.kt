package com.calinoti.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.calinoti.app.R
import com.calinoti.app.data.CalendarAppReader
import com.calinoti.app.data.CalendarReader
import com.calinoti.app.data.HiddenItemType
import com.calinoti.app.data.InstalledCalendarApp
import com.calinoti.app.data.NotificationSpacing
import com.calinoti.app.data.UserCalendar
import com.calinoti.app.data.UserPreferences
import com.calinoti.app.data.UserPreferencesRepository
import com.calinoti.app.notification.AgendaNotificationManager
import com.calinoti.app.notification.AgendaRemoteViewsFactory
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
    calendarAppReader: CalendarAppReader,
    notificationManager: AgendaNotificationManager,
    remoteViewsFactory: AgendaRemoteViewsFactory,
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

    // 설치 캘린더 앱 목록은 화면 수명 동안 한 번만 조회한다. 섹션을 접었다 펴도 다시 묻지
    // 않고, 조회 중(null)에는 "찾을 수 없음"을 대신 보여주지 않는다.
    val installedCalendarApps by produceState<List<InstalledCalendarApp>?>(null) {
        value = withContext(Dispatchers.IO) { calendarAppReader.loadInstalledCalendarApps() }
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
    var isEventRangeSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var isCollapsedHiddenItemsSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var isNotificationDisplaySectionExpanded by rememberSaveable { mutableStateOf(false) }
    var isNotificationActionSectionExpanded by rememberSaveable { mutableStateOf(false) }

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
                                        // 색 점도 알림 카드와 같은 표준 톤으로 통일해, 여기서
                                        // 보이는 색이 알림에서 보이는 색과 같게 한다. 화면은 항상
                                        // 라이트 스킴(Theme.kt)이므로 라이트 톤을 쓴다.
                                        Box(
                                            Modifier
                                                .size(12.dp)
                                                .background(
                                                    Color(
                                                        CalendarColorTone.standardizeCalendarColor(
                                                            color = calendar.color,
                                                            isDarkTheme = false,
                                                        )
                                                    ),
                                                    CircleShape,
                                                ),
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
                title = stringResource(R.string.settings_section_event_range),
                summary = stringResource(
                    R.string.event_range_summary_format,
                    userPreferences.windowStartDays,
                    userPreferences.windowEndDays,
                    userPreferences.maxVisibleEntries,
                ),
                isExpanded = isEventRangeSectionExpanded,
                onToggleExpanded = {
                    isEventRangeSectionExpanded = !isEventRangeSectionExpanded
                },
            ) {
                IntegerSettingField(
                    fieldLabelResourceId = R.string.window_start_days_label,
                    unitSuffixResourceId = R.string.days_unit_suffix,
                    storedValue = userPreferences.windowStartDays,
                    allowsNegativeValues = true,
                    guidanceText = stringResource(R.string.window_start_days_hint),
                    invalidValueText = stringResource(R.string.display_window_order_invalid_message),
                    isValidValue = { startDays -> startDays <= userPreferences.windowEndDays },
                    onValidValueChange = { startDays ->
                        updatePreferences { userPreferencesRepository.updateWindowStartDays(startDays) }
                    },
                )

                Spacer(Modifier.height(12.dp))
                IntegerSettingField(
                    fieldLabelResourceId = R.string.window_end_days_label,
                    unitSuffixResourceId = R.string.days_unit_suffix,
                    storedValue = userPreferences.windowEndDays,
                    guidanceText = stringResource(R.string.window_end_days_hint),
                    invalidValueText = stringResource(R.string.display_window_order_invalid_message),
                    isValidValue = { endDays -> endDays >= userPreferences.windowStartDays },
                    onValidValueChange = { endDays ->
                        updatePreferences { userPreferencesRepository.updateWindowEndDays(endDays) }
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
                    fieldLabelResourceId = R.string.max_visible_entries_label,
                    unitSuffixResourceId = R.string.entries_unit_suffix,
                    storedValue = userPreferences.maxVisibleEntries,
                    invalidValueText = stringResource(R.string.max_visible_entries_invalid_message),
                    isValidValue = { entryCount -> entryCount >= 1 },
                    onValidValueChange = { entryCount ->
                        updatePreferences { userPreferencesRepository.updateMaxVisibleEntries(entryCount) }
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            CollapsibleSection(
                title = stringResource(R.string.settings_section_collapsed_hidden_items),
                summary = stringResource(R.string.collapsed_hidden_items_summary),
                isExpanded = isCollapsedHiddenItemsSectionExpanded,
                onToggleExpanded = {
                    isCollapsedHiddenItemsSectionExpanded = !isCollapsedHiddenItemsSectionExpanded
                },
            ) {
                Text(
                    text = stringResource(R.string.collapsed_hidden_items_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                // entries 순회라 항목 추가는 enum 상수 + 문자열 2개만으로 끝난다.
                for (hiddenItemType in HiddenItemType.entries) {
                    HiddenItemOptionRow(
                        label = stringResource(hiddenItemType.labelResourceId()),
                        description = stringResource(hiddenItemType.descriptionResourceId()),
                        isChecked = hiddenItemType in userPreferences.hiddenItemTypes,
                        onCheckedChange = { isChecked ->
                            // 저장값 기준으로 원자적으로 반영되므로 빠르게 연속 토글해도
                            // 이전 변경이 덮어쓰이지 않는다 (캘린더 선택과 같은 규칙).
                            updatePreferences {
                                userPreferencesRepository.toggleHiddenItemType(
                                    itemType = hiddenItemType,
                                    isChecked = isChecked,
                                )
                            }
                        },
                    )
                }
                HiddenItemOptionRow(
                    label = stringResource(R.string.hidden_items_apply_to_expanded_label),
                    description =
                        stringResource(R.string.hidden_items_apply_to_expanded_description),
                    isChecked = userPreferences.applyHiddenItemsToExpanded,
                    onCheckedChange = { isChecked ->
                        updatePreferences {
                            userPreferencesRepository.updateHiddenItemsApplyToExpanded(isChecked)
                        }
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            // 알림 표시방식 섹션. 글자 크기와 여백은 알림을 그리는 방식을 꾸미는 설정이라
            // 미리보기와 한 섹션에 둔다 — 어느 값이 알림의 어느 부분을 바꾸는지 미리보기로
            // 바로 대응해 볼 수 있다.
            CollapsibleSection(
                title = stringResource(R.string.settings_section_notification_display),
                summary = stringResource(R.string.notification_display_summary),
                isExpanded = isNotificationDisplaySectionExpanded,
                onToggleExpanded = {
                    isNotificationDisplaySectionExpanded = !isNotificationDisplaySectionExpanded
                },
            ) {
                val currentSpacing = userPreferences.notificationSpacing
                // 드래그 중 임시값. 저장이 도착할 때마다 저장값으로 통째 되돌리지 않는다 —
                // 되돌리면 한 슬라이더의 저장이 직전에 조정한 다른 슬라이더의 값을 원래대로
                // 튀게 해 여백끼리 연동되는 것처럼 보인다. 임시값은 마지막 드래그 값을 유지
                // 하고, null은 드래그가 없어 저장값을 그대로 씀을 뜻한다. 여백·글자 크기는
                // 이 화면의 슬라이더로만 바뀌므로 임시값을 유지해도 저장 도착과 어긋나지 않는다.
                var previewSpacing by remember { mutableStateOf<NotificationSpacing?>(null) }
                var previewTimedEventTextSizeSp by remember { mutableStateOf<Int?>(null) }
                var previewAllDayEventTextSizeSp by remember { mutableStateOf<Int?>(null) }
                val effectiveSpacing = previewSpacing ?: currentSpacing
                val effectiveTimedEventTextSizeSp =
                    previewTimedEventTextSizeSp ?: userPreferences.notificationTextSizeSp
                val effectiveAllDayEventTextSizeSp =
                    previewAllDayEventTextSizeSp ?: userPreferences.allDayEventTextSizeSp
                Text(
                    text = stringResource(R.string.settings_display_preview_caption),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_display_preview_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                NotificationSpacingPreview(
                    spacing = effectiveSpacing,
                    notificationTextSizeSp = effectiveTimedEventTextSizeSp,
                    allDayEventTextSizeSp = effectiveAllDayEventTextSizeSp,
                    remoteViewsFactory = remoteViewsFactory,
                )
                Spacer(Modifier.height(12.dp))
                StepSettingSliderRow(
                    labelResourceId = R.string.timed_event_text_size_label,
                    currentValue = effectiveTimedEventTextSizeSp,
                    adjustableRange = UserPreferences.NOTIFICATION_TEXT_SIZE_RANGE_SP,
                    valueFormatResourceId = R.string.text_size_sp_format,
                    onPreviewValueChange = { textSizeSp ->
                        previewTimedEventTextSizeSp = textSizeSp
                    },
                ) { textSizeSp ->
                    updatePreferences {
                        userPreferencesRepository.updateNotificationTextSize(textSizeSp)
                    }
                }

                Spacer(Modifier.height(12.dp))
                StepSettingSliderRow(
                    labelResourceId = R.string.all_day_event_text_size_label,
                    currentValue = effectiveAllDayEventTextSizeSp,
                    adjustableRange = UserPreferences.NOTIFICATION_TEXT_SIZE_RANGE_SP,
                    valueFormatResourceId = R.string.text_size_sp_format,
                    onPreviewValueChange = { textSizeSp ->
                        previewAllDayEventTextSizeSp = textSizeSp
                    },
                ) { textSizeSp ->
                    updatePreferences {
                        userPreferencesRepository.updateAllDayEventTextSize(textSizeSp)
                    }
                }

                Spacer(Modifier.height(12.dp))
                StepSettingSliderRow(
                    labelResourceId = R.string.day_header_start_padding_label,
                    currentValue = effectiveSpacing.dayHeaderStartPaddingDp,
                    adjustableRange = NotificationSpacing.RANGE_DP,
                    valueFormatResourceId = R.string.spacing_dp_format,
                    onPreviewValueChange = { newValueDp ->
                        previewSpacing =
                            effectiveSpacing.copy(dayHeaderStartPaddingDp = newValueDp)
                    },
                ) { newValueDp ->
                    updatePreferences {
                        userPreferencesRepository.updateNotificationSpacing(
                            currentSpacing.copy(dayHeaderStartPaddingDp = newValueDp),
                        )
                    }
                }
                StepSettingSliderRow(
                    labelResourceId = R.string.time_to_title_spacing_label,
                    currentValue = effectiveSpacing.timeToTitleSpacingDp,
                    adjustableRange = NotificationSpacing.RANGE_DP,
                    valueFormatResourceId = R.string.spacing_dp_format,
                    onPreviewValueChange = { newValueDp ->
                        previewSpacing = effectiveSpacing.copy(timeToTitleSpacingDp = newValueDp)
                    },
                ) { newValueDp ->
                    updatePreferences {
                        userPreferencesRepository.updateNotificationSpacing(
                            currentSpacing.copy(timeToTitleSpacingDp = newValueDp),
                        )
                    }
                }
                StepSettingSliderRow(
                    labelResourceId = R.string.day_header_to_event_spacing_label,
                    currentValue = effectiveSpacing.dayHeaderToEventSpacingDp,
                    adjustableRange = NotificationSpacing.RANGE_DP,
                    valueFormatResourceId = R.string.spacing_dp_format,
                    onPreviewValueChange = { newValueDp ->
                        previewSpacing =
                            effectiveSpacing.copy(dayHeaderToEventSpacingDp = newValueDp)
                    },
                ) { newValueDp ->
                    updatePreferences {
                        userPreferencesRepository.updateNotificationSpacing(
                            currentSpacing.copy(dayHeaderToEventSpacingDp = newValueDp),
                        )
                    }
                }
                StepSettingSliderRow(
                    labelResourceId = R.string.between_events_spacing_label,
                    currentValue = effectiveSpacing.betweenEventsSpacingDp,
                    adjustableRange = NotificationSpacing.RANGE_DP,
                    valueFormatResourceId = R.string.spacing_dp_format,
                    onPreviewValueChange = { newValueDp ->
                        previewSpacing = effectiveSpacing.copy(betweenEventsSpacingDp = newValueDp)
                    },
                ) { newValueDp ->
                    updatePreferences {
                        userPreferencesRepository.updateNotificationSpacing(
                            currentSpacing.copy(betweenEventsSpacingDp = newValueDp),
                        )
                    }
                }
                StepSettingSliderRow(
                    labelResourceId = R.string.between_day_headers_spacing_label,
                    currentValue = effectiveSpacing.betweenDayHeadersSpacingDp,
                    adjustableRange = NotificationSpacing.RANGE_DP,
                    valueFormatResourceId = R.string.spacing_dp_format,
                    onPreviewValueChange = { newValueDp ->
                        previewSpacing =
                            effectiveSpacing.copy(betweenDayHeadersSpacingDp = newValueDp)
                    },
                ) { newValueDp ->
                    updatePreferences {
                        userPreferencesRepository.updateNotificationSpacing(
                            currentSpacing.copy(betweenDayHeadersSpacingDp = newValueDp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 접힌 헤더에도 두 클릭 동작의 지정 앱·고정 여부가 보이게 요약을 계산한다.
            // 목록이 아직 로딩 중이거나 지정 앱이 사라졌으면 기본값 라벨로 둔다.
            val selfClickTargetPackageName = LocalContext.current.packageName
            val eventClickTargetLabel = clickTargetSummaryLabel(
                clickTargetPackageName = userPreferences.eventClickTargetPackageName,
                selfClickTargetPackageName = selfClickTargetPackageName,
                installedCalendarApps = installedCalendarApps,
            )
            val notificationClickTargetLabel = clickTargetSummaryLabel(
                clickTargetPackageName = userPreferences.notificationClickTargetPackageName,
                selfClickTargetPackageName = selfClickTargetPackageName,
                installedCalendarApps = installedCalendarApps,
            )
            val notificationActionSummary =
                if (userPreferences.isNotificationPinned) {
                    stringResource(
                        R.string.notification_action_summary_fixed_format,
                        eventClickTargetLabel,
                        notificationClickTargetLabel,
                    )
                } else {
                    stringResource(
                        R.string.notification_action_summary_format,
                        eventClickTargetLabel,
                        notificationClickTargetLabel,
                    )
                }
            CollapsibleSection(
                title = stringResource(R.string.settings_section_notification_actions),
                summary = notificationActionSummary,
                isExpanded = isNotificationActionSectionExpanded,
                onToggleExpanded = {
                    isNotificationActionSectionExpanded = !isNotificationActionSectionExpanded
                },
            ) {
                ClickTargetSelector(
                    selectorTitleResourceId = R.string.event_click_action_title,
                    selectorDescriptionResourceId = R.string.event_click_action_description,
                    installedCalendarApps = installedCalendarApps,
                    selfClickTargetPackageName = selfClickTargetPackageName,
                    selectedPackageName = userPreferences.eventClickTargetPackageName,
                    onSelectClickTarget = { packageName ->
                        updatePreferences {
                            userPreferencesRepository.updateEventClickTargetPackageName(packageName)
                        }
                    },
                )

                Spacer(Modifier.height(16.dp))
                ClickTargetSelector(
                    selectorTitleResourceId = R.string.notification_click_action_title,
                    selectorDescriptionResourceId = R.string.notification_click_action_description,
                    installedCalendarApps = installedCalendarApps,
                    selfClickTargetPackageName = selfClickTargetPackageName,
                    selectedPackageName = userPreferences.notificationClickTargetPackageName,
                    onSelectClickTarget = { packageName ->
                        updatePreferences {
                            userPreferencesRepository
                                .updateNotificationClickTargetPackageName(packageName)
                        }
                    },
                )

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.notification_pinned_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = userPreferences.isNotificationPinned,
                        onCheckedChange = { isChecked ->
                            updatePreferences {
                                userPreferencesRepository.updateNotificationPinned(isChecked)
                            }
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.notification_pinned_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
 * [summary]는 접힌 상태에서도 현재 상태나 섹션의 용도를 알 수 있게 하는 한 줄 요약이다 (null이면 표시하지 않는다).
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

/**
 * 라벨과 현재 값, 정수 스냅 슬라이더 한 줄. 여백(dp)과 글자 크기(sp)가 같은 규격을 공유한다.
 * 값의 단일 출처는 상위다 — [currentValue]는 저장값 또는 드래그 중 임시값이고, 이 행은
 * 상태를 따로 두지 않는다(상위 임시값이 저장 도착에 튀지 않으므로 직결이 안전하다).
 *
 * [onPreviewValueChange]는 드래그 중 임시값을, [onChanged]는 드래그를 놓아 확정된 값을 받는다.
 * 저장마다 알림 갱신(캘린더 재쿼리)이 따라오므로 저장은 드래그를 놓을 때만 일어난다.
 */
@Composable
private fun StepSettingSliderRow(
    labelResourceId: Int,
    currentValue: Int,
    adjustableRange: IntRange,
    valueFormatResourceId: Int,
    onPreviewValueChange: (Int) -> Unit,
    onChanged: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(labelResourceId),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(valueFormatResourceId, currentValue),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StepSlider(
            value = currentValue.toFloat(),
            valueRange = adjustableRange,
            onValueChange = onPreviewValueChange,
            onValueChangeFinished = onChanged,
        )
    }
}

// StepSlider의 시각 규격. 트랙 양끝 inset은 드래그 중 최대 thumb(8dp)+halo(2dp)라서
// 어떤 순간에도 thumb가 캔버스 밖으로 잘리지 않는다.
private val TRACK_HEIGHT_DP = 4.dp
private val THUMB_RADIUS_DP = 6.dp
private val DRAGGED_THUMB_RADIUS_DP = 8.dp
private val THUMB_HALO_THICKNESS_DP = 2.dp
private val TRACK_INSET_DP = DRAGGED_THUMB_RADIUS_DP + THUMB_HALO_THICKNESS_DP

/**
 * 정수 단계로 스냅되는 얇은 트랙 슬라이더. Material3 기본 Slider보다 트랙·thumb가 작아
 * 설정 목록에 여러 개가 나란히 놓일 때 시선을 덜 뺏는다. 트랙을 탭하면 그 위치의 값으로
 * 바로 이동하고, 드래그 중에는 thumb가 살짝 부풀어 잡은 느낌을 준다.
 */
@Composable
private fun StepSlider(
    value: Float,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isThumbDragged by remember { mutableStateOf(false) }
    // pointerInput은 [valueRange]를 키로 둬 값이 바뀌어도 제스처 감시를 재시작하지 않는다 —
    // 그래서 이 블록에 잡힌 람다가 늘 최신 값을 보게 다리를 놓는다. 값 직결 구조에서
    // 옛 값을 붙잡으면 드래그를 놓는 순간 저장되는 값이 튄다.
    val latestValue by rememberUpdatedState(value)
    val thumbRadius by animateDpAsState(
        targetValue = if (isThumbDragged) DRAGGED_THUMB_RADIUS_DP else THUMB_RADIUS_DP,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "thumbRadius",
    )
    val activeTrackColor = MaterialTheme.colorScheme.primary
    val inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbHaloColor = MaterialTheme.colorScheme.surface

    // 터치 좌표를 값으로 바꾼다. 트랙 inset을 같이 빼야 thumb 위치와 터치 위치가 어긋나지 않는다.
    fun snapValueToPosition(positionX: Float, trackInsetPx: Float, trackWidthPx: Float): Int {
        val usableTrackWidthPx = trackWidthPx - 2 * trackInsetPx
        val dragFraction = ((positionX - trackInsetPx) / usableTrackWidthPx).coerceIn(0f, 1f)
        val rawValue = valueRange.first + dragFraction * (valueRange.last - valueRange.first)
        return rawValue.roundToInt()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = valueRange.first.toFloat()..valueRange.last.toFloat(),
                )
            }
            .pointerInput(valueRange) {
                detectTapGestures { gesturePosition ->
                    val snappedValue =
                        snapValueToPosition(gesturePosition.x, TRACK_INSET_DP.toPx(), size.width.toFloat())
                    onValueChange(snappedValue)
                    onValueChangeFinished(snappedValue)
                }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { _ -> isThumbDragged = true },
                    onDragEnd = {
                        isThumbDragged = false
                        onValueChangeFinished(latestValue.roundToInt())
                    },
                    onDragCancel = { isThumbDragged = false },
                ) { change, _ ->
                    change.consume()
                    onValueChange(
                        snapValueToPosition(change.position.x, TRACK_INSET_DP.toPx(), size.width.toFloat()),
                    )
                }
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val trackInsetPx = TRACK_INSET_DP.toPx()
            val trackStartX = trackInsetPx
            val trackEndX = size.width - trackInsetPx
            val valueFraction =
                ((value - valueRange.first) / (valueRange.last - valueRange.first))
                    .coerceIn(0f, 1f)
            val thumbCenterX = trackStartX + (trackEndX - trackStartX) * valueFraction
            val trackHeightPx = TRACK_HEIGHT_DP.toPx()
            val trackTopY = size.height / 2 - trackHeightPx / 2
            val trackCorner = CornerRadius(trackHeightPx / 2)

            if (thumbCenterX > trackStartX) {
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(trackStartX, trackTopY),
                    size = Size(thumbCenterX - trackStartX, trackHeightPx),
                    cornerRadius = trackCorner,
                )
            }
            if (trackEndX > thumbCenterX) {
                drawRoundRect(
                    color = inactiveTrackColor,
                    topLeft = Offset(thumbCenterX, trackTopY),
                    size = Size(trackEndX - thumbCenterX, trackHeightPx),
                    cornerRadius = trackCorner,
                )
            }
            // thumb은 배경색 halo 위에 활성 트랙 색 원 — 트랙 위에서 독립적으로 떠 보인다.
            drawCircle(
                color = thumbHaloColor,
                radius = thumbRadius.toPx() + THUMB_HALO_THICKNESS_DP.toPx(),
                center = Offset(thumbCenterX, size.height / 2),
            )
            drawCircle(
                color = activeTrackColor,
                radius = thumbRadius.toPx(),
                center = Offset(thumbCenterX, size.height / 2),
            )
        }
    }
}

/**
 * 정수 하나를 저장하는 설정 입력 필드. 숫자(맨 앞 '-' 포함)만 입력받으며,
 * 입력이 유효한 정수이고 저장값과 다를 때만 [onValidValueChange]를 부른다.
 * [isValidValue] 검증에 실패한 값은 [invalidValueText] 오류 문구와 함께 저장하지 않는다.
 * [guidanceText]는 오류가 없을 때 보이는 안내 문구다.
 * [allowsNegativeValues]가 true면 필드 안에 ± 토글 버튼을 함께 보여 숫자 키패드에
 * '-' 키가 없는 키보드(Samsung One UI 등)에서도 음수를 입력할 수 있게 한다.
 */
@Composable
private fun IntegerSettingField(
    fieldLabelResourceId: Int,
    unitSuffixResourceId: Int,
    storedValue: Int,
    allowsNegativeValues: Boolean = false,
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

    // 키보드 입력과 ± 토글 버튼이 같은 검증·반영 경로를 쓴다.
    fun acceptTypedText(typedText: String) {
        val isIntegerText = typedText.toIntOrNull() != null
        if (typedText.isEmpty() || typedText == "-" || isIntegerText) {
            inputText = typedText
            val typedValue = typedText.toIntOrNull()
            if (typedValue != null && isValidValue(typedValue) && typedValue != storedValue) {
                onValidValueChange(typedValue)
            }
        }
    }

    OutlinedTextField(
        value = inputText,
        onValueChange = { typedText -> acceptTypedText(typedText) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(fieldLabelResourceId)) },
        suffix = { Text(stringResource(unitSuffixResourceId)) },
        trailingIcon = if (allowsNegativeValues) {
            {
                IconButton(onClick = { acceptTypedText(negateSignOfIntegerText(inputText)) }) {
                    Text(stringResource(R.string.toggle_number_sign_button))
                }
            }
        } else {
            null
        },
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

/** 입력 중인 정수 텍스트의 부호를 반전한다. 빈 문자열은 "-", "-"는 빈 문자열이 된다. */
private fun negateSignOfIntegerText(text: String): String = when {
    text.isEmpty() -> "-"
    text == "-" -> ""
    text.startsWith("-") -> text.removePrefix("-")
    else -> "-$text"
}

/**
 * 클릭 동작(일정 클릭·알림 클릭)이 열 앱을 고르는 공용 선택 목록.
 * 서브섹션 제목·설명을 함께 보여주며, 옵션은 기본값(시스템 선택)·Calinoti 열기·
 * 설치 캘린더 앱 순이다. [selfClickTargetPackageName]은 Calinoti 열기 옵션의 저장값으로
 * 쓰는 자기 자신의 패키지 이름이다.
 */
@Composable
private fun ClickTargetSelector(
    selectorTitleResourceId: Int,
    selectorDescriptionResourceId: Int,
    installedCalendarApps: List<InstalledCalendarApp>?,
    selfClickTargetPackageName: String,
    selectedPackageName: String,
    onSelectClickTarget: (String) -> Unit,
) {
    Text(
        text = stringResource(selectorTitleResourceId),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = stringResource(selectorDescriptionResourceId),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    // 기본값과 Calinoti 열기는 캘린더 앱이 없는 기기에서도 유효한 선택지라 항상 보여준다.
    ClickTargetOptionRow(
        label = stringResource(R.string.calendar_app_default_option),
        isSelected =
            selectedPackageName == UserPreferences.UNSPECIFIED_CLICK_TARGET_PACKAGE_NAME,
        onClick = {
            onSelectClickTarget(UserPreferences.UNSPECIFIED_CLICK_TARGET_PACKAGE_NAME)
        },
    )
    ClickTargetOptionRow(
        label = stringResource(R.string.click_target_self_option),
        isSelected = selectedPackageName == selfClickTargetPackageName,
        onClick = { onSelectClickTarget(selfClickTargetPackageName) },
    )
    when {
        installedCalendarApps == null -> Unit
        installedCalendarApps.isEmpty() -> Text(
            text = stringResource(R.string.no_calendar_apps_found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> {
            for (calendarApp in installedCalendarApps) {
                ClickTargetOptionRow(
                    label = calendarApp.label,
                    // 라벨만으로 구분되지 않는 동명 앱이 많아 제작자 힌트로 패키지 이름을 함께 보여준다.
                    hint = calendarApp.packageName,
                    isSelected = selectedPackageName == calendarApp.packageName,
                    onClick = { onSelectClickTarget(calendarApp.packageName) },
                )
            }
        }
    }
}

/** 클릭 동작 옵션 한 행. [hint]는 동명 앱을 구분하게 하는 패키지 이름 같은 보조 텍스트다. */
@Composable
private fun ClickTargetOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    hint: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = isSelected, onClick = onClick)
        Column {
            Text(label)
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 클릭 동작 설정값을 접힌 헤더 요약 라벨로 바꾼다. Calinoti 자신이면 "Calinoti 열기",
 * 미지정이거나 설치 목록에서 찾을 수 없는(이미 지워진) 앱이면 기본값 라벨로 돌아간다.
 */
@Composable
private fun clickTargetSummaryLabel(
    clickTargetPackageName: String,
    selfClickTargetPackageName: String,
    installedCalendarApps: List<InstalledCalendarApp>?,
): String = when {
    clickTargetPackageName == selfClickTargetPackageName ->
        stringResource(R.string.click_target_self_option)

    clickTargetPackageName == UserPreferences.UNSPECIFIED_CLICK_TARGET_PACKAGE_NAME ->
        stringResource(R.string.calendar_app_default_option)

    else ->
        installedCalendarApps
            ?.firstOrNull { it.packageName == clickTargetPackageName }
            ?.label
            ?: stringResource(R.string.calendar_app_default_option)
}

/** 감춤 항목 한 줄. 라벨·설명과 체크박스. 감춤 규칙 행과 펼침 적용 행이 같은 모양을 쓴다. */
@Composable
private fun HiddenItemOptionRow(
    label: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}

private fun HiddenItemType.labelResourceId(): Int = when (this) {
    HiddenItemType.ALL_DAY_STARTED_TODAY -> R.string.hidden_item_started_today_label
    HiddenItemType.ALL_DAY_IN_PROGRESS -> R.string.hidden_item_in_progress_label
    HiddenItemType.ALL_DAY_UPCOMING -> R.string.hidden_item_upcoming_label
    HiddenItemType.ALL_DAY_FINISHED -> R.string.hidden_item_finished_label
}

private fun HiddenItemType.descriptionResourceId(): Int = when (this) {
    HiddenItemType.ALL_DAY_STARTED_TODAY -> R.string.hidden_item_started_today_description
    HiddenItemType.ALL_DAY_IN_PROGRESS -> R.string.hidden_item_in_progress_description
    HiddenItemType.ALL_DAY_UPCOMING -> R.string.hidden_item_upcoming_description
    HiddenItemType.ALL_DAY_FINISHED -> R.string.hidden_item_finished_description
}
