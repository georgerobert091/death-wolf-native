plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.deathwolf.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.deathwolf.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2-native-voice-input"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("deathwolf-release.jks")
            storePassword = System.getenv("SIGNING_PASSWORD")
            keyAlias = System.getenv("SIGNING_ALIAS")
            keyPassword = System.getenv("SIGNING_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.fragment:fragment:1.9.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("com.github.msnilsen:openwakeword-android:0.1.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-recaptcha")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
