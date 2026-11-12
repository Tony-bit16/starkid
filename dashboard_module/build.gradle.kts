import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fridge.dashboard"
    compileSdk = 36
    // 本机只安装了 Android 36.1 平台；使用 minor 版本避免额外下载 platform 36。
    compileSdkMinor = 1
    // AGP 默认查找 Build Tools 35.0.0，本机仅安装 36.0.0/36.1.0，显式锁定。
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.fridge.dashboard"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // 看板图表：按约束固定使用 MPAndroidChart（JitPack 仓库已在 settings.gradle.kts 限定）。
    implementation(libs.mpandroidchart)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
