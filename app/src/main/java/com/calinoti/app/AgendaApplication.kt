package com.calinoti.app

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import com.calinoti.app.data.CalendarReader
import com.calinoti.app.data.UserPreferencesRepository
import com.calinoti.app.notification.AgendaNotificationManager
import com.calinoti.app.notification.AgendaRemoteViewsFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 앱 전역 구성요소를 수동으로 조립해 보관한다.
 *
 * 갱신 트리거는 세 가지이고 전부 launchAgendaRefresh로 모인다:
 * - ContentObserver: 캘린더 앱에서 일정 변경 (프로세스가 살아 있는 동안만 동작)
 * - DataStore 감시: 설정 변경 — 갱신 의무를 개별 UI 콜백이 기억하지 않도록 여기서 강제한다
 * - 수동 새로고침(설정 화면 버튼)
 * 프로세스가 죽은 뒤의 갱신은 AgendaRefreshScheduler의 알람과 재부팅 리시버가 담당한다.
 */
class AgendaApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshRequested = AtomicBoolean(false)
    private var isCalendarObserverRegistered = false

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set
    lateinit var calendarReader: CalendarReader
        private set
    lateinit var notificationManager: AgendaNotificationManager
        private set
    lateinit var agendaRefresher: AgendaRefresher
        private set

    override fun onCreate() {
        super.onCreate()
        userPreferencesRepository = UserPreferencesRepository(this)
        calendarReader = CalendarReader(this)
        val remoteViewsFactory = AgendaRemoteViewsFactory(this)
        notificationManager = AgendaNotificationManager(this, remoteViewsFactory)
        agendaRefresher = AgendaRefresher(
            context = this,
            userPreferencesRepository = userPreferencesRepository,
            calendarReader = calendarReader,
            notificationManager = notificationManager,
        )

        notificationManager.ensureNotificationChannel()
        registerCalendarObserverIfPermitted()
        applicationScope.launch {
            // 설정 변경마다 갱신을 접수한다. 초기값도 한 번 접수되지만 겹침은 병합된다.
            // 감시가 죽으면 설정 변경이 알림에 반영되지 않으므로 수집 오류를 삼킨다.
            try {
                userPreferencesRepository.userPreferences.collect { launchAgendaRefresh() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (runtimeError: Exception) {
                Log.e(TAG, "설정 감시가 중단됐다", runtimeError)
            }
        }
        launchAgendaRefresh()
    }

    /**
     * 캘린더 변경 감시자를 권한이 생긴 뒤에 등록한다.
     * registerContentObserver는 등록 시 프로바이더를 열어, READ_CALENDAR이 없으면
     * SecurityException으로 프로세스가 죽는다 — 권한 없는 첫 실행 크래시의 원인이었다.
     * 멱등하므로 액티비티 resume마다 다시 불러도 된다.
     */
    fun registerCalendarObserverIfPermitted() {
        if (isCalendarObserverRegistered || !calendarReader.hasCalendarPermission()) return
        contentResolver.registerContentObserver(
            CalendarContract.Instances.CONTENT_URI,
            /* notifyForDescendants = */ true,
            calendarChangeObserver,
        )
        isCalendarObserverRegistered = true
    }

    /** 겹치는 갱신 요청을 하나로 합쳐 실행한다. refreshNow 자체는 Mutex로 직렬화된다. */
    fun launchAgendaRefresh() {
        if (!refreshRequested.compareAndSet(false, true)) return
        applicationScope.launch {
            while (refreshRequested.compareAndSet(true, false)) {
                try {
                    agendaRefresher.refreshNow()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (runtimeError: Exception) {
                    Log.e(TAG, "아젠다 알림 갱신에 실패했다", runtimeError)
                }
            }
        }
    }

    private val calendarChangeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            launchAgendaRefresh()
        }
    }

    private companion object {
        const val TAG = "AgendaApplication"
    }
}
