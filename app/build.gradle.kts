plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.madar.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.madar.launcher"
        // Built for realme GT 8 Pro (Android 16); 33 makes media permissions and themed icons unconditional.
        minSdk = 33
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
    }

    // The one key every installed Madar build carries; see keystore/README.md. Never committed.
    val madarKeystore = file(System.getenv("MADAR_KEYSTORE") ?: rootProject.file("keystore/madar.jks").path)
    signingConfigs {
        create("madar") {
            storeFile = madarKeystore
            storePassword = System.getenv("MADAR_KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("MADAR_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("MADAR_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("madar")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
            it.systemProperty("robolectric.dependency.repo.id", "gcs-central")
            it.systemProperty("madar.screens", rootProject.file("screens").absolutePath)
            it.maxHeapSize = "3g"
        }
    }
}

// Refuse to sign a release with any other key: the phone would reject it as an update.
tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst {
        val ks = android.signingConfigs.getByName("madar").storeFile
        check(ks != null && ks.exists()) { "Missing keystore/madar.jks — restore the Madar signing key first (see keystore/README.md)." }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
