package com.borasarang.macjupjup.data.repository

import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.db.entity.AppSourceMapping
import com.borasarang.macjupjup.data.db.entity.VersionHistory
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.TimeUtils
import androidx.room.withTransaction

/** 앱 저장·조회·병합·정리 */
class AppRepository(private val db: MacDatabase) {

    suspend fun saveApps(
        apps: List<App>,
        mappings: List<AppSourceMapping>,
    ): SaveResult {
        if (apps.isEmpty()) return SaveResult(0, 0, 0)
        try {
            return saveAppsInternal(apps, mappings)
        } catch (e: Exception) {
            DebugLogger.e("저장", "E-AND-DB-0402", "앱 저장 실패 ${apps.size}건: ${e.message}", e)
            throw e
        }
    }

    private suspend fun saveAppsInternal(
        apps: List<App>,
        mappings: List<AppSourceMapping>,
    ): SaveResult {
        // P0-2: 저장 전체를 단일 트랜잭션으로 원자화 (동시 워커의 read-merge-write 경쟁 제거)
        return db.withTransaction {
            val dao = db.appDao()
            var created = 0
            var updated = 0
            val createdIds = mutableListOf<String>()
            // P1-1: 배치 일괄 조회 (드래프트당 getById N+1 제거)
            val existingById = dao.getByIds(apps.map { it.id }).associateBy { it.id }
            val missingNames = apps.filter { it.id !in existingById }
                .map { it.name.lowercase() }.distinct()
            val siblingRows = if (missingNames.isEmpty()) {
                emptyList()
            } else {
                dao.findByNamesLower(missingNames)
            }
            val merged = apps.map { draft ->
                val existing = existingById[draft.id] ?: findSiblingIn(siblingRows, draft)
                if (existing == null) {
                    created++
                    createdIds += draft.id
                    draft
                } else {
                    updated++
                    // T-071: 이름 중복 병합 시 형제 id로 흡수 (매핑도 형제 id 기준이어야 함)
                    val aligned = if (existing.id != draft.id) draft.copy(id = existing.id) else draft
                    mergeApps(existing, aligned)
                }
            }
            dao.upsertAll(merged)
            // T-071: 이름 병합으로 id가 바뀐 draft의 매핑도 형제 id로 재정렬
            val idMap = apps.mapIndexedNotNull { i, d ->
                val finalId = merged[i].id
                if (finalId != d.id) d.id to finalId else null
            }.toMap()
            val alignedMappings = if (idMap.isEmpty()) mappings else mappings.map { m ->
                val target = idMap[m.appId] ?: m.appId
                if (target != m.appId) m.copy(appId = target) else m
            }
            if (alignedMappings.isNotEmpty()) db.appSourceMappingDao().upsertAll(alignedMappings)
            // 버전 히스토리 기록 (신규 버전만, 병합 후 기준)
            // T-080: 해당 버전 링크를 함께 기록 (GitHub 릴리즈 페이지 / MAS 스토어 페이지)
            val now = System.currentTimeMillis()
            val urlByApp = alignedMappings.groupBy { it.appId }
                .mapValues { (_, ms) -> ms.firstOrNull { !it.sourceUrl.isNullOrBlank() }?.sourceUrl }
            // P1-1: 버전 최신 이력 일괄 조회
            val withVersion = merged.filter { it.version != null }
            val latestByApp = if (withVersion.isEmpty()) {
                emptyMap()
            } else {
                db.versionHistoryDao().getByApps(withVersion.map { it.id })
                    .groupBy { it.appId }
                    .mapValues { (_, vs) -> vs.maxByOrNull { it.detectedAt } }
            }
            val newHistories = mutableListOf<VersionHistory>()
            for (app in withVersion) {
                val v = app.version?.trim() ?: continue
                val latest = latestByApp[app.id]
                if (latest == null || !com.borasarang.macjupjup.util.MergeUtils.sameVersion(latest.version, v)) {
                    if (latest != null) {
                        DebugLogger.i("버전추적", "버전 bump 감지: ${app.name} ${latest.version} → $v")
                    }
                    newHistories += VersionHistory(
                        appId = app.id,
                        version = v,
                        detectedAt = now,
                        notesSummary = app.releaseNotesSummary,
                        source = app.sourceId,
                        sourceUrl = urlByApp[app.id],
                    )
                }
            }
            if (newHistories.isNotEmpty()) db.versionHistoryDao().insertAll(newHistories)
            SaveResult(apps.size, created, updated, createdIds)
        }
    }

    suspend fun list(filter: AppFilter): PagedApps {
        val pageSize = filter.pageSize.coerceIn(1, Constants.API_MAX_PAGE_SIZE)
        val page = filter.page.coerceAtLeast(1)
        val offset = (page - 1) * pageSize
        val q = filter.q?.ifBlank { null }
        val apps = db.appDao().listFiltered(
            license = filter.license,
            category = filter.category,
            tag = filter.tag,
            q = q,
            sort = filter.sort,
            limit = pageSize,
            offset = offset,
            bumped = filter.bumped,
            updatedOnly = filter.updatedOnly,
        )
        val total = db.appDao().countFiltered(filter.license, filter.category, filter.tag, q, filter.bumped, filter.updatedOnly)
        // P1-1: 대표 매핑 일괄 조회 (행당 getByApp N+1 제거)
        val mapsByApp = if (apps.isEmpty()) {
            emptyMap()
        } else {
            db.appSourceMappingDao().getByApps(apps.map { it.id }).groupBy { it.appId }
        }
        val items = apps.map { a ->
            val mapping = mapsByApp[a.id]?.firstOrNull()
            AppListItem(
                app = a,
                sourceName = mapping?.sourceName,
                sourceUrl = mapping?.sourceUrl,
            )
        }
        return PagedApps(items, total, page, pageSize)
    }

    suspend fun detail(id: String): AppWithSourceList? {
        val withSources = db.appDao().getWithSources(id) ?: return null
        val versions = db.versionHistoryDao().getByApp(id)
        return withSources.toModel(versions)
    }

    suspend fun overview(): AppStats {
        val total = db.appDao().count()
        val active = db.crawlSourceDao().getEnabled().size
        val lastRun = db.crawlSourceDao().getAll().mapNotNull { it.lastRunAt }.maxOrNull()
        return AppStats(total, active, lastRun)
    }

    /** 일별 수집량 집계 (그래프용, days 1~30). 반환 = 오래된 순 */
    suspend fun collect(days: Int): List<DayCollect> {
        val d = days.coerceIn(1, 30)
        val since = System.currentTimeMillis() - d * TimeUtils.MILLIS_PER_DAY
        val rows = db.crawlLogDao().collectByDay(since)
        return rows.groupBy { it.day }.toSortedMap().map { (day, rs) ->
            DayCollect(
                day = day,
                found = rs.sumOf { it.found },
                newCount = rs.sumOf { it.newCount },
                updated = rs.sumOf { it.updated },
                runs = rs.sumOf { it.runs },
                bySource = rs.map {
                    SourceCollect(it.sourceId, it.sourceName, it.found, it.newCount, it.updated)
                }.sortedByDescending { it.found },
            )
        }
    }

    /** 인사이트 조립 입력 (최근 7일 수집 + 24h 실패 + 번역 잔량) */
    suspend fun insightsInput(): InsightsInput {
        val total = db.appDao().count()
        val trends = trends()
        val week = collect(7)
        val bestSource = week.flatMap { it.bySource }
            .groupBy { it.sourceId }
            .map { (_, rs) ->
                Triple(rs.first().sourceName, rs.sumOf { it.found }, rs.sumOf { it.newCount })
            }.maxByOrNull { it.second }
        val bestDay = week.maxByOrNull { it.newCount }
            ?.takeIf { it.newCount > 0 }?.let { it.day to it.newCount }
        val dayAgo = System.currentTimeMillis() - TimeUtils.MILLIS_PER_DAY
        val failed24h = db.crawlLogDao().recent(200)
            .filter { it.status == "FAILED" && it.startedAt >= dayAgo }
            .map { it.sourceName }
        val topCat = trends.byCategory.maxByOrNull { it.value }
        val share = if (total > 0 && topCat != null) (topCat.value * 100 / total) else 0
        return InsightsInput(
            totalApps = total,
            newLast7d = trends.newLast7d,
            bumpsLast7d = trends.versionBumpsLast7d,
            topCategory = topCat?.let { it.key to it.value },
            topCategorySharePct = share,
            bestSource = bestSource,
            bestDay = bestDay,
            failedSources24h = failed24h,
            untranslated = try {
                db.appDao().countUntranslated()
            } catch (_: Exception) {
                0
            },
        )
    }

    /** 트렌드 대시보드 집계 (T-041) */
    suspend fun trends(): TrendStats {
        val dao = db.appDao()
        val weekAgo = System.currentTimeMillis() - 7 * TimeUtils.MILLIS_PER_DAY
        return TrendStats(
            byCategory = dao.countByCategory().associate { it.name to it.cnt },
            byLicense = dao.countByLicense().associate { it.name to it.cnt },
            newLast7d = dao.countNewSince(weekAgo),
            updatedLast7d = dao.countUpdatedSince(weekAgo),
            versionBumpsLast7d = dao.countVersionBumpsSince(weekAgo),
            aiTagCount = dao.countAiTag(),
            menuBarTagCount = dao.countMenuBarTag(),
        )
    }

    /**
     * 재수집 병합: draft null → 기존값 유지 (보강분 보호).
     * 설명·노트는 버전 변경 시 draft, 아니면 긴 쪽. 라이선스는 OSS>PAID>FREE 우선.
     * firstSeenAt·isNew·수동 오버라이드는 기존 유지.
     */
    internal fun mergeApps(existing: App, draft: App): App {
        // T-132: 공백 차이 버전 오판 방지 (정규화 비교)
        val versionChanged = draft.version != null && existing.version != null &&
            !com.borasarang.macjupjup.util.MergeUtils.sameVersion(draft.version, existing.version)
        return draft.copy(
            firstSeenAt = existing.firstSeenAt,
            // isNew 정책: 버전 bump되면 정착 앱으로 간주, NEW 즉시 해제
            isNew = if (versionChanged) false else existing.isNew,
            licenseOverride = existing.licenseOverride,
            license = maxLicense(existing.license, draft.license),
            descriptionSnippet = longer(existing.descriptionSnippet, draft.descriptionSnippet),
            releaseNotes = if (versionChanged) {
                draft.releaseNotes ?: existing.releaseNotes
            } else {
                longer(existing.releaseNotes, draft.releaseNotes)
            },
            releaseNotesSummary = if (versionChanged) {
                draft.releaseNotesSummary ?: existing.releaseNotesSummary
            } else {
                draft.releaseNotesSummary ?: existing.releaseNotesSummary
            },
            iconUrl = draft.iconUrl ?: existing.iconUrl,
            descriptionKo = draft.descriptionKo ?: existing.descriptionKo,
            releaseNotesKo = if (versionChanged) draft.releaseNotesKo else {
                draft.releaseNotesKo ?: existing.releaseNotesKo
            },
            screenshotUrls = draft.screenshotUrls ?: existing.screenshotUrls,
            sellerName = draft.sellerName ?: existing.sellerName,
            fileSize = draft.fileSize ?: existing.fileSize,
            minOs = draft.minOs ?: existing.minOs,
            contentRating = draft.contentRating ?: existing.contentRating,
            stars = draft.stars ?: existing.stars,
            averageRating = draft.averageRating ?: existing.averageRating,
            ratingCount = draft.ratingCount ?: existing.ratingCount,
            primaryLanguage = draft.primaryLanguage ?: existing.primaryLanguage,
            topics = draft.topics ?: existing.topics,
            forks = draft.forks ?: existing.forks,
            issues = draft.issues ?: existing.issues,
            licenseName = draft.licenseName ?: existing.licenseName,
            homepageUrl = draft.homepageUrl ?: existing.homepageUrl,
            repoFullName = draft.repoFullName ?: existing.repoFullName,
            trackId = draft.trackId ?: existing.trackId,
            price = if (draft.price > 0) draft.price else existing.price,
            // 카테고리는 최초 분류 유지 (필터 안정성). 태그는 합집합
            category = existing.category,
            tags = unionTags(existing.tags, draft.tags),
            version = draft.version ?: existing.version,
            prevVersion = if (versionChanged) existing.version else {
                draft.prevVersion ?: existing.prevVersion
            },
            releaseDate = draft.releaseDate ?: existing.releaseDate,
            sourceId = existing.sourceId,
        )
    }

    private fun longer(a: String?, b: String?): String? {        if (a.isNullOrBlank()) return b
        if (b.isNullOrBlank()) return a
        return if (b.length > a.length) b else a
    }

    private fun maxLicense(a: String, b: String): String {        val rank = mapOf(
            com.borasarang.macjupjup.util.Constants.LICENSE_OSS to 3,
            com.borasarang.macjupjup.util.Constants.LICENSE_PAID to 2,
            com.borasarang.macjupjup.util.Constants.LICENSE_FREE to 1,
        )
        return if ((rank[b] ?: 0) >= (rank[a] ?: 0)) b else a
    }

    /**
     * T-071: 동일 이름·다른 개발사 형제 찾기 (P1-1 배치판: 미리 조회한 행 목록에서 판정).
     * 둘 중 하나라도 수집원 표기 개발사(HN·PH·MMB)일 때만 병합 대상.
     * 실개발사가 다른 동명이앱 보호.
     */
    internal fun findSiblingIn(
        rows: List<App>,
        draft: App,
    ): App? {
        if (draft.name.isBlank()) return null
        val candidates = rows.filter { it.id != draft.id }
        if (candidates.isEmpty()) return null
        // P0-3: 후보 다수면 임의 흡수 금지 — 병합 중단 + 경고 (동명이앱 오흡수 방지)
        if (candidates.size > 1) {
            DebugLogger.w("병합", "동명이앱 ${candidates.size}건 — 병합 중단 name=${draft.name}")
            return null
        }
        val placeholder = com.borasarang.macjupjup.crawler.itunes.ITunesNameMatcher
            .isPlaceholderDeveloper(draft.developer)
        return candidates.firstOrNull { s ->
            placeholder || com.borasarang.macjupjup.crawler.itunes.ITunesNameMatcher
                .isPlaceholderDeveloper(s.developer)
        }
    }

    private fun unionTags(a: String?, b: String?): String? {        val set = LinkedHashSet<String>()
        a?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { set.addAll(it) }
        b?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { set.addAll(it) }
        return set.joinToString(",").ifBlank { null }
    }

    /** 수집처 완전 제거 시 고아 데이터 정리 (단일 트랜잭션, P0-4).
     *  순서: 해당 출처 매핑 삭제 → 대표 앱 중 매핑 0건 고아 삭제(버전 이력 동반) → 수집 로그 삭제.
     *  타 출처와 병합된 앱은 살아남음. 반환 = 삭제된 앱 수. */
    suspend fun purgeSource(sourceId: String, sourceName: String): Int {
        val mappingDao = db.appSourceMappingDao()
        var deletedMaps = 0
        var deletedApps = 0
        var deletedLogs = 0
        try {
            db.withTransaction {
                deletedMaps = mappingDao.deleteBySourceName(sourceName)
                val candidates = db.appDao().getIdsBySourceId(sourceId)
                for (appId in candidates) {
                    if (mappingDao.getByApp(appId).isEmpty()) {
                        deletedApps += db.appDao().deleteById(appId)
                        db.versionHistoryDao().deleteByApp(appId)
                    }
                }
                deletedLogs = db.crawlLogDao().deleteBySource(sourceId)
            }
        } catch (e: Exception) {
            DebugLogger.e("정리", "E-AND-DB-0402", "수집처 제거 실패 source=$sourceId: ${e.message}", e)
        }
        DebugLogger.i("정리", "[FEATURE] 수집처 제거 source=$sourceId 매핑 ${deletedMaps}건·앱 ${deletedApps}건·로그 ${deletedLogs}건")
        return deletedApps
    }
    /** isNew 정책 일괄 적용: 7일 경과 NEW 해제. 시작 시 + 보관 정리 시 호출 */
    suspend fun clearStaleNewFlags(): Int {
        val weekAgo = System.currentTimeMillis() - 7 * TimeUtils.MILLIS_PER_DAY
        return try {
            val n = db.appDao().clearStaleNew(weekAgo)
            if (n > 0) DebugLogger.i("정리", "NEW 해제 ${n}건 (7일 경과)")
            n
        } catch (e: Exception) {
            DebugLogger.e("정리", "E-AND-DB-0402", "NEW 해제 실패: ${e.message}", e)
            0
        }
    }

    /** 보관기간 초과 데이터 정리. 반환 = 삭제된 앱 수 */
    suspend fun cleanup(retentionDays: Int): Int {        val before = System.currentTimeMillis() - retentionDays * TimeUtils.MILLIS_PER_DAY
        var deletedApps = 0
        try {
            clearStaleNewFlags()
            db.withTransaction {
                // P0-5: 앱 삭제 전 매핑·버전 이력 동반 삭제 (댕글링 방지)
                val ids = db.appDao().getIdsOlderThan(before)
                val mappingDao = db.appSourceMappingDao()
                val versionDao = db.versionHistoryDao()
                for (id in ids) {
                    mappingDao.deleteByApp(id)
                    versionDao.deleteByApp(id)
                    deletedApps += db.appDao().deleteById(id)
                }
                versionDao.deleteOlderThan(before)
                db.crawlLogDao().deleteOlderThan(before)
                db.notificationLogDao().deleteOlderThan(before)
            }
        } catch (e: Exception) {
            DebugLogger.e("정리", "E-AND-DB-0402", "보관 정리 실패: ${e.message}", e)
        }
        DebugLogger.i("정리", "보관 ${retentionDays}일 초과 정리: 앱 ${deletedApps}건")
        return deletedApps
    }
}

data class SaveResult(
    val total: Int,
    val created: Int,
    val updated: Int,
    val createdIds: List<String> = emptyList(),
)
