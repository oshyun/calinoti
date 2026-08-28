# Calinoti

## 리뷰 정책

- 빠른 개발 기간 동안 빌드 전후의 simplify·code-review 스킬 실행을 생략한다.
- 이 항목은 사용자가 필요한 시점에 직접 삭제한다 (삭제 시점을 Claude가 판단·제안하지 않는다).

## APK 배포 규칙

- APK 빌드는 작업 브랜치의 main 머지가 끝난 뒤 main tree에서 실행한다.
  머지 전 worktree에서 미리 빌드한 APK는 전달하지 않는다.
- 머지가 완료되면 APK 생성 여부를 묻지 않는다. bump부터 빌드·Desktop 복사까지
  확인 없이 자동으로 한 흐름으로 진행한다.
- 빌드 후 `app/build/outputs/apk/debug/app-debug.apk`를
  `~/Desktop/Calinoti-v<versionName>-debug.apk`로 복사해 사용자에게 전달한다.
- 데스크탑에 두는 파일 이름에는 항상 versionName을 포함한다 (예: `Calinoti-v1.2.1-debug.apk`).
- 새 APK를 복사할 때 같은 자리의 구버전 `Calinoti-v*-debug.apk` 파일은 삭제해
  어떤 파일이 최신인지 헷갈리지 않게 한다.
- 기능이나 동작이 바뀌는 빌드를 전달할 때는 versionCode/versionName을 bump한다.
  bump는 단독 커밋으로 "Bump to vX.Y.Z (versionCode N)" 형식의 메시지를 쓴다.
- bump는 사용자가 머지를 결정한 뒤 그 시점에 진행한다 (작업 브랜치 작업 중 미리
  올려두지 않는다).
- bump 커밋을 main에 직접 올리지 않는다. 작업 브랜치에 bump 커밋을 추가한 뒤
  그 브랜치를 main으로 머지한다 — bump는 머지와 함께 들어간다.
- bump 직전에는 반드시 main 브랜치의 `app/build.gradle.kts`에서 현재
  versionCode/versionName을 확인하고 그보다 한 칸 올린다.
  다른 세션이 main에 bump를 머지했을 수 있으므로, 자신이 마지막으로 본 버전을
  기준으로 삼지 않는다 (main tree에서 `git show main:app/build.gradle.kts`로 확인).
- 화면 하단에 `v%1$s (%2$d)` 형식(`version_format`)으로 설치된 버전이 표시되므로,
  사용자가 구버전 APK를 받았는지 화면에서 바로 확인할 수 있게 안내한다.
