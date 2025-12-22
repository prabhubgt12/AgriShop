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
    namespace = "com.ledge.cashbook"
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.ledge.cashbook"
        minSdk = 24
        targetSdk = 35
        versionCode = 17
        versionName = "1.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    // Load signing properties from root keystore.properties (shared with ledgerbook)
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
            // Enable test ads in debug builds
            buildConfigField("boolean", "USE_TEST_ADS", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            // Closed testing: keep test ads enabled in release build (change to false for production)
            buildConfigField("boolean", "USE_TEST_ADS", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
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
        // Ensure native libraries are not extracted (required for 16KB page size compliance on Android 15+)
        jniLibs { useLegacyPackaging = false }
    }

    // Keep all locales in base to support in-app language change after Play delivery
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
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Coil for image loading in Compose
    implementation("io.coil-kt:coil-compose:2.6.0")

    // DataStore for preferences (theme, language)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Google Sign-In and Drive for backup/restore
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.6.0")
    implementation("com.google.http-client:google-http-client-android:1.43.3")
    implementation("com.google.http-client:google-http-client-gson:1.43.3")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230815-2.0.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    val roomVersion = "2.5.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Google Mobile Ads SDK (AdMob)
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    // InMobi Kotlin SDK and AdMob–InMobi mediation adapter (matches GMA 23.3.0)
    implementation("com.inmobi.monetization:inmobi-ads-kotlin:10.7.8")
    implementation("com.google.ads.mediation:inmobi:10.7.8.0")

    // Play Billing for in-app purchases
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    // FastExcel for Excel (.xlsx) export (Android-friendly)
    implementation("org.dhatim:fastexcel:0.17.0")
    // WorkManager for background auto backups
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
