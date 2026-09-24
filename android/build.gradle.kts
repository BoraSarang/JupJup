// 최상위 빌드 파일 — 플러그인 선언만 (AGP 9 내장 Kotlin이므로 kotlin.android 플러그인 불필요)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
}