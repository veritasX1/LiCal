import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "io.github.veritasx1.lical"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.veritasx1.lical"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (keystoreProperties.isNotEmpty()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Tests check the German texts (the source language) – regardless of the computer's locale.
            it.systemProperty("lical.language", (project.findProperty("lang") as String?) ?: "de")
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            it.systemProperty("lical.cases", rootProject.file("../shared/cases").absolutePath)
            // Pictures: ./gradlew testDebugUnitTest --tests '*ShotTest*' -Pshots=/folder
            (project.findProperty("shots") as String?)?.let { folder -> it.systemProperty("lical.shots", folder) }
            // A QR code drawn by Ubuntu (test_mac_gui.py --shots): -Pqrpng=/folder/qr-code.png
            (project.findProperty("qrpng") as String?)?.let { file -> it.systemProperty("lical.qrpng", file) }
            (project.findProperty("qrtitle") as String?)?.let { title -> it.systemProperty("lical.qrtitle", title) }
        }
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

// The translations: one catalogue for both apps, kept with the Ubuntu app.
val copyLocale by tasks.registering(Sync::class) {
    from(rootProject.file("../linux/lical/locale")) { include("*.json") }
    into(layout.buildDirectory.dir("generated/locale/locale"))
}
android.sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/locale"))
tasks.named("preBuild") { dependsOn(copyLocale) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    // QR codes (card b483dadf): ZXing draws and reads them, CameraX for the camera – all on the phone.
    implementation("com.google.zxing:core:3.5.3")
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
