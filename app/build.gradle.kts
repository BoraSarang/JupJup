plugins {
    alias(libs.plugins.android.application)
    id("jupjup.base")
}

android {
    namespace = "com.borasarang.jupjup"

    defaultConfig {
        applicationId = "com.borasarang.jupjup"
        targetSdk = 36
        versionCode = 11
        versionName = "1.9.0"
    }

    signingConfigs {
        create("release") {
            val store = System.getenv("KEYSTORE_PATH")
            if (!store.isNullOrBlank()) {
                storeFile = file(store)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("KEYSTORE_PATH").orEmpty().isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":services:common"))
    implementation(project(":services:mac"))
    implementation(project(":services:plan"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}