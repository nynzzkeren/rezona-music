import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

if (file("google-services.json").exists()) {
  apply(plugin = "com.google.gms.google-services")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
  FileInputStream(keystorePropertiesFile).use { stream ->
    keystoreProperties.load(stream)
  }
}

android {
  namespace = "com.nayama.music"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.nayama.music"
    minSdk = 24
    targetSdk = 35
    versionCode = 2
    versionName = "1.0.1"
  }

  signingConfigs {
    create("releaseKey") {
      if (keystorePropertiesFile.exists()) {
        storeFile = file(keystoreProperties.getProperty("storeFile"))
        storePassword = keystoreProperties.getProperty("storePassword")
        keyAlias = keystoreProperties.getProperty("keyAlias")
        keyPassword = keystoreProperties.getProperty("keyPassword")
      } else {
        storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    debug {
      signingConfig = signingConfigs.getByName("releaseKey")
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("releaseKey")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  lint {
    checkReleaseBuilds = false
    abortOnError = false
    ignoreWarnings = true
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Networking
  implementation(libs.retrofit.core)
  implementation(libs.retrofit.converter.kotlinx.serialization)
  implementation(libs.okhttp.core)
  implementation(libs.okhttp.logging)
  implementation(libs.kotlinx.serialization.json)

  // Local Persistence (DataStore & Room)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // Image Loading
  implementation(libs.coil.compose)

  // Desugaring & Streaming Engine
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
  implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1")

  // Media3 / ExoPlayer Audio Engine
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)
  implementation(libs.media3.common)
  implementation(libs.media3.ui)

  // Liquid Glass & Hardware-Accelerated Blur
  implementation(libs.haze)
  implementation(libs.haze.materials)

  // Firebase (Auth, Firestore, App Check Play Integrity) & Coroutines Play Services
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.appcheck.playintegrity)
  implementation(libs.kotlinx.coroutines.play.services)

  // Credential Manager & Google ID (Sign in with Google)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services.auth)
  implementation(libs.googleid)

  // Background Offline-First Sync
  implementation(libs.androidx.work.runtime.ktx)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation("org.json:json:20240303")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
