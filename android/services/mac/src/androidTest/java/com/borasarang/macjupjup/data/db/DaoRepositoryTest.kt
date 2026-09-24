package com.borasarang.macjupjup.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.borasarang.macjupjup.crawler.AppSourceMappingHelper
import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.db.entity.CrawlLog
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.data.repository.AppFilter
import com.borasarang.macjupjup.data.repository.AppRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room 실DB 테스트 (S22 connected). 저장·병합·집계 회귀 방지 */
@RunWith(AndroidJUnit4::class)
class DaoRepositoryTest {

    private lateinit var db: MacDatabase
    private lateinit var repo: AppRepository

    private fun app(
        id: String,
        name: String = "N",
        developer: String = "D",
        version: String? = null,
        isNew: Boolean = true,
        sourceId: String? = "t",
        homepage: String? = null,
    ) = App(
        id = id, platform = "macOS", name = name, developer = developer,
        license = "FREE", price = 0.0, currency = "USD", category = "유틸리티",
        tags = null, trackId = null, repoFullName = null, homepageUrl = homepage,
        version = version, prevVersion = null, releaseNotesSummary = null,
        releaseNotes = null, releaseDate = null, descriptionSnippet = null,
        screenshotUrls = null, averageRating = null, ratingCount = null,
        stars = null, primaryLanguage = null, topics = null, iconUrl = null,
        descriptionKo = null, releaseNotesKo = null, sellerName = null,
        fileSize = null, minOs = null, contentRating = null, forks = null,
        issues = null, licenseName = null, firstSeenAt = 1, lastUpdatedAt = 2,
        isNew = isNew, sourceId = sourceId, licenseOverride = null,
        supportedLanguages = null,
    )

    private fun source(id: String = "t", name: String = "T") = CrawlSource(
        id = id, name = name, type = "T", baseUrl = "https://example.com",
        enabled = true, intervalHours = 6, intervalMinutes = 360,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null,
        selectorConfigJson = null,
    )

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, MacDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = AppRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun saveApps_생성_버전bump_이력() = runBlocking {
        val r1 = repo.saveApps(listOf(app("a", version = "1.0")), emptyList())
        assertEquals(1, r1.created)
        val r2 = repo.saveApps(listOf(app("a", version = "2.0")), emptyList())
        assertEquals(0, r2.created)
        assertEquals(1, r2.updated)
        val hist = db.versionHistoryDao().getByApp("a")
        assertEquals(2, hist.size)
        assertEquals("2.0", hist.first().version)
    }

    @Test
    fun saveApps_형제흡수_홈페이지보존() = runBlocking {
        val mmb = app("macmenubar-lyrimuse", "Lyrimuse", "MacMenuBar", homepage = "https://h.example/")
        repo.saveApps(
            listOf(mmb),
            listOf(AppSourceMappingHelper.mapping(mmb.id, "MacMenuBar 신규", "https://m.example/", 1)),
        )
        val ph = app("khalil-lyrimuse", "Lyrimuse", "Khalil")
        val r = repo.saveApps(
            listOf(ph),
            listOf(AppSourceMappingHelper.mapping(ph.id, "Product Hunt", "https://p.example/", 2)),
        )
        assertEquals(0, r.created)
        assertEquals(1, db.appDao().count())
        val kept = db.appDao().getById("macmenubar-lyrimuse")!!
        assertEquals("https://h.example/", kept.homepageUrl)
        // 양쪽 출처 매핑이 형제 id로 흡수
        assertEquals(2, db.appSourceMappingDao().getByApp("macmenubar-lyrimuse").size)
        assertTrue(db.appSourceMappingDao().getByApp("khalil-lyrimuse").isEmpty())
    }

    @Test
    fun list_bumped필터_페이징() = runBlocking {
        repo.saveApps(
            listOf(
                app("n1", name = "NewApp", version = "1.0", isNew = true),
                app("u1", name = "UpdApp", version = "2.0", isNew = false),
                app("u2", name = "NoversionApp", version = null, isNew = false),
            ),
            emptyList(),
        )
        val bumped = repo.list(AppFilter(bumped = true, pageSize = 50))
        assertEquals(1, bumped.total)
        assertEquals("u1", bumped.apps.first().app.id)
        val all = repo.list(AppFilter(pageSize = 50))
        assertEquals(3, all.total)
    }

    @Test
    fun collectByDay_일별집계() = runBlocking {
        val now = System.currentTimeMillis()
        db.crawlLogDao().insert(
            CrawlLog(
                sourceId = "s", sourceName = "S", startedAt = now, finishedAt = now,
                status = "SUCCESS", plansFound = 10, plansNew = 5, plansUpdated = 3,
                errorMessage = null,
            ),
        )
        val rows = db.crawlLogDao().collectByDay(now - 86400000)
        assertEquals(1, rows.size)
        assertEquals(10L, rows.first().found)
        assertEquals(5L, rows.first().newCount)
        val collect = repo.collect(7)
        assertTrue(collect.isNotEmpty())
        assertEquals(10L, collect.first().found)
    }

    @Test
    fun isNew_7일경과_해제() = runBlocking {
        val old = System.currentTimeMillis() - 8 * 86400000L
        val fresh = app("old-new", name = "OldNewApp").copy(firstSeenAt = old, isNew = true)
        val recent = app("recent-new", name = "RecentNewApp").copy(
            firstSeenAt = System.currentTimeMillis(), isNew = true,
        )
        db.appDao().upsertAll(listOf(fresh, recent))
        val cleared = repo.clearStaleNewFlags()
        assertEquals(1, cleared)
        assertEquals(false, db.appDao().getById("old-new")?.isNew)
        assertEquals(true, db.appDao().getById("recent-new")?.isNew)
    }

    @Test
    fun purgeSource_고아만삭제() = runBlocking {        db.crawlSourceDao().upsertAll(listOf(source("gone", "Gone")))
        val orphan = app("orphan", name = "OrphanApp", sourceId = "gone")
        val shared = app("shared", name = "SharedApp", sourceId = "gone")
        repo.saveApps(
            listOf(orphan, shared),
            listOf(
                AppSourceMappingHelper.mapping("orphan", "Gone", "u1", 1),
                AppSourceMappingHelper.mapping("shared", "Gone", "u2", 1),
                AppSourceMappingHelper.mapping("shared", "Other", "u3", 2),
            ),
        )
        val deleted = repo.purgeSource("gone", "Gone")
        assertEquals(1, deleted)
        assertNull(db.appDao().getById("orphan"))
        assertEquals("shared", db.appDao().getById("shared")?.id)
        assertTrue(db.versionHistoryDao().getByApp("orphan").isEmpty())
    }
}
