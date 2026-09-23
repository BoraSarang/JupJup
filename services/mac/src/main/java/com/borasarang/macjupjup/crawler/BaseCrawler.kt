package com.borasarang.macjupjup.crawler

import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.db.entity.AppSourceMapping
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.AppLicense
import com.borasarang.macjupjup.util.CategoryInfer
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.MergeUtils
import com.borasarang.macjupjup.util.classifyLicense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 크롤러 공통 베이스. 네트워크 I/O는 IO 디스패처, 요청 간 예의 대기.
 * 한 크롤러의 실패는 예외로 상위에 전달 → Worker가 격리 처리.
 */
abstract class BaseCrawler(
    protected val source: CrawlSource,
) : AppCrawler {

    override val sourceName: String get() = source.name

    protected suspend fun fetchGet(url: String): String = withContext(Dispatchers.IO) {
        val result = CrawlHttp.get(url)
        if (!result.isOk) {
            throw IllegalStateException("GET 실패 url=$url code=${result.code} err=${result.error}")
        }
        result.body
    }

    protected suspend fun fetchGetHeaders(url: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val result = CrawlHttp.getWithHeaders(url, headers)
            if (!result.isOk) {
                throw IllegalStateException("GET 실패 url=$url code=${result.code} err=${result.error}")
            }
            result.body
        }

    protected suspend fun politenessDelay() {
        delay(Constants.CRAWL_REQUEST_DELAY_MS)
    }

    /** 파싱 실패 (E-AND-CRAWL-0201). 크롤러별 throw 하드코딩 통합 */
    protected fun parseFail(what: String): Nothing =
        throw IllegalStateException("$what 파싱 실패 (E-AND-CRAWL-0201)")

    /**
     * R1-3: 피드/쿼리/키워드 순회 템플릿.
     * 입력당 fetch+parse → 실패는 해당 입력만 스킵 → 예의 대기 → 중복 제거 → 완료 로그.
     */
    protected suspend fun <T> crawlEach(
        inputs: List<T>,
        label: String,
        dedupKey: (AppDraft) -> Any? = { it.app.id },
        fetchOne: suspend (T) -> List<AppDraft>,
    ): List<AppDraft> {
        val drafts = mutableListOf<AppDraft>()
        for (input in inputs) {
            try {
                drafts += fetchOne(input)
            } catch (e: Exception) {
                DebugLogger.w("수집", "$label 스킵 $input: ${e.message}")
            }
            politenessDelay()
        }
        val seen = mutableSetOf<Any?>()
        return drafts.filter { seen.add(dedupKey(it)) }.also {
            DebugLogger.i("수집", "$label 완료 inputs=${inputs.size} found=${drafts.size} unique=${it.size}")
        }
    }

    /** id 기준 중복 제거 (R1-9) */
    protected fun dedupById(drafts: List<AppDraft>): List<AppDraft> {
        val seen = mutableSetOf<String>()
        return drafts.filter { seen.add(it.app.id) }
    }

    /**
     * 크롤러 공통 App 초안 빌더. 라이선스 자동분류 + 카테고리/태그 추론 적용.
     * 수동 오버라이드(licenseOverride)가 있으면 M5 설정에서 별도 처리.
     */
    protected fun buildDraft(
        name: String,
        developer: String,
        price: Double = 0.0,
        repoFullName: String? = null,
        homepageUrl: String? = null,
        version: String? = null,
        releaseNotesSummary: String? = null,
        releaseNotes: String? = null,
        releaseDate: Long? = null,
        descriptionSnippet: String? = null,
        longDescription: String? = null,
        trackId: Long? = null,
        averageRating: Double? = null,
        ratingCount: Int? = null,
        stars: Int? = null,
        primaryLanguage: String? = null,
        topics: List<String> = emptyList(),
        iconUrl: String? = null,
        forks: Int? = null,
        issues: Int? = null,
        licenseName: String? = null,
        detailUrl: String,
    ): AppDraft? {
        if (name.isBlank() || developer.isBlank()) return null
        val id = MergeUtils.generateId(name, developer)
        val now = System.currentTimeMillis()
        val inferred = CategoryInfer.infer("$name $developer ${descriptionSnippet ?: ""}", topics)
        val license = when (classifyLicense(repoFullName, price)) {
            AppLicense.OSS -> Constants.LICENSE_OSS
            AppLicense.PAID -> Constants.LICENSE_PAID
            AppLicense.FREE -> Constants.LICENSE_FREE
        }
        val app = App(
            id = id,
            platform = Constants.PLATFORM_MACOS,
            name = name.trim(),
            developer = developer.trim(),
            license = license,
            price = price,
            currency = "USD",
            category = inferred.category,
            tags = inferred.tags.joinToString(",").ifBlank { null },
            trackId = trackId,
            repoFullName = repoFullName,
            homepageUrl = homepageUrl,
            version = version,
            prevVersion = null,
            releaseNotesSummary = releaseNotesSummary?.take(Constants.MAX_SUMMARY_LEN),
            releaseNotes = releaseNotes?.take(Constants.RELEASE_NOTES_MAX),
            releaseDate = releaseDate,
            descriptionSnippet = descriptionSnippet?.take(Constants.APP_SUMMARY_LEN),
            longDescription = longDescription?.take(Constants.APP_BODY_MAX),
            screenshotUrls = null,
            averageRating = averageRating,
            ratingCount = ratingCount,
            stars = stars,
            primaryLanguage = primaryLanguage,
            supportedLanguages = null,
            topics = topics.joinToString(",").ifBlank { null },
            iconUrl = iconUrl,
            descriptionKo = null,
            longDescriptionKo = null,
            releaseNotesKo = null,
            sellerName = null,
            fileSize = null,
            minOs = null,
            contentRating = null,
            forks = forks,
            issues = issues,
            licenseName = licenseName,
            firstSeenAt = now,
            lastUpdatedAt = now,
            isNew = true,
            sourceId = source.id,
            licenseOverride = null,
        )
        val mapping = AppSourceMapping(
            appId = id,
            sourceName = source.name,
            sourceUrl = detailUrl,
            fetchedAt = now,
        )
        return AppDraft(app, listOf(mapping))
    }
}
