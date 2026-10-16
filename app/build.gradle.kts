import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.byronhung.firstlight"
    // Android 16.1: the platform installed here. Play needs new apps to target 36 or higher.
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }

    defaultConfig {
        // The Play Store ID, under the studio name. The code's own package (namespace) stays as it is.
        applicationId = "com.plainworks.firstlight"
        // 29 (Android 10): every lock-screen and full-screen-alarm API we need exists without fallbacks.
        minSdk = 29
        targetSdk = 36
        versionCode = 11
        versionName = "0.11"
        // Closed test: every tester gets Plus, so the Plus puzzles and skies get real mornings too.
        // Turn off (or delete) when Google Play Billing goes in (P11).
        buildConfigField("boolean", "TEST_PLUS", "true")
    }

    // Release key lives outside the repo; keystore.properties (git-ignored) says where.
    val keystoreFile = rootProject.file("keystore.properties")
    val releaseSigning = if (keystoreFile.exists()) {
        val props = Properties().apply { keystoreFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        release {
            // R8: strips unused library code. Cuts the APK from ~44 MB to a few.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
        // Same shrunk code as release, installable next to the everyday debug build, with the
        // adb test receiver. For proving R8 didn't break anything before an APK goes out.
        create("releaseCheck") {
            initWith(getByName("release"))
            applicationIdSuffix = ".check"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    sourceSets.getByName("releaseCheck") {
        java.srcDir("src/debug/java")
        manifest.srcFile("src/debug/AndroidManifest.xml")
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
        // BuildConfig.DEBUG gates the Plus debug switch in Settings (until Google Play Billing).
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // QR round (chunk 11, parked): camera preview + barcode reading. The Play-services flavour of
    // ML Kit downloads its model on demand instead of bundling ~19 MB of native code in the APK.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    // Asks Play services to fetch the barcode model at registration, so 6am works offline.
    implementation(libs.play.services.base)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
