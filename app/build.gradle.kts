plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.simplelauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.simplelauncher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    // 固定の署名鍵。CI では GitHub の Secrets から渡される。
    // 鍵がない環境（手元のビルドなど）では、デバッグ用の鍵で署名する。
    val keystorePath = System.getenv("SIGNING_KEYSTORE")
    val keystorePassword = System.getenv("SIGNING_PASSWORD")
    val hasReleaseKey = !keystorePath.isNullOrBlank() && file(keystorePath).exists() &&
        !keystorePassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = "simplelauncher"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        // 普段使いはこちら。デバッグ用の処理が外れ、R8 で最適化されるので動きが軽い
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // 自分用なので、リリースビルド時の lint は省く（依存の取得失敗でビルドが落ちるのを避ける）
    lint { checkReleaseBuilds = false }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
