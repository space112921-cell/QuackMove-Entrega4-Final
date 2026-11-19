// Configuración general de Gradle.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.google.gms.google-services") version "4.5.0" apply false

    id("com.google.devtools.ksp") version "2.3.10" apply false


    id("com.google.firebase.crashlytics") version "3.0.8" apply false

}
