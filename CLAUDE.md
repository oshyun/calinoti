# Calinoti

## APK 배포 규칙

- 빌드 후 `app/build/outputs/apk/debug/app-debug.apk`를
  `~/Desktop/Calinoti-v<versionName>-debug.apk`로 복사해 사용자에게 전달한다.
- 데스크탑에 두는 파일 이름에는 항상 versionName을 포함한다 (예: `Calinoti-v1.2.1-debug.apk`).
- 새 APK를 복사할 때 같은 자리의 구버전 `Calinoti-v*-debug.apk` 파일은 삭제해
  어떤 파일이 최신인지 헷갈리지 않게 한다.
- 기능이나 동작이 바뀌는 빌드를 전달할 때는 versionCode/versionName을 bump한다.
  bump는 단독 커밋으로 "Bump to vX.Y.Z (versionCode N)" 형식의 메시지를 쓴다.
- bump 전에는 반드시 main 브랜치의 `app/build.gradle.kts`에서 현재
  versionCode/versionName을 확인하고 그보다 한 칸 올린다.
  다른 세션이 main에 bump를 머지했을 수 있으므로, 자신이 마지막으로 본 버전을
  기준으로 삼지 않는다 (main tree에서 `git show main:app/build.gradle.kts`로 확인).
- 화면 하단에 `v%1$s (%2$d)` 형식(`version_format`)으로 설치된 버전이 표시되므로,
  사용자가 구버전 APK를 받았는지 화면에서 바로 확인할 수 있게 안내한다.
