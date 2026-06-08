plugins {
    id("com.android.application")
}

android {
    namespace = "com.camerastreamer"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.camerastreamer"
        minSdk = 23
        targetSdk = 23
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
