#!/usr/bin/env bash
set -euo pipefail

# GitHub Releases 배포 스크립트
# 사용법: ./scripts/publish-release.sh [--dry-run] [--force] [--version <versionName>] [--apk <path>]

DRY_RUN=0
FORCE=0
VERSION_OVERRIDE=""
APK_OVERRIDE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)
            DRY_RUN=1
            shift
            ;;
        --force)
            FORCE=1
            shift
            ;;
        --version)
            VERSION_OVERRIDE="$2"
            shift 2
            ;;
        --apk)
            APK_OVERRIDE="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [--dry-run] [--force] [--version <versionName>] [--apk <path>]"
            exit 0
            ;;
        *)
            echo "Error: Unknown argument '$1'" >&2
            exit 1
            ;;
    esac
done

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# 1. 브랜치 및 작업 트리 확인
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "main" && "$FORCE" -eq 0 ]]; then
    echo "Error: Current branch is '$CURRENT_BRANCH'. Releases must be published from 'main' (use --force to override)." >&2
    exit 1
fi

if ! git diff-index --quiet HEAD -- && [[ "$FORCE" -eq 0 ]]; then
    echo "Error: Working directory has uncommitted changes. Please commit or stash them (use --force to override)." >&2
    exit 1
fi

# 2. versionName 및 APK 경로 결정
if [[ -n "$VERSION_OVERRIDE" ]]; then
    VERSION_NAME="$VERSION_OVERRIDE"
else
    VERSION_NAME="$(grep -E 'versionName[[:space:]]*=' app/build.gradle.kts | head -n 1 | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/')"
fi

if [[ -z "$VERSION_NAME" ]]; then
    echo "Error: Could not determine versionName from app/build.gradle.kts" >&2
    exit 1
fi

if [[ -n "$APK_OVERRIDE" ]]; then
    APK_PATH="$APK_OVERRIDE"
else
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

if [[ ! -f "$APK_PATH" ]]; then
    echo "Error: APK file '$APK_PATH' not found. Run ./gradlew assembleDebug first." >&2
    exit 1
fi

TAG="v$VERSION_NAME"
ASSET_NAME="Calinoti-v$VERSION_NAME-debug.apk"

# 3. 토큰 조회 (git credential helper - osxkeychain)
CRED_OUTPUT="$(printf "protocol=https\nhost=github.com\n\n" | GIT_TERMINAL_PROMPT=0 git credential fill 2>/dev/null || true)"
TOKEN="$(echo "$CRED_OUTPUT" | awk -F= '/^password=/ {print $2}')"

if [[ -z "$TOKEN" ]]; then
    echo "Error: GitHub PAT not found in keychain credential helper." >&2
    echo "Please save your token with: printf 'protocol=https\nhost=github.com\nusername=<user>\npassword=<PAT>\n' | git credential approve" >&2
    exit 1
fi

# 4. 저장소 slug 도출
REMOTE_URL="$(git remote get-url origin)"
SLUG="$(echo "$REMOTE_URL" | sed -E 's#(https://github.com/|git@github.com:)([^/]+/[^/.]+)(\.git)?#\2#')"

if [[ -z "$SLUG" || "$SLUG" != *"/"* ]]; then
    echo "Error: Could not derive GitHub repository slug from remote URL '$REMOTE_URL'" >&2
    exit 1
fi

echo "Repository: $SLUG"
echo "Version:    $VERSION_NAME"
echo "Tag:        $TAG"
echo "Asset:      $ASSET_NAME"
echo "APK Path:   $APK_PATH"

# 5. 기존 릴리스 확인
RELEASE_CHECK="$(curl -s -w "\n%{http_code}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/$SLUG/releases/tags/$TAG")"
CHECK_CODE="$(echo "$RELEASE_CHECK" | tail -n 1)"
CHECK_BODY="$(echo "$RELEASE_CHECK" | sed '$d')"

if [[ "$CHECK_CODE" -eq 200 ]]; then
    if [[ "$FORCE" -eq 1 ]]; then
        EXISTING_ID="$(echo "$CHECK_BODY" | grep -E '"id"[[:space:]]*:[[:space:]]*[0-9]+' | head -n 1 | sed -E 's/.*"id"[[:space:]]*:[[:space:]]*([0-9]+).*/\1/')"
        echo "Existing release found (ID: $EXISTING_ID). Deleting due to --force..."
        if [[ "$DRY_RUN" -eq 0 ]]; then
            curl -s -X DELETE \
                -H "Authorization: Bearer $TOKEN" \
                -H "Accept: application/vnd.github+json" \
                "https://api.github.com/repos/$SLUG/releases/$EXISTING_ID" >/dev/null
            curl -s -X DELETE \
                -H "Authorization: Bearer $TOKEN" \
                -H "Accept: application/vnd.github+json" \
                "https://api.github.com/repos/$SLUG/git/refs/tags/$TAG" >/dev/null || true
            git push --delete origin "$TAG" 2>/dev/null || true
        fi
    else
        echo "Error: Release '$TAG' already exists on $SLUG. Use --force to replace." >&2
        exit 1
    fi
fi

if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "[DRY-RUN] Release publication dry-run completed successfully."
    exit 0
fi

# 6. 릴리스 생성 (POST /repos/$SLUG/releases)
echo "Creating release $TAG..."
RELEASE_PAYLOAD="$(printf '{"tag_name":"%s","target_commitish":"main","name":"%s","draft":false,"prerelease":false}' "$TAG" "$TAG")"

RELEASE_RES="$(curl -s -w "\n%{http_code}" -X POST \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -H "Content-Type: application/json" \
    "https://api.github.com/repos/$SLUG/releases" \
    -d "$RELEASE_PAYLOAD")"

CREATE_CODE="$(echo "$RELEASE_RES" | tail -n 1)"
CREATE_BODY="$(echo "$RELEASE_RES" | sed '$d')"

if [[ "$CREATE_CODE" -ne 201 ]]; then
    echo "Error: Failed to create release (HTTP $CREATE_CODE):" >&2
    echo "$CREATE_BODY" >&2
    exit 1
fi

UPLOAD_URL="$(echo "$CREATE_BODY" | grep -E '"upload_url":' | head -n 1 | sed -E 's/.*"upload_url"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/' | sed -E 's/\{[^\}]*\}//')"

if [[ -z "$UPLOAD_URL" ]]; then
    echo "Error: Could not extract upload_url from release response." >&2
    exit 1
fi

# 7. 에셋 업로드
echo "Uploading asset $ASSET_NAME..."
UPLOAD_RES="$(curl -s -w "\n%{http_code}" -X POST \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary "@$APK_PATH" \
    "$UPLOAD_URL?name=$ASSET_NAME")"

UPLOAD_CODE="$(echo "$UPLOAD_RES" | tail -n 1)"
UPLOAD_BODY="$(echo "$UPLOAD_RES" | sed '$d')"

if [[ "$UPLOAD_CODE" -ne 201 ]]; then
    echo "Error: Failed to upload asset (HTTP $UPLOAD_CODE):" >&2
    echo "$UPLOAD_BODY" >&2
    exit 1
fi

# 8. 비인증 GET /releases/latest 로 검증
echo "Verifying latest release..."
LATEST_RES="$(curl -s -w "\n%{http_code}" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/$SLUG/releases/latest")"

LATEST_CODE="$(echo "$LATEST_RES" | tail -n 1)"
LATEST_BODY="$(echo "$LATEST_RES" | sed '$d')"

if [[ "$LATEST_CODE" -ne 200 ]]; then
    echo "Warning: /releases/latest returned HTTP $LATEST_CODE" >&2
else
    DOWNLOAD_URL="$(echo "$LATEST_BODY" | grep -E '"browser_download_url":' | head -n 1 | sed -E 's/.*"browser_download_url"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')"
    echo "Release published successfully!"
    echo "Download URL: $DOWNLOAD_URL"
fi
