import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

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
            buildConfigField("String", "USERBACK_TOKEN", "\"${localProps.getProperty("USERBACK_TOKEN", "")}\"")
            buildConfigField("String", "USERBACK_API_URL", "\"${localProps.getProperty("USERBACK_API_URL", "https://api.userback.io/")}\"")
            buildConfigField("String", "USERBACK_EVENTS_URL", "\"${localProps.getProperty("USERBACK_EVENTS_URL", "https://events.userback.io")}\"")
            buildConfigField("String", "USERBACK_BASE_URL", "\"${localProps.getProperty("USERBACK_BASE_URL", "https://static.userback.io")}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "USERBACK_TOKEN", "\"${localProps.getProperty("USERBACK_TOKEN", "")}\"")
            buildConfigField("String", "USERBACK_API_URL", "\"${localProps.getProperty("USERBACK_API_URL", "https://api.userback.io/")}\"")
            buildConfigField("String", "USERBACK_EVENTS_URL", "\"${localProps.getProperty("USERBACK_EVENTS_URL", "https://events.userback.io")}\"")
            buildConfigField("String", "USERBACK_BASE_URL", "\"${localProps.getProperty("USERBACK_BASE_URL", "https://static.userback.io")}\"")
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
