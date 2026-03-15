plugins {
  id("com.android.application") version "8.13.2"
}

dependencies {
  implementation("androidx.core:core:1.16.0")
  implementation("dev.rikka.shizuku:api:13.1.5")
  implementation("dev.rikka.shizuku:provider:13.1.5")
}

android {
  namespace = "juloo.sysconsole"
  compileSdkVersion = "android-35"

  defaultConfig {
    applicationId = "juloo.sysconsole"
    minSdk = 24
    targetSdk { version = release(35) }
    versionCode = 1
    versionName = "1.0.0"
  }

  sourceSets {
    named("main") {
      manifest.srcFile("AndroidManifest.xml")
      java.srcDirs("srcs")
      res.srcDirs("res")
    }
  }

  signingConfigs {
    named("debug") {
      storeFile = file(System.getenv("DEBUG_KEYSTORE") ?: "debug.keystore")
      storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "debug0"
      keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "debug"
      keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "debug0"
    }
  }

  buildTypes {
    named("debug") {
      isMinifyEnabled = false
      isDebuggable = true
      applicationIdSuffix = ".debug"
      resValue("string", "app_name", "System Console (Debug)")
      signingConfig = signingConfigs["debug"]
    }
    named("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      resValue("string", "app_name", "System Console")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}
