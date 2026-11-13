import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.hblaze.apexy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hblaze.apexy"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.7.0"
    }

    flavorDimensions += "distribution"
    productFlavors {
        // GitHub releases: everything, including the MediaPipe models for Photo match and snore detection.
        create("full") {
            dimension = "distribution"
        }
        // F-Droid: no prebuilt Google ML libraries, so Photo match and snore detection are left out.
        create("foss") {
            dimension = "distribution"
        }
    }

    splits {
        abi {
            // One APK per CPU: arm64 for real phones (the POCO), x86_64 for the emulator.
            // -PnoAbiSplits builds one universal APK instead, as F-Droid does.
            isEnable = !project.hasProperty("noAbiSplits")
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    androidResources {
        noCompress += "tflite"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    "fullImplementation"("com.google.mediapipe:tasks-vision:0.10.20") {
        // MediaPipe leaks the AutoValue annotation processor onto the runtime classpath.
        exclude(group = "com.google.auto.value", module = "auto-value")
    }
    "fullImplementation"("com.google.mediapipe:tasks-audio:0.10.20") {
        exclude(group = "com.google.auto.value", module = "auto-value")
    }
}

// Baseline profiles come out slightly different from build to build, which breaks
// F-Droid's reproducible-build check, so the foss flavor ships without them.
tasks.configureEach {
    if (name.contains("Foss") && name.contains("ArtProfile")) enabled = false
}
