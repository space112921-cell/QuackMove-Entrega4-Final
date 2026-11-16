plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")

    id("com.google.firebase.crashlytics")

}

android {
    namespace = "com.quackmove.app"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.quackmove.app"

        minSdk = 26
        targetSdk = 37

        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {


    implementation("com.google.firebase:firebase-crashlytics")

    // =====================================
    // JETPACK COMPOSE
    // =====================================

    implementation(
        platform(libs.androidx.compose.bom)
    )

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(
        "androidx.compose.material:material-icons-extended"
    )

    // =====================================
    // ANDROID Y LIFECYCLE
    // =====================================

    implementation(libs.androidx.core.ktx)

    implementation(
        libs.androidx.lifecycle.runtime.ktx
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-compose:2.11.0"
    )

    implementation(
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0"
    )

    // =====================================
    // NAVEGACIÓN
    // =====================================

    implementation(
        "androidx.navigation:navigation-compose:2.9.6"
    )



    // =====================================
    // MAPLIBRE - TEMPORAL
    // =====================================

    // Se conserva durante la migración.
    // Lo eliminaremos cuando Google Maps funcione.
    implementation(
        "org.maplibre.gl:android-sdk:11.13.0"
    )

    // =====================================
    // FIREBASE
    // =====================================

    implementation(
        platform("com.google.firebase:firebase-bom:34.8.0")
    )

    implementation(
        "com.google.firebase:firebase-firestore"
    )

    implementation(
        "com.google.firebase:firebase-auth"
    )

    // =====================================
    // COROUTINES
    // =====================================

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2"
    )

    // =====================================
    // ROOM
    // =====================================

    implementation(
        "androidx.room:room-runtime:2.8.5"
    )

    ksp(
        "androidx.room:room-compiler:2.8.5"
    )

    // =====================================
    // TESTS
    // =====================================

    testImplementation(libs.junit)

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
}
