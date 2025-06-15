plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.mobileapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mobileapp"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    // Camera2 (или CameraX) — у вас уже есть:
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // ML Kit Face Detection
    implementation("com.google.mlkit:face-detection:16.1.5")

    // OkHttp + WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // cardview для закругления объектов
    implementation("androidx.cardview:cardview:1.0.0")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("com.airbnb.android:lottie:6.3.0")
    implementation(libs.lifecycle.service)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)



}