plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kelaslive.admin"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kelaslive.admin"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
