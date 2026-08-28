package com.calinoti.app.data

import android.content.Context
import android.content.pm.PackageManager

/** 설정 화면의 캘린더 앱 선택 목록에 보여줄 설치 앱 한 개. */
data class InstalledCalendarApp(val packageName: String, val label: String)

/** 알림 클릭 대상이 될 수 있는 설치 캘린더 앱을 읽어온다. */
class CalendarAppReader(private val context: Context) {

    /**
     * 일정 상세(ACTION_VIEW + 캘린더 content URI)를 열 수 있는 설치 앱 목록.
     * 라벨순이되 같은 라벨(예: 서로 다른 제조사의 "캘린더")은 패키지 이름순으로 구분해 정렬한다.
     * 이 인텐트는 AndroidManifest의 <queries>와 같아서, 여기 걸린 패키지는
     * getLaunchIntentForPackage로도 조회 가능하다(API 30+ 패키지 가시성).
     */
    fun loadInstalledCalendarApps(): List<InstalledCalendarApp> {
        val packageManager = context.packageManager
        return packageManager
            .queryIntentActivities(
                CalendarIntents.buildEventViewIntent(CalendarIntents.EVENT_PROBE_ID),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            // 앱마다 여러 액티비티가 걸릴 수 있어 패키지 단위로 겹치는 항목을 버린다.
            .distinctBy { it.activityInfo.packageName }
            .map { resolveInfo ->
                InstalledCalendarApp(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString()
                        .ifEmpty { resolveInfo.activityInfo.packageName },
                )
            }
            .sortedWith(
                compareBy<InstalledCalendarApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
                    .thenComparator { first, second ->
                        first.packageName.compareTo(second.packageName)
                    },
            )
    }
}
