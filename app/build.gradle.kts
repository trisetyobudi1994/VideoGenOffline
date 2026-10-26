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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // DJL Tokenizer (Versi Sinkron)
    implementation("ai.djl.huggingface:tokenizers:0.26.0")
    implementation("ai.djl.android:tokenizer-native:0.26.0")
}
