plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.userback.example"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.userback.example"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "USERBACK_TOKEN", "\"P-munRw6sN7ExmKIuAwNvumliFy\"")
            buildConfigField("String", "USERBACK_API_URL", "\"https://api.userback.ngrok.app/\"")
            buildConfigField("String", "USERBACK_EVENTS_URL", "\"https://events.userback.ngrok.app\"")
            buildConfigField("String", "USERBACK_BASE_URL", "\"https://app.userback.ngrok.app\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Replace these with your actual production URLs
            buildConfigField("String", "USERBACK_TOKEN", "\"YOUR_PRODUCTION_TOKEN\"")
            buildConfigField("String", "USERBACK_API_URL", "\"https://api.userback.io/\"")
            buildConfigField("String", "USERBACK_EVENTS_URL", "\"https://events.userback.io\"")
            buildConfigField("String", "USERBACK_BASE_URL", "\"https://static.userback.io\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":userback-sdk"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.okhttp)
}
