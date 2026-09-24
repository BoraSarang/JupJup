plugins {
    alias(libs.plugins.android.library)
    id("jupjup.base")
}

android {
    namespace = "com.borasarang.common"
    // R2: 공통 모듈 리소스는 jup_ 접두사 강제 (mac_/plan_ 충돌 방지)
    resourcePrefix = "jup_"
}

dependencies {
    implementation(libs.timber)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    // R2b: 서버 JSON 헬퍼용 (ApplicationCall 확장 + JsonObject 빌더)
    implementation(libs.ktor.server.core)
    implementation(libs.kotlinx.serialization.json)
    // R21: 공통 AI/검색 클라이언트 (OpenRouter · Exa)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
