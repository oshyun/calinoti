package com.calinoti.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 화면 컬러의 단일 출처: 화면 코드는 컬러 값을 직접 두지 않고 컬러롤만 참조한다.
 * 다크 스킴 시인성 문제로 시스템 다크 모드와 무관하게 항상 라이트 스킴을 쓴다.
 */
@Composable
fun CalendarStatusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content,
    )
}
