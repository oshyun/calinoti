@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.calinoti.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calinoti.app.AgendaRefresher
import com.calinoti.app.R
import com.calinoti.app.data.CalendarReader
import com.calinoti.app.data.NotificationClickAction
import com.calinoti.app.data.UserCalendar
import com.calinoti.app.data.UserPreferences
import com.calinoti.app.data.UserPreferencesRepository
import com.calinoti.app.data.withToggledCalendar
import kotlinx.coroutines.launch

/** 권한 안내와 설정(캘린더 선택·표시 옵션)으로 이뤄진 앱의 유일한 화면. */
@Composable
fun CalendarStatusScreen(
    calendarReader: CalendarReader,
    userPreferencesRepository: UserPreferencesRepository,
    agendaRefresher: AgendaRefresher,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences by userPreferencesRepository.userPreferences
        .collectAsState(initial = UserPreferences.DEFAULTS)

    var hasCalendarPermission by remember {
        mutableStateOf(calendarReader.hasCalendarPermission())
    }
    var hasNotificationPermission by remember {
        mutableStateOf(context.hasNotificationPermission())
    }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.READ_CALENDAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasCalendarPermission = calendarReader.hasCalendarPermission()
        hasNotificationPermission = context.hasNotificationPermission()
    }

    val calendars = remember(hasCalendarPermission) {
        if (hasCalendarPermission) calendarReader.loadCalendars() else emptyList()
    }

    val applyUpdateAndRefresh: (suspend () -> Unit) -> Unit = { update ->
        coroutineScope.launch {
            update()
            agendaRefresher.refreshNow()
        }
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

        if (!hasCalendarPermission || !hasNotificationPermission) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.calendar_permission_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.calendar_permission_description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                        Text(stringResource(R.string.calendar_permission_button))
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
            if (calendars.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_calendars_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (userPreferences.selectedCalendarIds.isEmpty()) {
                    Text(
                        text = stringResource(R.string.calendars_all_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                for (calendar in calendars) {
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
                            checked = userPreferences.selectedCalendarIds.isEmpty() ||
                                calendar.id in userPreferences.selectedCalendarIds,
                            onCheckedChange = { isChecked ->
                                applyUpdateAndRefresh {
                                    val nextPreferences = userPreferences.withToggledCalendar(
                                        calendar = calendar,
                                        isChecked = isChecked,
                                        allCalendars = calendars,
                                    )
                                    userPreferencesRepository.updateSelectedCalendarIds(
                                        nextPreferences.selectedCalendarIds,
                                    )
                                }
                            },
                        )
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (days in UserPreferences.DAYS_TO_LOOK_AHEAD_CHOICES) {
                    FilterChip(
                        selected = userPreferences.daysToLookAhead == days,
                        onClick = {
                            applyUpdateAndRefresh {
                                userPreferencesRepository.updateDaysToLookAhead(days)
                            }
                        },
                        label = { Text(stringResource(R.string.days_format, days)) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.max_visible_entries_label),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (entryCount in UserPreferences.MAX_VISIBLE_ENTRIES_CHOICES) {
                    FilterChip(
                        selected = userPreferences.maxVisibleEntries == entryCount,
                        onClick = {
                            applyUpdateAndRefresh {
                                userPreferencesRepository.updateMaxVisibleEntries(entryCount)
                            }
                        },
                        label = { Text(stringResource(R.string.entries_format, entryCount)) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.click_action_label),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = userPreferences.notificationClickAction == NotificationClickAction.OPEN_APP,
                    onClick = {
                        applyUpdateAndRefresh {
                            userPreferencesRepository.updateNotificationClickAction(
                                NotificationClickAction.OPEN_APP,
                            )
                        }
                    },
                )
                Text(stringResource(R.string.click_action_open_app))
                Spacer(Modifier.width(16.dp))
                RadioButton(
                    selected = userPreferences.notificationClickAction == NotificationClickAction.CREATE_EVENT,
                    onClick = {
                        applyUpdateAndRefresh {
                            userPreferencesRepository.updateNotificationClickAction(
                                NotificationClickAction.CREATE_EVENT,
                            )
                        }
                    },
                )
                Text(stringResource(R.string.click_action_create_event))
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = { applyUpdateAndRefresh { } }) {
                Text(stringResource(R.string.refresh_now_button))
            }
        }
    }
}

/** API 33 미만은 알림 권한이 런타임 권한이 아니므로 허용된 것으로 본다. */
private fun Context.hasNotificationPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
