package com.calinoti.app

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.calinoti.app.data.CalendarReader
import com.calinoti.app.data.UserPreferencesRepository
import com.calinoti.app.notification.AgendaNotificationManager
import com.calinoti.app.notification.AgendaRemoteViewsFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 앱 전역 구성요소를 수동으로 조립해 보관한다.
 * 캘린더 변경 감시(ContentObserver)는 프로세스가 살아 있는 동안만 동작하며,
 * 프로세스가 죽은 뒤의 갱신은 AgendaRefreshScheduler의 알람과 재부팅 리시버가 담당한다.
 */
class AgendaApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set
    lateinit var calendarReader: CalendarReader
        private set
    lateinit var agendaRefresher: AgendaRefresher
        private set

    private var lastObservedRefreshAtMilliseconds = 0L

    override fun onCreate() {
        super.onCreate()
        userPreferencesRepository = UserPreferencesRepository(this)
        calendarReader = CalendarReader(this)
        val remoteViewsFactory = AgendaRemoteViewsFactory(this)
        val notificationManager = AgendaNotificationManager(this, remoteViewsFactory)
        agendaRefresher = AgendaRefresher(
            context = this,
            userPreferencesRepository = userPreferencesRepository,
            calendarReader = calendarReader,
            notificationManager = notificationManager,
        )

        notificationManager.ensureNotificationChannel()
        contentResolver.registerContentObserver(
            CalendarContract.Instances.CONTENT_URI,
            /* notifyForDescendants = */ true,
            calendarChangeObserver,
        )
        applicationScope.launch { agendaRefresher.refreshNow() }
    }

    /** 연속된 변경 알림을 스로틀해 불필요한 재갱신을 줄인다. */
    private val calendarChangeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            val now = System.currentTimeMillis()
            if (now - lastObservedRefreshAtMilliseconds < CHANGE_THROTTLE_MILLIS) return
            lastObservedRefreshAtMilliseconds = now
            applicationScope.launch { agendaRefresher.refreshNow() }
        }
    }

    private companion object {
        const val CHANGE_THROTTLE_MILLIS = 1000L
    }
}
