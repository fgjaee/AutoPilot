plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fgjaee.autopilot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fgjaee.autopilot"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
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
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    compileOnly("de.robv.android.xposed:api:82")
}
