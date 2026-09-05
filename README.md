# Calinoti

**Calinoti** = *cal(endar) in noti(fication) bar*.

캘린더 일정을 안드로이드 알림 드로어에 목록 형태로 띄워,
어느 화면·어떤 앱을 쓰다가도 알림만 내려 한눈에 일정을 확인하게 해주는 앱.

## 기능 (v1)

- 다가오는 일정을 **지속 알림**으로 표시 (접힘: 3개 요약 / 펼침: 최대 설정 개수)
  - 알림 전체 폭을 일정 목록이 사용 — targetSdk 31 이상은 시스템이 알림 왼쪽에 앱 아이콘 열(52dp)을 강제 예약하므로, 이를 피하려고 targetSdk 30에 고정했다 (Play 스토어 배포 불가, Android 13+ 알림 권한 자동 부여)
- 날짜 그룹 헤더 (`08.31, 금요일` 형식) + 항목 레이아웃 `[시간/기간][제목(굵게)][위치(회색)]`
  - 종일 일정은 기간 표시(하루: "종일", 여러 날: "n일간"), 위치 없는 일정은 위치 칸 생략
- 상태바 알림 아이콘이 오늘 날짜를 표시 (날짜별 벡터 31종, 자정 갱신 때 바뀜 —
  `tools/generate_notification_icons.py`로 생성)
- 일정 항목 행·알림 여백(공백)을 눌렀을 때 열 곳을 각각 지정 —
  기본값(시스템 선택) / Calinoti 열기 / 캘린더 앱 지정 (지정 앱 목록에는 동명 앱 구분용으로 패키지 이름 표시)
- 표시할 캘린더 선택 (초기 상태 = 모든 캘린더, 모두 해제하면 빈 알림)
- 알림에서 감추기: 하루종일 일정 상태별 감춤(오늘 시작/진행 중/예정/지난) + 연속 하루종일 일정 감추기(N일 이상 이어지는 종일 일정을 접힌/펼친 알림에서 각각 감춤, 0이면 끔) + 단어 감춤 규칙 —
  제목·캘린더명에 단어가 모두 포함된 일정을 감춘다(조건 AND, 규칙 OR, 임박 실시간 알림에도 적용).
  접힌 알림과 펼친 알림에 각각 적용할 수 있고 설정 화면에서 규칙에 걸리는 일정을 실시간 미리보기로 확인한다
- 표시 기간(3/7/14/30일), 최대 항목 수(5/10/15), 알림 고정(밀어도 즉시 다시 나타남) 설정
- 알림 여백 설정(날짜/일정 항목 앞 여백, 날짜-일정·일정 사이·날짜 사이 간격, 알림 위아래
  바깥 여백, 0~24dp) — 변경 즉시 알림에 반영
- 언어: 한국어·영어 지원. 안드로이드 13+에서는 설정 화면(또는 시스템 설정의 앱 언어)에서
  선택하며 알림·화면에 즉시 반영, 그 이하는 시스템 언어를 따른다
- 알림 갱신 주기 설정(10분~24시간, 기본 6시간) — 일정 변화 없는 구간의 안전망 갱신 간격을 조절
  (절전 모드에서는 시스템 제한으로 앱당 15분 간격으로 늘어질 수 있음)
- 배터리 최적화 제외 요청(권한 관리 섹션) — 절전 중 알람 스로틀·프로세스 정리를 면제해 갱신 보호
- 시작 1시간 미만 일정은 실시간 카운트다운 표시 — 시스템 Chronometer가 알람 없이 초 단위로 갱신
- 갱신 시점: 일정 변경 시(프로세스 생존 중), 다음 일정 시작·종료 시각, 일정 시작 1시간 전
  (카운트다운 전환), 자정, 재부팅 직후, 설정한 안전망 갱신 주기마다
- 앱 내 업데이트 확인: 설정 화면 하단의 "앱 정보" 섹션에서 설치된 버전을 확인하고 GitHub Releases의 최신 버전을 조회하며, 새 버전이 있으면 다운로드 및 설치를 진행합니다.
  - 앱 설치를 위해 "알 수 없는 앱 설치"(`REQUEST_INSTALL_PACKAGES`) 권한 허용이 필요하며, 권한이 없을 경우 설정 화면으로 자동 안내됩니다.
- 설정 내보내기·가져오기: 설정 전체를 YAML 파일로 저장하거나, 내보낸 파일로 현재 설정을 되돌립니다
  ("설정 내보내기 · 가져오기" 섹션). 내보내기 전에는 저장될 YAML을 미리 보고, 가져오기 전에는 파일 내용을
  확인할 수 있습니다. 언어 설정은 제외되고, 캘린더 선택의 ID는 기기마다 다른 캘린더를 가리킬 수 있습니다.

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
├── CalinotiApplication.kt        # 수동 DI 조립, 캘린더 변경 감시(ContentObserver)
├── NotificationRefresher.kt          # 설정 → 쿼리 → 알림 → 예약을 묶은 갱신 진입점
├── MainActivity.kt             # Compose 설정 화면 진입
├── data/
│   ├── EventModels.kt         # EventEntry / EventListEntry / UserCalendar
│   ├── CalendarReader.kt       # CalendarContract 쿼리 (캘린더 목록, Instances 범위 검색)
│   ├── EventListBuilder.kt    # 일정 목록에 날짜 헤더 끼워 넣기
│   ├── NotificationSpacing.kt  # 알림 여백 설정 값 (기본값, 조절 범위)
│   └── UserPreferencesRepository.kt  # DataStore 설정 저장소
├── notification/
│   ├── NotificationPublisher.kt  # 채널 생성, 알림 발행, 권한 확인, 클릭 PendingIntent
│   └── NotificationViewsFactory.kt   # 알림 RemoteViews 조립
├── scheduling/
│   ├── NotificationRefreshScheduler.kt     # AlarmManager 다음 갱신 예약
│   └── NotificationRefreshReceiver.kt      # 알람 수신 → 갱신, 재부팅(BOOT_COMPLETED) 복원
├── update/
│   ├── ApkDownloader.kt        # 릴리스 APK 다운로드 및 캐시 관리
│   ├── AppUpdateController.kt  # 업데이트 상태 머신 및 설치 인텐트 조립
│   ├── GitHubReleaseClient.kt  # GitHub Releases API 최신 릴리스 조회
│   └── VersionComparison.kt    # 버전 문자열 파싱 및 비교
└── ui/                          # Compose 테마와 설정 화면
```

## 향후 아이디어

- Google Tasks 통합
- 레이아웃 밀도·요소 순서 커스터마이징, Today/Tomorrow 표기 옵션
- 알림 액션 버튼
- 완료된 일정 회색 처리

## 라이선스

학습 목적으로 자유롭게 사용한다.
