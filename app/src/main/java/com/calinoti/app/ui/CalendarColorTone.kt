package com.calinoti.app.ui

import android.graphics.Color
import kotlin.math.roundToInt

/**
 * 캘린더 색 표준 톤의 단일 출처. 캘린더 앱이 저장한 색은 채도·명도가 제각각이라 그대로
 * 쓰면 어떤 색은 선명하고 어떤 색은 흐릿하다. 색조(hue)만 유지하고 채도·명도는 테마별
 * 표준 톤으로 통일해, 캘린더끼리는 색조로만 구분되는 균등한 팔레트를 만든다. 알림 카드
 * 제목과 설정 화면 색 점이 함께 쓴다.
 */
object CalendarColorTone {

    /** 라이트 배경(흰 알림 카드, 항상 라이트 스킴인 앱 화면) 위 표준 명도. */
    private const val LIGHT_TONE_LIGHTNESS = 0.40f

    /**
     * 다크 배경(알림 카드) 위 표준 명도. One UI는 커스텀 뷰 카드를 표준 알림보다 연하게
     * 그려(다크에서도 밝은 회색 계열) 여유를 두고 올려 잡는다.
     */
    private const val DARK_TONE_LIGHTNESS = 0.68f

    /** 표준 채도. 선명함을 통일하는 값으로, 캘린더 앱 원본의 채도는 버려진다. */
    private const val STANDARD_SATURATION = 0.70f

    /**
     * 이 이하 채도는 회색 계열로 본다. 회색은 색조가 정의되지 않아(HSL 변환에서 빨강
     * 방향 0이 나옴) 표준 채도를 강제하면 회색 캘린더가 빨갛게 물든다 — 원본 채도를
     * 유지하고 명도만 표준으로 바꾼다.
     */
    private const val GRAY_SATURATION_LIMIT = 0.15f

    /**
     * 캘린더 색을 표준 톤으로 바꾼다. 색조는 원본을 유지해 캘린더 색 사이의 구분이
     * 남는다. 이미 표준 톤이면 원본을 그대로 돌려준다.
     */
    fun standardizeCalendarColor(color: Int, isDarkTheme: Boolean): Int {
        val (hue, saturation, lightness) = rgbToHsl(color)
        val standardizedSaturation =
            if (saturation < GRAY_SATURATION_LIMIT) saturation else STANDARD_SATURATION
        val standardizedLightness =
            if (isDarkTheme) DARK_TONE_LIGHTNESS else LIGHT_TONE_LIGHTNESS
        if (standardizedSaturation == saturation && standardizedLightness == lightness) return color
        return hslToRgb(
            hue = hue,
            saturation = standardizedSaturation,
            lightness = standardizedLightness,
            alpha = Color.alpha(color),
        )
    }

    /** 색을 0..1 범위의 색상·채도·명도로 분해한다. 표준 톤으로 조정하기 위한 변환이다. */
    private fun rgbToHsl(color: Int): Triple<Float, Float, Float> {
        val red = Color.red(color) / 255f
        val green = Color.green(color) / 255f
        val blue = Color.blue(color) / 255f
        val maxChannel = maxOf(red, green, blue)
        val minChannel = minOf(red, green, blue)
        val lightness = (maxChannel + minChannel) / 2f
        if (maxChannel == minChannel) return Triple(0f, 0f, lightness)
        val channelRange = maxChannel - minChannel
        val saturation = if (lightness > 0.5f) {
            channelRange / (2f - maxChannel - minChannel)
        } else {
            channelRange / (maxChannel + minChannel)
        }
        val huePortion = when (maxChannel) {
            red -> (green - blue) / channelRange + if (green < blue) 6f else 0f
            green -> (blue - red) / channelRange + 2f
            else -> (red - green) / channelRange + 4f
        }
        return Triple(huePortion / 6f, saturation, lightness)
    }

    /** 0..1 범위의 색상·채도·명도를 알파를 유지한 색으로 되돌린다. */
    private fun hslToRgb(hue: Float, saturation: Float, lightness: Float, alpha: Int): Int {
        if (saturation == 0f) {
            val grayChannel = (lightness * 255f).roundToInt()
            return Color.argb(alpha, grayChannel, grayChannel, grayChannel)
        }
        // 결과 채널 값의 상한·하한. 표준 HSL 변환식의 q·p에 해당한다.
        val resultMaxChannel =
            if (lightness < 0.5f) lightness * (1f + saturation)
            else lightness + saturation - lightness * saturation
        val resultMinChannel = 2f * lightness - resultMaxChannel

        fun channelFromHue(huePortion: Float): Float {
            val wrappedHuePortion = when {
                huePortion < 0f -> huePortion + 1f
                huePortion > 1f -> huePortion - 1f
                else -> huePortion
            }
            return when {
                wrappedHuePortion < 1f / 6f ->
                    resultMinChannel + (resultMaxChannel - resultMinChannel) * 6f * wrappedHuePortion
                wrappedHuePortion < 1f / 2f -> resultMaxChannel
                wrappedHuePortion < 2f / 3f ->
                    resultMinChannel +
                        (resultMaxChannel - resultMinChannel) * (2f / 3f - wrappedHuePortion) * 6f
                else -> resultMinChannel
            }
        }
        return Color.argb(
            alpha,
            (channelFromHue(hue + 1f / 3f) * 255f).roundToInt(),
            (channelFromHue(hue) * 255f).roundToInt(),
            (channelFromHue(hue - 1f / 3f) * 255f).roundToInt(),
        )
    }
}
