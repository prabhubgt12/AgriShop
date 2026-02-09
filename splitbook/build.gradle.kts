import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.ledge.splitbook"
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.ledge.splitbook"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "0.1.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    // Load signing properties from root keystore.properties (shared with other apps)
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
    }

    signingConfigs {
        create("release") {
            val ksFile = keystoreProps.getProperty("keystoreFile")
            if (!ksFile.isNullOrBlank()) {
                storeFile = rootProject.file(ksFile)
                storePassword = keystoreProps.getProperty("keystorePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_TEST_ADS", "true")
            buildConfigField("boolean", "DISABLE_LOCK", "false")
            buildConfigField("boolean", "SHOW_MINIMAL_UI", "false")
            buildConfigField("boolean", "SHOW_COMPOSE_MINIMAL", "false")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("boolean", "USE_TEST_ADS", "false")
            buildConfigField("boolean", "DISABLE_LOCK", "false")
            buildConfigField("boolean", "SHOW_MINIMAL_UI", "false")
            buildConfigField("boolean", "SHOW_COMPOSE_MINIMAL", "false")
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
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/INDEX.LIST"
        }
        jniLibs { useLegacyPackaging = false }
    }

    bundle {
        language { enableSplit = false }
    }
}

kapt { correctErrorTypes = true }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // Coil for image loading in Compose (future use)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // DataStore for preferences (theme, lock settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Google Sign-In and Drive for backup/restore
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.6.0")
    implementation("com.google.http-client:google-http-client-android:1.43.3")
    implementation("com.google.http-client:google-http-client-gson:1.43.3")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230815-2.0.0")

    // Google Mobile Ads SDK (AdMob)
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    // Google Play Billing for IAP (remove ads)
    implementation("com.android.billingclient:billing-ktx:6.2.1")

    // Hilt (align with other apps)
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room (DB)
    val roomVersion = "2.5.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Note: Ads not used in Simple Split; exclude GMA to avoid MobileAdsInitProvider crash without app ID

    // WorkManager (backups/exports scheduling later)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // FastExcel for Excel export
    implementation("org.dhatim:fastexcel:0.17.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
