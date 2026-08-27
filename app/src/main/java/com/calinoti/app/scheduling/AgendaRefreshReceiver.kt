package com.calinoti.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calinoti.app.AgendaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** AlarmManager 예약 등의 트리거를 받아 아젠다 알림을 갱신한다. */
open class AgendaRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                (context.applicationContext as AgendaApplication).agendaRefresher.refreshNow()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
