plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// CI passes the run number so every published build outranks the last; a local
// build stays at 1, which is only ever installed by hand.
val buildVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
val buildVersionName = "0.3.$buildVersionCode"

// Release signing is configured only when CI supplies the keystore. Without it the
// release build falls back to debug signing, so a local `assembleRelease` still works.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
val keyAlias: String? = System.getenv("KEY_ALIAS")
val keyPassword: String? = System.getenv("KEY_PASSWORD")
val hasReleaseSigning = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

android {
    namespace = "com.sacredtxd.connectionchecker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sacredtxd.connectionchecker"
        minSdk = 24
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            // Addressed by tag, not /releases/latest/: that path resolves only to a
            // non-prerelease release, and the rolling build is published as a prerelease.
            "\"https://github.com/SacredTxd/Connectionchecker/releases/download/latest/version.json\"",
        )
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Left off deliberately: shrinking Compose and kotlinx.serialization is a
            // runtime-failure risk that cannot be caught without a device to test on.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
