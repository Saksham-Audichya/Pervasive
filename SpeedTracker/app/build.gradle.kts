plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 33

    defaultConfig {
        // Remove applicationId from defaultConfig section
        minSdk = 23
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    // Specify the namespace (replaces applicationId)
    namespace = "com.example.speedtracker"

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-source 1.8")
        options.compilerArgs.add("-target 1.8")
    }
}



dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.7.2")
    implementation("com.google.android.gms:play-services-location:18.0.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
}
