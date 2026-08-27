plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.neonvoid.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neonvoid.game"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // Supplied by CI; a local release build falls back to unsigned.
            val store = System.getenv("NEONVOID_KEYSTORE")
            if (store != null) {
                storeFile = file(store)
                storePassword = System.getenv("NEONVOID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NEONVOID_KEY_ALIAS")
                keyPassword = System.getenv("NEONVOID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (System.getenv("NEONVOID_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
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

    kotlinOptions {
        jvmTarget = "17"
    }
}
