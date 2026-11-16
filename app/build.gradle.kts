plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "tn.alwaraqa.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "tn.alwaraqa.app"
        minSdk = 26
        targetSdk = 35
        // Mise à jour : augmenter versionCode à chaque version, ne jamais changer applicationId.
        versionCode = 5
        versionName = "0.5.0"
    }

    // Clé de test fixe, gardée dans le dépôt : chaque APK porte la même signature,
    // donc une nouvelle version s'installe par-dessus l'ancienne sans perdre les données.
    signingConfigs {
        getByName("debug") {
            storeFile = file("alwaraqa-test.keystore")
            storePassword = "alwaraqa"
            keyAlias = "alwaraqa"
            keyPassword = "alwaraqa"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Moteur PDF embarqué : sommaire, texte et mots de passe sur toutes les versions d'Android
    implementation("io.legere:pdfiumandroid:1.0.30")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
