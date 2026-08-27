# Calinoti

## 리뷰 정책

- 빠른 개발 기간 동안 빌드 전후의 simplify·code-review 스킬 실행을 생략한다.
- 이 항목은 사용자가 필요한 시점에 직접 삭제한다 (삭제 시점을 Claude가 판단·제안하지 않는다).

## APK 배포 규칙

- 빌드 후 `app/build/outputs/apk/debug/app-debug.apk`를
  `~/Desktop/Calinoti-v<versionName>-debug.apk`로 복사해 사용자에게 전달한다.
- 데스크탑에 두는 파일 이름에는 항상 versionName을 포함한다 (예: `Calinoti-v1.2.1-debug.apk`).
- 새 APK를 복사할 때 같은 자리의 구버전 `Calinoti-v*-debug.apk` 파일은 삭제해
  어떤 파일이 최신인지 헷갈리지 않게 한다.
- 기능이나 동작이 바뀌는 빌드를 전달할 때는 versionCode/versionName을 bump한다.
  bump는 단독 커밋으로 "Bump to vX.Y.Z (versionCode N)" 형식의 메시지를 쓴다.
- 화면 하단에 `v%1$s (%2$d)` 형식(`version_format`)으로 설치된 버전이 표시되므로,
  사용자가 구버전 APK를 받았는지 화면에서 바로 확인할 수 있게 안내한다.
