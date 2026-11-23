plugins { id("com.android.application") }

android {
    namespace = "com.sigajunto.controle"
    buildFeatures { buildConfig = true }
    compileSdk = 36
    defaultConfig {
        applicationId = "com.sigajunto.controle"
        minSdk = 21
        // 29 + requestLegacyExternalStorage: no Android 11 da central ainda dá para
        // escrever em /sdcard/Fliperama e /sdcard/pegasus-frontend com a permissão comum.
        targetSdk = 29
        versionCode = 3
        versionName = "2.1"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    lint { abortOnError = false; checkReleaseBuilds = false }
}
