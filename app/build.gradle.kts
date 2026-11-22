import java.util.Properties

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.app.haven"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.app.haven"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "USDA_API_KEY", "\"${localProps.getProperty("USDA_API_KEY", "")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
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
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    //Firebase
    implementation(platform("com.google.firebase:firebase-bom:35.0.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")      // profile pictures; version comes from the BOM above
    implementation("com.google.firebase:firebase-messaging")    // push notifications (FCM), e.g. when a roommate adds an expense
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.navigation:navigation-compose:2.9.0")   // use the newest version Android Studio suggests
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    // Room (local database)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)       // coroutines + Flow support
    ksp(libs.androidx.room.compiler)             // generates the DAO/database code at build time
    // Coil (loads images from URLs, e.g. profile pictures)
    implementation(libs.coil.compose)            // AsyncImage for Compose
    implementation(libs.coil.network.okhttp)     // Coil 3 needs this to download images over the network
    // Open Food Facts (look up a scanned grocery barcode)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")                       // HTTP requests; same version Coil already uses
    // CameraX + ML Kit (barcode scanner screen)
    implementation("androidx.camera:camera-camera2:1.6.2")                     // CameraX on top of the phone's camera hardware
    implementation("androidx.camera:camera-lifecycle:1.6.2")                   // starts/stops the camera with the screen's lifecycle
    implementation("androidx.camera:camera-compose:1.6.2")                     // CameraXViewfinder: camera preview as a composable (no Android View)
    implementation("androidx.camera:camera-mlkit-vision:1.6.2")                // MlKitAnalyzer: passes camera frames to ML Kit
    implementation("com.google.mlkit:barcode-scanning:17.3.0")                 // ML Kit barcode reader, bundled so it works offline right away
    implementation("com.google.zxing:core:3.5.3")                              // makes QR code images (ML Kit can only read them)
    testImplementation("org.json:json:20240303")                               // real org.json for unit tests (Android's is only a stub there)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2") // runTest + test dispatchers for ViewModel tests
}