package com.calinoti.app.data

/**
 * 알림 카드 배경색 묶음. 라이트·다크 테마 각각의 배경색을 담는다. null은 사용자가 지정하지
 * 않았다는 뜻이며 그 테마의 기본값 리소스(R.color.notification_card_background)를 쓴다.
 * 지정색이 없을 때의 기본값 resolve 규칙은 NotificationViewsFactory 한 곳이 소유한다.
 */
data class NotificationBackgroundColors(
    /** 라이트 테마에서 쓸 배경색(ARGB). null이면 라이트 기본값 리소스를 쓴다. */
    val lightThemeArgb: Int?,
    /** 다크 테마에서 쓸 배경색(ARGB). null이면 다크 기본값 리소스를 쓴다. */
    val darkThemeArgb: Int?,
)
