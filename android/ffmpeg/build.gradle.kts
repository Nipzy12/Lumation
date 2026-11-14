plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lumavun.lumation.ffmpeg"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        ndk {
            abiFilters += listOf("arm64-v8a")
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
    // Non-GPL maintained Android fork. Version intentionally pinned.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")
    testImplementation("junit:junit:4.13.2")
}
