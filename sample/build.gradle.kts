plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.henrydavl.apilogkit.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.henrydavl.apilogkit.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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

    // Intentionally NO Compose here — this sample is a plain XML/View app, to
    // prove a non-Compose host can fully consume the ApiLogKit library.
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    sourceSets["main"].java.srcDir("src/main/kotlin")
}

dependencies {
    implementation(project(":apilogkit"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.android.material)

    // The host owns OkHttp; ApiLogInterceptor plugs into it.
    implementation(libs.okhttp)
}
