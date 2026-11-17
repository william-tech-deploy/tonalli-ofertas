plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.tonalli.nativo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.tonalli.nativo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "2.0-nativo"
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
}
