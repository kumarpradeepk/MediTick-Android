import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing lives outside git: keystore.properties (gitignored) points
// at keystore/meditick-release.jks (also gitignored). Absent on a fresh
// checkout or CI without the secret — release builds fall back to being
// unsigned rather than failing configuration, so debug work is unaffected.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun quotedBuildConfig(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
val aiScanEndpoint = providers.environmentVariable("AI_SCAN_ENDPOINT").orNull
    ?: localProperties.getProperty("ai.scan.endpoint", "")
val aiScanClientToken = providers.environmentVariable("AI_SCAN_CLIENT_TOKEN").orNull
    ?: localProperties.getProperty("ai.scan.clientToken", "")

android {
    namespace = "com.kabi.pillpal.meditick"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kabi.pillpal.meditick"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"

        buildConfigField("String", "AI_SCAN_ENDPOINT", quotedBuildConfig(aiScanEndpoint))
        buildConfigField("String", "AI_SCAN_CLIENT_TOKEN", quotedBuildConfig(aiScanClientToken))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    // MediTick has an in-app language picker, so every translation must be
    // present in the installed app. Play's default bundle behaviour installs
    // only the device's own locales, which would make the picker silently fall
    // back to English for anything else.
    bundle {
        language {
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.compose.ui:ui:1.9.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.5")
    implementation("androidx.compose.foundation:foundation:1.9.5")
    implementation("androidx.compose.animation:animation:1.9.5")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    // Scan to Add: CameraX preview/analysis + on-device ML Kit OCR.
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.5")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
