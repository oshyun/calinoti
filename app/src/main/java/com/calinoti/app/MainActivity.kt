package com.calinoti.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.calinoti.app.ui.CalendarStatusScreen
import com.calinoti.app.ui.CalendarStatusTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val agendaApplication = application as AgendaApplication
        setContent {
            CalendarStatusTheme {
                CalendarStatusScreen(
                    calendarReader = agendaApplication.calendarReader,
                    notificationManager = agendaApplication.notificationManager,
                    userPreferencesRepository = agendaApplication.userPreferencesRepository,
                    refreshAgenda = agendaApplication::launchAgendaRefresh,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 권한 다이얼로그가 닫히거나 설정에서 돌아온 뒤 감시자 등록을 다시 시도한다 (멱등).
        (application as AgendaApplication).registerCalendarObserverIfPermitted()
    }
}
