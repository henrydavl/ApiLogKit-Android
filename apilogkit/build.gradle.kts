plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "com.henrydavl.apilogkit"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
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

    buildFeatures {
        compose = true
    }

    // Required for JitPack / Maven publishing of an Android library: expose the
    // `release` variant as a publishable component (with a sources jar).
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    sourceSets["main"].java.srcDir("src/main/kotlin")
}

// JitPack injects the real groupId (com.github.<user>.<repo>) and version (the
// git tag); we just register the publication from the `release` component.
publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
            artifactId = "apilogkit"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // OkHttp is optional: only needed by hosts that use ApiLogInterceptor.
    // compileOnly keeps it off the transitive classpath for manual-API-only hosts.
    compileOnly(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    // Real org.json on the unit-test classpath (the android.jar stub is non-functional).
    testImplementation(libs.json)
}
