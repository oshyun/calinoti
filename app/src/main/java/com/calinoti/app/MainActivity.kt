package com.calinoti.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.pm.PackageInfoCompat
import com.calinoti.app.ui.CalendarStatusScreen
import com.calinoti.app.ui.CalendarStatusTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val agendaApplication = application as AgendaApplication
        // 화면은 파라미터만 받게 한다 — 버전 조회(IPC)는 컴포지션 밖 여기서 한 번만.
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionLabel = getString(
            R.string.version_format,
            packageInfo.versionName,
            PackageInfoCompat.getLongVersionCode(packageInfo),
        )
        setContent {
            CalendarStatusTheme {
                CalendarStatusScreen(
                    calendarReader = agendaApplication.calendarReader,
                    calendarAppReader = agendaApplication.calendarAppReader,
                    notificationManager = agendaApplication.notificationManager,
                    remoteViewsFactory = agendaApplication.remoteViewsFactory,
                    userPreferencesRepository = agendaApplication.userPreferencesRepository,
                    appLocaleController = agendaApplication.appLocaleController,
                    versionLabel = versionLabel,
                    refreshAgenda = agendaApplication::launchAgendaRefresh,
                    openAppSettings = ::startAppDetailsSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val agendaApplication = application as AgendaApplication
        // 시스템 설정 쪽에서 언어를 바꾸고 돌아온 경우를 흡수한다. 구성 변경이
        // Application까지 전달됐다면 기준값이 이미 갱신돼 아무 일도 일어나지 않는다 (멱등).
        agendaApplication.refreshIfLocaleChanged(resources.configuration)
        // 권한 다이얼로그가 닫히거나 설정에서 돌아온 뒤 감시자 등록을 다시 시도한다 (멱등).
        agendaApplication.registerCalendarObserverIfPermitted()
    }

    /** 알림 권한을 영구 거부한 사용자를 위한 탈출구: 이 앱의 시스템 설정 화면을 연다. */
    private fun startAppDetailsSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }
}
