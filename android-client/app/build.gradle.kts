plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.aetherlink"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.aetherlink"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("shared") {
            val systemDebugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")
            if (systemDebugKeystore.exists()) {
                storeFile = systemDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            } else {
                val keystoreFile = file("aetherlink.keystore")
                storeFile = keystoreFile
                storePassword = "aetherlink_sign_key"
                keyAlias = "aetherlink"
                keyPassword = "aetherlink_sign_key"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

    // Networking (WebSocket) & JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Google Play Services Code Scanner (No camera permission needed, official QR scanner)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    debugImplementation(libs.androidx.ui.tooling)
}
