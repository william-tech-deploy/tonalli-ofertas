import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// La dirección y la clave pública de Supabase se leen de local.properties,
// que NO se sube a GitHub. Ver supabase/LEEME.md.
val local = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun texto(clave: String) = "\"" + local.getProperty(clave, "").trim().replace("\"", "") + "\""

android {
    namespace = "app.tonalli.nativo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.tonalli.nativo"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "2.1-nativo"
        buildConfigField("String", "SUPABASE_URL", texto("supabase.url"))
        buildConfigField("String", "SUPABASE_KEY", texto("supabase.key"))
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    // Escáner de códigos de barras de Google: no pide permiso de cámara.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
