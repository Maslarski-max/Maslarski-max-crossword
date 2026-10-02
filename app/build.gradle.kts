import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// google-services.json comes from the Firebase console and is git-ignored. Without it the app builds and runs
// with Analytics/Crashlytics disabled (see FirebaseTelemetry), so contributors don't need Firebase access.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

fun loadProperties(name: String): Properties = Properties().apply {
    rootProject.file(name).takeIf { it.exists() }?.inputStream()?.use(::load)
}

val localProperties = loadProperties("local.properties")

/** -Pkey=value, then local.properties, then environment (UPPER_SNAKE_CASE), then gradle.properties. */
fun config(key: String): String? =
    gradle.startParameter.projectProperties[key]
        ?: localProperties.getProperty(key)
        ?: System.getenv(key.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase())?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(key) as String?)

// Release signing: keystore.properties (git-ignored) or CROSSWORD_KEYSTORE_* environment variables (CI / fastlane).
val keystoreProperties = loadProperties("keystore.properties")
fun signing(key: String, env: String): String? = keystoreProperties.getProperty(key) ?: System.getenv(env)?.takeIf { it.isNotBlank() }
val releaseStoreFile = signing("storeFile", "CROSSWORD_KEYSTORE_FILE")

val playLicenseKey = config("playLicenseKey")?.trim().orEmpty()

android {
    namespace = "com.maslarski.crossword"
    // Compose BOM 2026.09 needs the API 37.2 SDK to compile; the app still targets (and is tested on) API 36.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.maslarski.crossword"
        minSdk = 26
        targetSdk = 36
        versionCode = (config("versionCode") ?: "1").toInt()
        versionName = config("versionName") ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        manifestPlaceholders["admobAppId"] = requireNotNull(config("admobAppId"))
        buildConfigField("long", "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", "${config("playIntegrityCloudProjectNumber") ?: "0"}L")
        // Base64 RSA public key from Play Console > Monetization setup > Licensing; purchases are verified against it.
        buildConfigField("String", "PLAY_LICENSE_KEY", "\"$playLicenseKey\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", hasFirebaseConfig.toString())
        // Hashed device id printed by the UMP SDK in logcat; lets debug builds force the EEA consent form.
        buildConfigField("String", "UMP_TEST_DEVICE_ID", "\"${config("umpTestDeviceId") ?: ""}\"")
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signing("storePassword", "CROSSWORD_KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "CROSSWORD_KEY_ALIAS")
                keyPassword = signing("keyPassword", "CROSSWORD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Unsigned bundles can still be produced (and signed later by Play App Signing upload tooling).
            signingConfig = if (releaseStoreFile != null) signingConfigs.getByName("release") else null
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        // Version-freshness checks are handled by dependency updates, not lint. targetSdk stays on 36 (the current
        // Play requirement) even though compileSdk is 37.2, so OldTargetApi is expected; mipmap-anydpi-v26 is kept for clarity.
        disable += setOf("ObsoleteSdkInt", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.play.integrity)
    implementation(libs.play.billing)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// Release builds must be able to verify Play purchases; without the key buyers would be charged and get nothing.
val checkPlayLicenseKey by tasks.registering {
    val key = playLicenseKey
    doLast {
        val valid = runCatching {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key)))
        }.isSuccess
        if (!valid) {
            throw GradleException(
                "playLicenseKey / PLAY_LICENSE_KEY is missing or not a valid Base64 RSA public key " +
                    "(Play Console > Monetize > Monetization setup > Licensing).",
            )
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkPlayLicenseKey) }
