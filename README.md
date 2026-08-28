# Calinoti

**Calinoti** = *cal(endar) in noti(fication) bar*.

캘린더 일정을 안드로이드 알림 드로어에 아젠다 형태로 띄워,
어느 화면·어떤 앱을 쓰다가도 알림만 내려 한눈에 일정을 확인하게 해주는 앱.

## 기능 (v1)

- 다가오는 일정을 **지속 알림**으로 표시 (접힘: 3개 요약 / 펼침: 최대 설정 개수)
  - 알림 전체 폭을 아젠다가 사용 — targetSdk 31 이상은 시스템이 알림 왼쪽에 앱 아이콘 열(52dp)을 강제 예약하므로, 이를 피하려고 targetSdk 30에 고정했다 (Play 스토어 배포 불가, Android 13+ 알림 권한 자동 부여)
- 날짜 그룹 헤더 (`08.31, 금요일` 형식) + 항목 레이아웃 `[시간][제목(굵게)][위치(회색)]`
  - 종일 일정은 시간 칸 생략, 위치 없는 일정은 위치 칸 생략
- 일정 항목 행·알림 여백(공백)을 눌렀을 때 열 곳을 각각 지정 —
  기본값(시스템 선택) / Calinoti 열기 / 캘린더 앱 지정 (지정 앱 목록에는 동명 앱 구분용으로 패키지 이름 표시)
- 표시할 캘린더 선택 (초기 상태 = 모든 캘린더, 모두 해제하면 빈 알림)
- 표시 기간(3/7/14/30일), 최대 항목 수(5/10/15), 알림 고정(밀어도 즉시 다시 나타남) 설정
- 알림 여백 설정(날짜/일정 항목 앞 여백, 날짜-일정·일정 사이·날짜 사이 간격, 0~24dp) —
  변경 즉시 알림에 반영
- 갱신 시점: 일정 변경 시(프로세스 생존 중), 다음 일정 시작·종료 시각, 자정, 재부팅 직후

## 요구사항

- JDK 17
- Android SDK (compileSdk 35)
- Gradle 8.9 (wrapper 포함)

## 빌드 · 설치

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Android Studio에서 열어도 된다 (JDK 17 필요).

## 프로젝트 구조

```
app/src/main/java/com/calinoti/app/
├── AgendaApplication.kt        # 수동 DI 조립, 캘린더 변경 감시(ContentObserver)
├── AgendaRefresher.kt          # 설정 → 쿼리 → 알림 → 예약을 묶은 갱신 진입점
├── MainActivity.kt             # Compose 설정 화면 진입
├── data/
│   ├── AgendaModels.kt         # AgendaEntry / AgendaListEntry / UserCalendar
│   ├── CalendarReader.kt       # CalendarContract 쿼리 (캘린더 목록, Instances 범위 검색)
│   ├── AgendaListBuilder.kt    # 일정 목록에 날짜 헤더 끼워 넣기
│   ├── NotificationSpacing.kt  # 알림 여백 설정 값 (기본값, 조절 범위)
│   └── UserPreferencesRepository.kt  # DataStore 설정 저장소
├── notification/
│   ├── AgendaNotificationManager.kt  # 채널 생성, 알림 발행, 권한 확인, 클릭 PendingIntent
│   └── AgendaRemoteViewsFactory.kt   # 알림 RemoteViews 조립
├── scheduling/
│   ├── AgendaRefreshScheduler.kt     # AlarmManager 다음 갱신 예약
│   └── AgendaRefreshReceiver.kt      # 알람 수신 → 갱신, 재부팅(BOOT_COMPLETED) 복원
└── ui/                          # Compose 테마와 설정 화면
```

## 향후 아이디어

- Google Tasks 통합
- 레이아웃 밀도·요소 순서 커스터마이징, Today/Tomorrow 표기 옵션
- 상태바 아이콘에 날짜 표시, 알림 액션 버튼
- 완료된 일정 회색 처리

## 라이선스

학습 목적으로 자유롭게 사용한다.
