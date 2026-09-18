plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("jupjup.base")
}

android {
    namespace = "com.borasarang.promptfactoryjupjup"

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":services:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.timber)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    // OkHttp for AI API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
