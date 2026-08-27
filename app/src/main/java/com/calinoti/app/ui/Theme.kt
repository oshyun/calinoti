package com.calinoti.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 앱 정체성에 맞춘 다크 스킴 — 런처 코랄(#F2A08E)과 알림 크림 옐로우(#ECE9A9)와
 * 같은 웜 계열로, 기본 보라 다크 스킴이 앱의 따뜻한 무드와 어긋나던 것을 맞춘다.
 * 화면 컬러의 단일 출처: 화면 코드는 컬러 값을 직접 두지 않고 컬러롤만 참조한다.
 */
private val WarmCoralDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4A3),
    onPrimary = Color(0xFF5C1F17),
    primaryContainer = Color(0xFF7C3328),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFFEBC2B4),
    onSecondary = Color(0xFF432C24),
    // FilterChip 선택 칩 배경으로도 쓰이는 톤 — 배경보다 한 단계 밝게.
    secondaryContainer = Color(0xFF5C4139),
    onSecondaryContainer = Color(0xFFFFDAD3),
    // 알림 제목 accent(#ECE9A9)와 같은 크림 옐로우 계열.
    tertiary = Color(0xFFE7E2AC),
    onTertiary = Color(0xFF403C14),
    tertiaryContainer = Color(0xFF585328),
    onTertiaryContainer = Color(0xFFF4F8BF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    // 순회색 대신 살짝 레드 기운을 띤 웜 블랙 — 코랄 팔레트의 바탕.
    background = Color(0xFF181211),
    onBackground = Color(0xFFEDE0DB),
    surface = Color(0xFF181211),
    onSurface = Color(0xFFEDE0DB),
    surfaceVariant = Color(0xFF2C221F),
    onSurfaceVariant = Color(0xFFD0C2BC),
    surfaceTint = Color(0xFFFFB4A3),
    outline = Color(0xFFA28D86),
    outlineVariant = Color(0xFF4F413C),
    // Card(surfaceContainerLow)가 배경 위로 은은하게 뜨는 단차.
    surfaceContainerLowest = Color(0xFF120E0D),
    surfaceContainerLow = Color(0xFF1F1816),
    surfaceContainer = Color(0xFF241C1A),
    surfaceContainerHigh = Color(0xFF2F2624),
    surfaceContainerHighest = Color(0xFF3A302E),
    surfaceDim = Color(0xFF130F0E),
    surfaceBright = Color(0xFF4A3F3D),
    inverseSurface = Color(0xFFEDE0DB),
    inverseOnSurface = Color(0xFF362F2D),
    inversePrimary = Color(0xFF8F4939),
    scrim = Color(0xFF000000),
)

/** 라이트는 Compose 기본 스킴을 유지한다 — 요청 범위는 다크 화면 개선이다. */
@Composable
fun CalendarStatusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) WarmCoralDarkColorScheme else lightColorScheme(),
        content = content,
    )
}
