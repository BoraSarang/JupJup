import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * R7: 4 모듈(:app·:services:common·mac·plan) 공통 Android 설정 단일 진실.
 * SDK·컴파일 옵션·패키징 제외·단위테스트 기본값을 묶는다.
 * 버전은 루트 카탈로그(gradle/libs.versions.toml)가 진실 — 하드코딩 금지.
 * com.android.application/library 뒤에 적용 (android 확장을 찾아 설정).
 * 제외: namespace·applicationId·버전·서명·buildFeatures·Room/ksp (모듈별 상이).
 *
 * AGP 9의 CommonExtension은 블록 함수가 아닌 프로퍼티 기반이라 대입식으로 기술한다.
 */
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val compileSdk = catalog.findVersion("compileSdk").get().requiredVersion.toInt()
val minSdk = catalog.findVersion("minSdk").get().requiredVersion.toInt()

val android = extensions.findByType(CommonExtension::class.java)
    ?: error("jupjup.base는 com.android.application 또는 com.android.library와 함께 적용하세요")

android.compileSdk = compileSdk
android.defaultConfig.minSdk = minSdk
android.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

android.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
android.compileOptions.targetCompatibility = JavaVersion.VERSION_17
android.compileOptions.isCoreLibraryDesugaringEnabled = true

android.packaging.resources.excludes.addAll(
    setOf(
        "META-INF/INDEX.LIST",
        "META-INF/native-image/**",
        "META-INF/versions/9/**",
    ),
)

android.testOptions.unitTests.isReturnDefaultValues = true

dependencies {
    add("coreLibraryDesugaring", catalog.findLibrary("desugar-jdk-libs").get().get())
}
