plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Cambia este identificador ANTES de publicar en Google Play: no se puede modificar después.
    namespace = "app.tonalli"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tonalli"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

}

// La app web vive en ../../web y es la MISMA que se sube al hosting.
// Antes de cada compilación se copia a app/src/main/assets/ (dentro de la app, abre sin internet).
// Si la carpeta ../../web no existe (por ejemplo, abriste solo la carpeta "android"), se usa la copia
// que ya está en src/main/assets/index.html.
val syncWeb = tasks.register<Copy>("syncWeb") {
    val origen = file("../../web/index.html")
    onlyIf { origen.exists() }
    from(origen)
    into("src/main/assets")
}
tasks.named("preBuild") { dependsOn(syncWeb) }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Sirve los archivos locales con una dirección https (así localStorage funciona bien).
    implementation("androidx.webkit:webkit:1.12.1")
    // Escáner de códigos de barras de Google (no pide permiso de cámara a la app).
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
