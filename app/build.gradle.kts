plugins {
    id("com.android.application")
}

android {
    namespace = "com.soundsplit.prototype"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.soundsplit.prototype"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "0.2-native-test"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}
