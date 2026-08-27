package com.calinoti.app.data

/**
 * 알림 화면의 여백 설정. 단위는 dp.
 * 간격은 "뒤 항목의 paddingTop이 담당" 규칙으로 적용되므로 세 수직 간격이 서로 독립적이다.
 */
data class NotificationSpacing(
    /** 날짜 헤더 항목 앞(시작 쪽) 여백. */
    val dayHeaderStartPaddingDp: Int,
    /** 일정 항목 앞(시작 쪽) 여백. */
    val eventStartPaddingDp: Int,
    /** 날짜 헤더와 그 아래 첫 일정 사이 간격. */
    val dayHeaderToEventSpacingDp: Int,
    /** 같은 날짜 그룹 안 일정 사이 간격. */
    val betweenEventsSpacingDp: Int,
    /** 날짜 그룹(헤더) 사이 간격. */
    val betweenDayHeadersSpacingDp: Int,
) {
    companion object {
        /** 슬라이더와 저장값 클램프가 공유하는 조절 범위의 단일 출처. */
        val RANGE_DP = 0..24

        // 기본값은 v1.2.4까지 레이아웃 XML에 하드코딩되던 값의 시각 등가다.
        // 수직 간격은 인접 항목 패딩의 합으로 렌더링되었으므로 헤더(2)+일정(4)=6, 일정(4)+일정(4)=8,
        // 일정(4)+헤더(8)=12를 옮겨왔다.
        val DEFAULTS = NotificationSpacing(
            dayHeaderStartPaddingDp = 16,
            eventStartPaddingDp = 16,
            dayHeaderToEventSpacingDp = 6,
            betweenEventsSpacingDp = 8,
            betweenDayHeadersSpacingDp = 12,
        )
    }
}
