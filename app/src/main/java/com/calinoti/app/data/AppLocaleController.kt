package com.calinoti.app.data

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * 화면의 언어 설정(표준 per-app language, API 33+) 읽기·쓰기. 저장·적용·액티비티 재생성은
 * 시스템이 담당하므로 이 클래스는 LocaleManager API만 감싼다. DataStore에 이중 저장하지
 * 않는다 — 시스템 저장값이 단일 출처이다.
 *
 * API 33 미만은 per-app language가 없어 지원 여부가 거짓이고, 앱은 values-en 번역을 통해
 * 시스템 언어를 그대로 따른다.
 */
class AppLocaleController(private val context: Context) {

    /** API 33 미만은 앱 언어 저장소가 없어 설정 UI를 노출하지 않는다. */
    val isLanguageSelectionSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * 앱 전용 locale 목록. 빈 목록이면 앱 전용 설정이 없다는 뜻으로 시스템 언어를 따른다.
     * "시스템 기본" 옵션의 선택 상태 판정도 이 빈 목록으로 한다.
     */
    fun currentAppLocales(): LocaleList {
        if (!isLanguageSelectionSupported) return LocaleList.getEmptyLocaleList()
        return localeManager().applicationLocales
    }

    /**
     * 앱 언어를 바꾼다. null이면 시스템 기본으로 되돌린다(빈 locale 목록 저장).
     * 시스템이 즉시 저장하고 configuration change로 화면을 재생성하므로 호출부는
     * 낙관적 상태를 두지 않는다.
     */
    fun selectAppLocale(languageTag: String?) {
        if (!isLanguageSelectionSupported) return
        localeManager().applicationLocales =
            if (languageTag == null) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(languageTag)
    }

    // LocaleManager 클래스는 API 33에 추가됐다. 위 두 함수가 지원 여부 검사 뒤에만
    // 이 참조를 실행하므로 구버전에서 클래스 적재가 일어나지 않는다.
    private fun localeManager(): LocaleManager =
        context.getSystemService(LocaleManager::class.java)
}
