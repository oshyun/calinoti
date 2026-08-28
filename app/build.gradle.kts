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
        // targetSdk 31+에서 시스템이 커스텀 알림에 아이콘·앱 이름 헤더를 입힌다(왼쪽 52dp 사용).
        //   아젠다 전체 폭보다 아이콘 표시를 택했다. 33+부턴 알림 권한을 런타임에 요청해야 한다.
        targetSdk = 35
        versionCode = 21
        versionName = "1.2.20260828133258"
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
    implementation(libs.androidx.work.runtime.ktx)
}
