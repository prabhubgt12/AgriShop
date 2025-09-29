import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fertipos.agroshop"
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.fertipos.agroshop"
        minSdk = 24
        targetSdk = 35
        versionCode = 18
        versionName = "1.0.18"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    // Load signing properties
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { this.load(it) }
    }

    signingConfigs {
        create("release") {
            val path = keystoreProps.getProperty("keystoreFile")
            if (path != null) {
                storeFile = file(path)
                storePassword = keystoreProps.getProperty("keystorePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Enable test ads in debug builds
            buildConfigField("boolean", "USE_TEST_ADS", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            // Closed testing: keep test ads enabled in release build
            buildConfigField("boolean", "USE_TEST_ADS", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xjvm-default=all",
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/INDEX.LIST"
        }
    }

    // Keep all locales in base to support in-app language change after Play delivery
    bundle {
        language {
            enableSplit = false
        }
    }
}

// Post-build rename tasks for APK outputs (compatible with AGP 8.2)
tasks.register<Copy>("renameDebugApk") {
    val verName = android.defaultConfig.versionName ?: "1.0"
    from("build/outputs/apk/debug/app-debug.apk")
    into("build/outputs/apk/debug")
    rename("app-debug.apk", "SimpleShop-debug-${verName}.apk")
    dependsOn("assembleDebug")
}

tasks.register<Copy>("renameReleaseApk") {
    val verName = android.defaultConfig.versionName ?: "1.0"
    from("build/outputs/apk/release/app-release.apk")
    into("build/outputs/apk/final")
    rename("app-release.apk", "SimpleShop-release-${verName}.apk")
    dependsOn("assembleRelease")
}

// Some AGP versions output app-release-unsigned.apk by default. Provide a rename for that too.
tasks.register<Copy>("renameReleaseUnsignedApk") {
    val verName = android.defaultConfig.versionName ?: "1.0"
    from("build/outputs/apk/release/app-release-unsigned.apk")
    into("build/outputs/apk/final")
    rename("app-release-unsigned.apk", "SimpleShop-release-${verName}-unsigned.apk")
    dependsOn("assembleRelease")
}

kapt {
    correctErrorTypes = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Room (KSP)
    val roomVersion = "2.5.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Security (AES utils will use javax/crypto which is JDK)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Coil for image loading in Compose (logo preview in Settings)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // DataStore for user preferences (theme, etc.)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Google Sign-In (for Drive App Folder access)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Google Drive REST API (Android client)
    implementation("com.google.api-client:google-api-client-android:2.6.0")
    implementation("com.google.http-client:google-http-client-android:1.43.3")
    implementation("com.google.http-client:google-http-client-gson:1.43.3")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230815-2.0.0")

    // Google Mobile Ads SDK (AdMob) - test ads
    implementation("com.google.android.gms:play-services-ads:22.6.0")

    // Play Billing for in-app purchases
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
