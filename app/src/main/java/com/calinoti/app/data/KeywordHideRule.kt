package com.calinoti.app.data

import kotlinx.serialization.Serializable

/**
 * 키워드 감춤 규칙의 단어 조건 하나. 단어가 일정 제목 또는 캘린더 표시명 중 어느 하나에
 * 포함돼도 충족된다 — 매칭 대상을 고르는 UI 없이 단어 하나로 두 대상을 함께 본다.
 * [isExclude]가 false면 포함 조건(포함돼야 감춤), true면 예외 조건(포함되면 감추지 않음)이다.
 * 단어는 자유 텍스트이므로 직렬화는 JSON 단일 키(UserPreferencesRepository)로 한다.
 */
@Serializable
data class KeywordHideCondition(
    val id: Long,
    val keyword: String,
    val isExclude: Boolean = false,
)

/**
 * 키워드 감춤 규칙 하나. 포함 조건들이 **모두(AND)** 충족되고 예외 조건은 **어느 것도 충족되지
 * 않아야(NOT ANY)** 일정을 감춘다. 규칙 여러 개는 **OR**로 판정한다(어느 규칙 하나라도
 * 걸리면 감춤). 규칙마다 접힌·펼친 알림 적용 여부를 각자 저장한다(HiddenItemType 토글과 같은 구조).
 */
@Serializable
data class KeywordHideRule(
    val id: Long,
    val conditions: List<KeywordHideCondition>,
    val isHiddenWhenCollapsed: Boolean,
    val isHiddenWhenExpanded: Boolean,
) {
    /**
     * 규칙이 이 일정을 걸는가. 공백만 있는 조건은 무시하고, 유효한 포함 조건이 하나도 없으면
     * 거짓이다 — 조건이 없거나 예외 단어만 있는 규칙이 "모든 일정 감춤"이 되는 사고를 막는다.
     * 모든 활성 포함 조건을 만족하고, 활성 예외 조건 중 어느 것에도 걸리지 않아야 참이다.
     */
    fun matchesEvent(eventEntry: EventEntry): Boolean {
        val activeIncludeConditions = conditions.filter { !it.isExclude && it.keyword.isNotBlank() }
        val activeExcludeConditions = conditions.filter { it.isExclude && it.keyword.isNotBlank() }
        if (activeIncludeConditions.isEmpty()) return false
        return activeIncludeConditions.all { condition -> condition.matchesEvent(eventEntry) } &&
            activeExcludeConditions.none { condition -> condition.matchesEvent(eventEntry) }
    }

    /** 조건 충족 여부. 단어가 제목 또는 캘린더명에 포함(대소문자 무시)돼면 충족이다. */
    private fun KeywordHideCondition.matchesEvent(eventEntry: EventEntry): Boolean {
        val trimmedKeyword = keyword.trim()
        return eventEntry.title.contains(trimmedKeyword, ignoreCase = true) ||
            eventEntry.calendarDisplayName.contains(trimmedKeyword, ignoreCase = true)
    }
}

/**
 * 접힌 알림에서 이 일정을 감추는가. 접힌 뷰에 적용하기로 한 규칙만 대상이다.
 */
fun List<KeywordHideRule>.hidesEventInCollapsedView(eventEntry: EventEntry): Boolean =
    any { rule -> rule.isHiddenWhenCollapsed && rule.matchesEvent(eventEntry) }

/**
 * 펼친 알림에서 이 일정을 감추는가. [hidesEventInCollapsedView]와 독립이다 — 접힌 뷰에
 * 적용한 규칙을 펼친 뷰가 따라오지 않는다(HiddenItemType과 같은 규칙).
 */
fun List<KeywordHideRule>.hidesEventInExpandedView(eventEntry: EventEntry): Boolean =
    any { rule -> rule.isHiddenWhenExpanded && rule.matchesEvent(eventEntry) }

/**
 * 접힘/펼침 구분이 없는 곳(임박 실시간 알림, 시스템 헤더 subText·카운트다운)의 감춤 판정.
 * 규칙이 어느 한쪽에라도 체크돼 있으면 여기서도 감춘다 — 사용자가 "감춘다"고 한 일정이
 * 다른 표면으로 새어 나가지 않게 하기 위함이다.
 */
fun List<KeywordHideRule>.hidesEventAnywhere(eventEntry: EventEntry): Boolean =
    any { rule ->
        (rule.isHiddenWhenCollapsed || rule.isHiddenWhenExpanded) && rule.matchesEvent(eventEntry)
    }
