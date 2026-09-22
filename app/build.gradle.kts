plugins {
    // AGP 9 has built-in Kotlin support; the standalone kotlin-android plugin
    // is no longer applied (and is actively rejected).
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ekaur.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ekaur.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 43
        versionName = "0.18.7"

        // The GitHub repo the in-app updater reads releases from.
        buildConfigField("String", "UPDATE_REPO", "\"HakkanShah/Ek-Aur\"")

        // The project's public address and its publishable key. Both are meant
        // to ship inside the app -- they identify the project, they are not
        // credentials, and row level security is what actually protects the
        // data. Anyone can read them out of any Supabase app's APK.
        buildConfigField("String", "SUPABASE_URL", "\"https://vgfsuwhhuuodvoyntqbf.supabase.co\"")
        buildConfigField(
            "String",
            "SUPABASE_KEY",
            "\"sb_publishable_70DYlsA3sLbQiOIO3Tx_jw_fGI-zNts\"",
        )
    }

    // A stable, committed keystore. The build container is ephemeral, so a
    // regenerated debug key would produce APKs that refuse to install over the
    // previous build -- forcing an uninstall (and wiping the local database)
    // on every update. Sideload-only app, never published.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/ekaur.jks")
            storePassword = "ekaurdev"
            keyAlias = "ekaur"
            keyPassword = "ekaurdev"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
