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
                    userPreferencesRepository = agendaApplication.userPreferencesRepository,
                    agendaRefresher = agendaApplication.agendaRefresher,
                )
            }
        }
    }
}
