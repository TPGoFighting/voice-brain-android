plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "top.tpgofighting.voicebrain"
    compileSdk = 35

    defaultConfig {
        applicationId = "top.tpgofighting.voicebrain"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE")
            val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "voicebrain"
            val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: keystorePassword

            if (!keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            } else {
                // 回退使用内置 debug 签名，确保 release 构建零障碍可运行
                initWith(signingConfigs.getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // 网络与 WebSocket 全双工流
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 高斯模糊（底部导航栏毛玻璃）：Haze 1.3.1 与当前 Compose BOM 2024.12（Compose 1.7）同期配套；
    // Haze 2.x 面向 Compose 1.12，不跟随升级以免连锁升 BOM。
    implementation("dev.chrisbanes.haze:haze:1.3.1")
    // 批次 I 第三方库（均按 Kotlin 2.0.21 / Compose 1.7 兼容线选版）：
    // Lottie（Airbnb 官方 Compose 绑定）播放简笔画线条动画素材
    implementation("com.airbnb.android:lottie-compose:6.6.10")
    // Lucide 线性图标库（composables.com 出品）：全 App 图标统一为简笔画线条风
    implementation("com.composables:icons-lucide:1.1.0")
    // mikepenz Markdown 渲染器（Material 3 版）：Agent 回答与纪要成稿的富文本渲染；
    // 0.27.0 恰构建于 Kotlin 2.0.21，与本项目编译器同线
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.27.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
