plugins {
    id("com.android.application")
}

val keystorePath = System.getenv("SOUNDSPLIT_KEYSTORE_PATH")
val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val keyAliasValue = System.getenv("ANDROID_KEY_ALIAS")
val keyPasswordValue = System.getenv("ANDROID_KEY_PASSWORD")
val hasPrototypeSigning = !keystorePath.isNullOrBlank() &&
        !keystorePassword.isNullOrBlank() &&
        !keyAliasValue.isNullOrBlank() &&
        !keyPasswordValue.isNullOrBlank()

android {
    namespace = "com.soundsplit.prototype"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.soundsplit.prototype"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "0.3-spotify-control"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (hasPrototypeSigning) {
            create("prototype") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasPrototypeSigning) {
                signingConfig = signingConfigs.getByName("prototype")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (hasPrototypeSigning) {
                signingConfig = signingConfigs.getByName("prototype")
            }
        }
    }
}

dependencies {
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation("com.google.code.gson:gson:2.10.1")
}
