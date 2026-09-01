package com.calinoti.app.data

/**
 * 알림 화면의 여백 설정. 단위는 dp.
 * 수직 간격은 "뒤 항목의 paddingTop이 담당" 규칙으로 적용되므로 수직 간격끼리 서로 독립적이다.
 * 날짜와 일정 사이 여백만 유일한 수평 간격이다 — 일정은 날짜 오른쪽 같은 x에서 시작하므로
 * 일정 항목 자체의 시작 여백은 따로 없다.
 */
data class NotificationSpacing(
    /** 알림 콘텐츠 맨 위와 맨 아래 바깥 여백. 한 값이 위아래 양쪽에 함께 적용된다. */
    val outerVerticalPaddingDp: Int,
    /** 날짜 헤더 항목 앞(시작 쪽) 여백. */
    val dayHeaderStartPaddingDp: Int,
    /** 날짜와 일정 사이 수평 간격(일정 열의 시작 여백). */
    val dayHeaderToEventSpacingDp: Int,
    /** 같은 날짜 그룹 안 일정 사이 수직 간격. */
    val betweenEventsSpacingDp: Int,
    /** 날짜 그룹(헤더) 사이 수직 간격. */
    val betweenDayHeadersSpacingDp: Int,
    /** 일정 줄 안에서 시각과 제목 사이 수평 간격. */
    val timeToTitleSpacingDp: Int,
) {
    companion object {
        /** 슬라이더와 저장값 클램프가 공유하는 조절 범위의 단일 출처. */
        val RANGE_DP = 0..24

        // 기본값은 사용자가 설정 화면에서 고른 배치 값이다.
        val DEFAULTS = NotificationSpacing(
            outerVerticalPaddingDp = 0,
            dayHeaderStartPaddingDp = 0,
            dayHeaderToEventSpacingDp = 5,
            betweenEventsSpacingDp = 0,
            betweenDayHeadersSpacingDp = 0,
            timeToTitleSpacingDp = 3,
        )
    }
}
