plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.videogen.offline"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.videogen.offline"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            // Fokus ke HP modern (64-bit) agar ukuran APK lebih kecil dan performa maksimal
            abiFilters.addAll(listOf("arm64-v8a"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress += listOf("tflite", "json")
    }

    packaging {
        jniLibs {
            pickFirsts += listOf("**/libc++_shared.so")
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    
    // --- TENSORFLOW LITE CORE ---
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    
    // --- GOD MODE: GPU & NPU DELEGATES ---
    // 1. Library utama untuk GPU
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    // 2. API untuk mengecek kompatibilitas GPU HP
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")
    // 3. Mendukung operasi khusus (mencegah crash saat model dimuat)
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")
}
