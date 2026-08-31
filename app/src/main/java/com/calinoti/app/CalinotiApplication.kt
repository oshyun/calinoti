package com.calinoti.app

import android.app.Application
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import com.calinoti.app.data.AppLocaleController
import com.calinoti.app.data.CalendarAppReader
import com.calinoti.app.data.CalendarReader
import com.calinoti.app.data.UserPreferencesRepository
import com.calinoti.app.notification.ImminentEventNotifier
import com.calinoti.app.notification.NotificationPublisher
import com.calinoti.app.notification.NotificationViewsFactory
import com.calinoti.app.update.ApkDownloader
import com.calinoti.app.update.AppUpdateController
import com.calinoti.app.update.GitHubReleaseClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 앱 전역 구성요소를 수동으로 조립해 보관한다.
 *
 * 갱신 트리거는 다섯 가지이고 전부 launchNotificationRefresh로 모인다:
 * - ContentObserver: 캘린더 앱에서 일정 변경 (프로세스가 살아 있는 동안만 동작)
 * - DataStore 감시: 설정 변경 — 갱신 의무를 개별 UI 콜백이 기억하지 않도록 여기서 강제한다
 * - onConfigurationChanged: 다크 테마·언어 전환 (프로세스가 살아 있는 동안만 동작)
 * - onResume 폴백: 구성 변경 미전달에 대비한 언어 변경 재확인 (MainActivity)
 * - 수동 새로고침(설정 화면 버튼)
 * 프로세스가 죽은 뒤의 갱신은 NotificationRefreshScheduler의 알람과 재부팅 리시버가 담당한다.
 */
class CalinotiApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshRequested = AtomicBoolean(false)
    private var isCalendarObserverRegistered = false

    /** 마지막으로 본 시스템 다크 테마. 전환 순간만 걸러 내기 위한 기준값이다. */
    private var lastSeenSystemNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED

    /** 마지막으로 본 유효 locale의 언어 태그. 언어 변경 순간만 걸러 내기 위한 기준값이다. */
    private var lastSeenLocaleTag: String? = null

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set
    lateinit var appLocaleController: AppLocaleController
        private set
    lateinit var calendarReader: CalendarReader
        private set
    lateinit var calendarAppReader: CalendarAppReader
        private set
    lateinit var notificationPublisher: NotificationPublisher
        private set
    lateinit var imminentEventNotifier: ImminentEventNotifier
        private set
    lateinit var notificationRefresher: NotificationRefresher
        private set
    lateinit var appUpdateController: AppUpdateController
        private set

    /** 설정 화면의 여백 미리보기가 실제 알림과 같은 조립 경로를 쓰게 공유한다. */
    lateinit var remoteViewsFactory: NotificationViewsFactory
        private set

    override fun onCreate() {
        super.onCreate()
        lastSeenSystemNightMode =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        lastSeenLocaleTag = resources.configuration.locales[0].toLanguageTag()
        userPreferencesRepository = UserPreferencesRepository(this)
        appLocaleController = AppLocaleController(this)
        calendarReader = CalendarReader(this)
        calendarAppReader = CalendarAppReader(this)
        remoteViewsFactory = NotificationViewsFactory(this)
        notificationPublisher = NotificationPublisher(this, remoteViewsFactory)
        imminentEventNotifier =
            ImminentEventNotifier(this, remoteViewsFactory, notificationPublisher)
        notificationRefresher = NotificationRefresher(
            context = this,
            userPreferencesRepository = userPreferencesRepository,
            calendarReader = calendarReader,
            notificationPublisher = notificationPublisher,
            imminentEventNotifier = imminentEventNotifier,
        )
        appUpdateController = AppUpdateController(
            context = this,
            gitHubReleaseClient = GitHubReleaseClient(),
            apkDownloader = ApkDownloader(this),
            controllerScope = applicationScope,
        )

        notificationPublisher.ensureNotificationChannel()
        registerCalendarObserverIfPermitted()
        applicationScope.launch {
            // 설정 변경마다 갱신을 접수한다. 초기값도 한 번 접수되지만 겹침은 병합된다.
            // 감시가 죽으면 설정 변경이 알림에 반영되지 않으므로 수집 오류를 기록하고 끝낸다.
            userPreferencesRepository.userPreferences
                .catch { runtimeError -> Log.e(TAG, "설정 감시가 중단됐다", runtimeError) }
                .collect { launchNotificationRefresh() }
        }
        launchNotificationRefresh()
    }

    /**
     * 다크 테마·언어 전환에 알림을 다시 그린다. RemoteViews는 조립 시점의 uiMode로 캘린더 색
     * 톤과 values-night 보조 텍스트 색을 굽고 문자열·날짜는 조립 시점의 locale로 풀기 때문에,
     * 카드 배경은 SystemUI가 새로 그려도 텍스트는 이전 구성에 묶여 있다. night mask와 유효
     * locale이 바뀐 경우만 걸러 회전·밀도 등 다른 구성 변경에는 반응하지 않게 한다.
     * 프로세스가 죽은 상태에서의 전환은 다음 알람까지 늦는다.
     */
    override fun onConfigurationChanged(newConfiguration: Configuration) {
        super.onConfigurationChanged(newConfiguration)
        val newSystemNightMode = newConfiguration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val nightModeChanged = newSystemNightMode != lastSeenSystemNightMode
        lastSeenSystemNightMode = newSystemNightMode
        if (nightModeChanged) launchNotificationRefresh()
        refreshIfLocaleChanged(newConfiguration)
    }

    /**
     * 유효 locale(앱 전용 언어 설정이 있으면 그것, 없으면 시스템 언어)이 바뀌면 채널 이름·설명을
     * 새 언어로 갱신하고 알림을 다시 게시한다. 앱 언어가 "시스템 기본"일 때의 시스템 언어
     * 변경까지 함께 잡히도록 앱에 전달된 구성의 첫 locale을 비교한다. onConfigurationChanged가
     * 구성 변경을 전달하지 않는 경우를 위한 폴백으로 MainActivity.onResume에서도 호출하며,
     * 두 경로가 [lastSeenLocaleTag]를 공유해 동시 호출에도 갱신은 한 번만 접수된다.
     */
    fun refreshIfLocaleChanged(latestConfiguration: Configuration) {
        val latestLocaleTag = latestConfiguration.locales[0].toLanguageTag()
        if (latestLocaleTag == lastSeenLocaleTag) return
        lastSeenLocaleTag = latestLocaleTag
        // 같은 채널 ID로 다시 만들면 이름·설명만 새 언어로 갱신된다(중요도 등 behavior는 유지).
        notificationPublisher.ensureNotificationChannel()
        launchNotificationRefresh()
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
    fun launchNotificationRefresh() {
        if (!refreshRequested.compareAndSet(false, true)) return
        applicationScope.launch {
            while (refreshRequested.compareAndSet(true, false)) {
                try {
                    notificationRefresher.refreshNow()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (runtimeError: Exception) {
                    Log.e(TAG, "일정 알림 갱신에 실패했다", runtimeError)
                }
            }
        }
    }

    private val calendarChangeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            launchNotificationRefresh()
        }
    }

    private companion object {
        const val TAG = "CalinotiApplication"
    }
}
