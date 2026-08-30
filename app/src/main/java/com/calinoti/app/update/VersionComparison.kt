package com.calinoti.app.update

/**
 * X.Y.YYMMDD.HHMMSS (또는 v 접두사가 붙은) 버전 문자열을 4개의 정수 파트로 분해한다.
 * 파싱에 실패하면 null을 반환한다.
 */
internal fun versionParts(raw: String): LongArray? {
    val trimmed = raw.trim().removePrefix("v").removePrefix("V")
    if (trimmed.isEmpty()) return null
    val tokens = trimmed.split(".")
    if (tokens.size != 4) return null
    val parts = LongArray(4)
    for (i in 0 until 4) {
        val num = tokens[i].toLongOrNull() ?: return null
        if (num < 0) return null
        parts[i] = num
    }
    return parts
}

/**
 * 원격(GitHub Releases) 버전이 설치된 버전보다 엄격히 새로운지 여부를 판별한다.
 * 형식: Major.Minor.YYMMDD.HHMMSS (각 요소를 앞자리부터 순차 비교).
 */
internal fun isRemoteVersionNewer(installed: String, remote: String): Boolean {
    val installedParts = versionParts(installed) ?: return false
    val remoteParts = versionParts(remote) ?: return false
    for (i in 0 until 4) {
        if (remoteParts[i] > installedParts[i]) return true
        if (remoteParts[i] < installedParts[i]) return false
    }
    return false
}
