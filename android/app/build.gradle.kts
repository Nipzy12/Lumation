plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lumavun.lumation"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lumavun.lumation"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(project(":ffmpeg"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
