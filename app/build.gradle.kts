import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Dirección del servidor (backend en Render, con PostgreSQL + MongoDB).
// Por defecto usa el servidor del proyecto. Para cambiarlo, en local.properties:
//   API_URL=http://10.0.2.2:3000      (servidor en tu PC, desde el emulador)
//   API_URL=                          (vacío: datos de ejemplo en memoria, sin internet)
val localProperties = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}
val apiUrl: String = localProperties.getProperty("API_URL", "https://conatacoch-api.onrender.com").trim()

android {
    namespace = "cl.conatacoch.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "cl.conatacoch.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_URL", "\"$apiUrl\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Compila para Java 17 con el JDK que use Gradle (por ejemplo, el 21 de Android Studio).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    // biometric 1.1.0 trae fragment 1.2.5, que no entrega bien los resultados de ActivityResult.
    implementation(libs.androidx.fragment.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    // Mapa de OpenStreetMap (gratis, sin clave).
    implementation(libs.osmdroid)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
