plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cellier.manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cellier.manager"
        minSdk = 26
        targetSdk = 34
        versionCode = 224
        versionName = "2.1.14-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    // Schemas de Room (utile pour les migrations futures)
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

dependencies {
    // --- Core Android ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // --- Compose ---
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.ui.tooling)

    // --- Room ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- WorkManager ---
    implementation(libs.androidx.work.runtime.ktx)

    // --- Réseau local compagnon et API Bromelier ---
    implementation(libs.okhttp)

    // --- Coroutines ---
    implementation(libs.kotlinx.coroutines.android)

    // --- Image loading (photos des bouteilles) ---
    implementation(libs.coil.compose)

    // --- OCR local (ML Kit) ---
    implementation(libs.mlkit.text.recognition)

    // --- Tests ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
