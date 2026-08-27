plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.calinoti.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.calinoti.app"
        minSdk = 26
        // QUIRK(android12-notification-decor): targetSdk 31 이상에서 시스템이 커스텀 알림에
        //   왼쪽 아이콘 열(52dp)을 강제 예약한다. 알림 전체 폭을 아젠다가 쓰려면 30 이하여야 한다.
        //   대가로 Play 스토어 배포는 불가능해지고 Android 13+에서 알림 권한이 설치 시 자동 부여된다.
        // QUIRK-REMOVE-WHEN: 커스텀 알림 뷰의 전체 폭 렌더링을 허용하는 공개 API가 나왔을 때
        targetSdk = 30
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
}
