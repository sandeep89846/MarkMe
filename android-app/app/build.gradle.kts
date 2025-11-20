plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.markme.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.markme.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        // REVERTED: Back to Java 8
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        // REVERTED: Back to Java 8
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.kotlinComposeCompiler.get()
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("com.markme.data")
        }
    }
}

dependencies {
    // --- Core Android & Compose ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)


    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)


    // --- Networking ---
    implementation(libs.retrofit.main)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp.main)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi.kotlin)

    // --- Background Sync ---
    implementation(libs.androidx.workmanager)

    // --- Local Database ---
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines)

    // --- Security ---
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    // --- Google Sign-In ---
    implementation(libs.play.services.auth)

    implementation(libs.play.services.location)

    // 2. ZXing (Zebra Crossing) for QR Scanning
    implementation(libs.zxing.android.embedded)

    // --- ViewModel for Compose ---
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // --- ADD THIS FOR ICONS ---
    implementation(libs.androidx.material.icons.extended)

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}