plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.darkveil.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.darkveil.app"
    minSdk = 26
    // targetSdk 34：Android 15+ 对高 targetSdk 的应用有额外前台服务/录屏限制，34 为已验证组合
    targetSdk = 34
    versionCode = 5
    versionName = "2.0.0"
  }

  signingConfigs {
    // 仓库内固定 debug 签名：命令行构建不依赖 ~/.android/debug.keystore
    create("repoDebug") {
      storeFile = rootProject.file("keystore/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("repoDebug")
    }
    debug {
      signingConfig = signingConfigs.getByName("repoDebug")
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

  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }
}

dependencies {
  implementation(platform("androidx.compose:compose-bom:2024.02.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.activity:activity-compose:1.8.2")
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
