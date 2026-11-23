import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(file.inputStream())
    }
}
val geminiApiKey: String = localProperties.getProperty("GEMINI_API_KEY") ?: ""
val firmaSha256: String = localProperties.getProperty("FIRMA_OFICIAL_SHA256") ?: ""
val firmaSha1: String = localProperties.getProperty("FIRMA_OFICIAL_SHA1") ?: ""


plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "es.jlrn.escudoantifraude"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "es.jlrn.escudoantifraude"
        minSdk = 24
        targetSdk = 35
        versionCode = 11
        versionName = "2.4"

        resValue("string", "gemini_api_key", geminiApiKey)
        resValue("string", "firma_oficial_sha256", firmaSha256)
        resValue("string", "firma_oficial_sha1", firmaSha1)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        resValues = true
    }

//    buildTypes {
//        release {
//            optimization {
//                enable = false
//                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
//            }
//        }
//    }

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":scanner"))
    implementation(project(":analyzer"))
    implementation(project(":alert"))
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}