plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.heartratepredictor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.heartratepredictor"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.wear)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.gms.play.services.location)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material)

    implementation(libs.gms.play.services.wearable)

    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    implementation("org.tensorflow:tensorflow-lite:2.13.0") // Core TensorFlow Lite library
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.13.0")
    implementation(libs.androidx.material3.android) // Select TensorFlow ops
    //implementation("org.tensorflow:tensorflow-lite-flex:2.13.0") // Flex Delegate


}