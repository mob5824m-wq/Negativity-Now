plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.negativitynow.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.negativitynow.app"
        // Android 4.0 (Ice Cream Sandwich, API 14) and newer.
        minSdk = 14
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // NotificationCompat gives Android 4.x devices the best available notification UI,
    // including action buttons on Android 4.1+.
    implementation("androidx.core:core:1.12.0")
}
