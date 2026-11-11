plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.myoslatal.languageforge.uikit"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    api("androidx.appcompat:appcompat:1.7.1")
    api("androidx.core:core:1.17.0")
}