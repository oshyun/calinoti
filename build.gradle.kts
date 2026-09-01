// 루트 빌드 스크립트: 플러그인 버전 선언만 담당하고 실제 적용은 각 모듈에서 한다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
