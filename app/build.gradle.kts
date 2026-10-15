import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.legacy.kapt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val yandexSalt = localProperties.getProperty("YANDEX_SALT") ?: "\"\""

android {
    namespace = "com.korenskoy.yandexmusic"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "YandexMusicLite.KorenskoyR"
        minSdk = 24
        targetSdk = 35
        versionCode = 10
        versionName = "1.3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        buildConfigField("String", "YANDEX_SALT", yandexSalt)

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    val keystorePath = localProperties.getProperty("KEYSTORE_FILE") ?: System.getenv("KEYSTORE_FILE") ?: "release.keystore"
    val releaseKeystore = rootProject.file(keystorePath)
    val keystorePassword = localProperties.getProperty("KEYSTORE_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
    val keyAlias = localProperties.getProperty("KEY_ALIAS") ?: System.getenv("KEY_ALIAS") ?: ""
    val keyPassword = localProperties.getProperty("KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD") ?: ""

    val hasValidSigning = releaseKeystore.exists() &&
        keystorePassword.isNotEmpty() &&
        keyAlias.isNotEmpty() &&
        keyPassword.isNotEmpty()

    signingConfigs {
        create("release") {
            if (hasValidSigning) {
                storeFile = releaseKeystore
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasValidSigning) {
                signingConfigs.getByName("release")
            } else {
                throw GradleException("Cannot build release APK without production keystore! Missing valid release.keystore or signing credentials.")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig = if (hasValidSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    androidResources {
        noCompress.addAll(listOf("tflite", "mp3"))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer.hls)

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Security & Navigation & Image Loading
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette.ktx)
    
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}