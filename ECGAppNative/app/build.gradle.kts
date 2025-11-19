plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tugasakhir.ecgappnative"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.tugasakhir.ecgappnative"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        @Suppress("DEPRECATION")
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true // Kita pake ViewBinding
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    // Tambahkan ini untuk 'by viewModels()'
    implementation(libs.androidx.activity.ktx)
    // 1. Networking (Retrofit + OkHttp + GSON)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    // 2. Storage (DataStore Preferences)
    implementation(libs.androidx.datastore.preferences)
    // 3. Lifecycle (ViewModel, LiveData, Coroutines)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation (libs.androidx.room.runtime)
    implementation (libs.androidx.room.ktx)
    ksp (libs.androidx.room.compiler)
    // 5. QR Code (Scanner & Generator)
    implementation(libs.journeyapps.zxing.embedded)
    implementation(libs.google.zxing.core)
    // 6. GRAFIK ECG
    implementation(libs.mpandroidchart)
    // 7. BLE PROVISIONING
    implementation(libs.esp.idf.provisioning.android)

}