package com.borasarang.macjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.db.entity.AppSourceMapping

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<App>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: App): Long

    @Query("SELECT * FROM apps WHERE id = :id")
    suspend fun getById(id: String): App?

    /** 저장 배치용 일괄 조회 (N+1 제거, P1-1) */
    @Query("SELECT * FROM apps WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<App>

    @Query("SELECT COUNT(*) FROM apps")
    suspend fun count(): Int

    @Query(
        """SELECT * FROM apps
        WHERE (:license IS NULL OR license = :license)
          AND (:category IS NULL OR category = :category)
          AND (:tag IS NULL OR tags LIKE '%' || :tag || '%')
          AND (:q IS NULL OR name LIKE '%' || :q || '%' OR developer LIKE '%' || :q || '%')
          AND (:bumped = 0 OR (isNew = 0 AND version IS NOT NULL))
          AND (:updatedOnly = 0 OR prevVersion IS NOT NULL)
          AND (:newOnly = 0 OR isNew = 1)
          AND (:filterBySource = 0 OR sourceId IN (:sourceIds))
          AND (:excludeGames = 0 OR category != '게임')
        ORDER BY
          CASE WHEN :sort = 'mas' THEN (CASE WHEN trackId IS NOT NULL THEN 0 ELSE 1 END) END ASC,
          CASE WHEN :sort = 'stars' THEN stars END DESC,
          CASE WHEN :sort = 'rating' THEN averageRating END DESC,
          CASE WHEN :sort = 'updated' THEN lastUpdatedAt END DESC,
          CASE WHEN :sort = 'priceAsc' THEN price END ASC,
          lastUpdatedAt DESC
        LIMIT :limit OFFSET :offset"""
    )
    suspend fun listFiltered(
        license: String?,
        category: String?,
        tag: String?,
        q: String?,
        sort: String,
        limit: Int,
        offset: Int,
        bumped: Boolean = false,
        updatedOnly: Boolean = false,
        newOnly: Boolean = false,
        filterBySource: Boolean = false,
        sourceIds: List<String> = emptyList(),
        excludeGames: Boolean = false,
    ): List<App>

    @Query(
        """SELECT COUNT(*) FROM apps
        WHERE (:license IS NULL OR license = :license)
          AND (:category IS NULL OR category = :category)
          AND (:tag IS NULL OR tags LIKE '%' || :tag || '%')
          AND (:q IS NULL OR name LIKE '%' || :q || '%' OR developer LIKE '%' || :q || '%')
          AND (:bumped = 0 OR (isNew = 0 AND version IS NOT NULL))
          AND (:updatedOnly = 0 OR prevVersion IS NOT NULL)
          AND (:newOnly = 0 OR isNew = 1)
          AND (:filterBySource = 0 OR sourceId IN (:sourceIds))
          AND (:excludeGames = 0 OR category != '게임')"""
    )
    suspend fun countFiltered(
        license: String?,
        category: String?,
        tag: String?,
        q: String?,
        bumped: Boolean = false,
        updatedOnly: Boolean = false,
        newOnly: Boolean = false,
        filterBySource: Boolean = false,
        sourceIds: List<String> = emptyList(),
        excludeGames: Boolean = false,
    ): Int

    /** 맥 게임 목록 (PLAN_v21). genre는 tags LIKE '%{genre}%' (한글 장르) */
    @Query(
        """SELECT * FROM apps
        WHERE category = '게임'
          AND (:genre IS NULL OR tags LIKE '%' || :genre || '%')
          AND (:store IS NULL OR tags LIKE '%' || :store || '%')
          AND (:sourceId IS NULL OR sourceId = :sourceId)
          AND (:q IS NULL OR name LIKE '%' || :q || '%')
        ORDER BY
          CASE WHEN :sort = 'rating' THEN averageRating END DESC,
          releaseDate DESC,
          lastUpdatedAt DESC
        LIMIT :limit OFFSET :offset"""
    )
    suspend fun listGames(
        genre: String?,
        store: String?,
        sourceId: String?,
        q: String?,
        sort: String,
        limit: Int,
        offset: Int,
    ): List<App>

    @Query(
        """SELECT COUNT(*) FROM apps
        WHERE category = '게임'
          AND (:genre IS NULL OR tags LIKE '%' || :genre || '%')
          AND (:store IS NULL OR tags LIKE '%' || :store || '%')
          AND (:sourceId IS NULL OR sourceId = :sourceId)
          AND (:q IS NULL OR name LIKE '%' || :q || '%')"""
    )
    suspend fun countGames(genre: String?, store: String?, sourceId: String?, q: String?): Int

    @Query("SELECT * FROM apps WHERE trackId = :trackId LIMIT 1")
    suspend fun getByTrackId(trackId: Long): App?

    @Query("SELECT * FROM apps WHERE repoFullName IS NOT NULL ORDER BY lastUpdatedAt ASC LIMIT :limit")
    suspend fun getReposForReleaseCheck(limit: Int): List<App>

    @Query("SELECT * FROM apps WHERE trackId IS NOT NULL ORDER BY lastUpdatedAt ASC LIMIT :limit")
    suspend fun getAppsWithTrackId(limit: Int): List<App>

    @Query("SELECT * FROM apps WHERE trackId IS NULL AND repoFullName IS NULL ORDER BY lastUpdatedAt ASC LIMIT :limit")
    suspend fun getAppsWithoutIds(limit: Int): List<App>

    /** 제거된 수집처의 대표 앱 id 목록 (고아 판정용) */
    @Query("SELECT id FROM apps WHERE sourceId = :sourceId")
    suspend fun getIdsBySourceId(sourceId: String): List<String>

    /** 본문(발췌·전문) 보유 id — 목록 초안이 전부 null이어도 상세 보강 순서 결정용 */
    @Query(
        """SELECT id FROM apps WHERE id IN (:ids)
        AND (COALESCE(TRIM(longDescription), '') != ''
          OR COALESCE(TRIM(descriptionSnippet), '') != '')""",
    )
    suspend fun getIdsWithBody(ids: List<String>): List<String>

    /** 고아 앱 삭제. 반환 = 삭제 행 수 */
    @Query("DELETE FROM apps WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** 보관기간 초과 앱 id 목록 (매핑·이력 동반 삭제용, P0-5) */
    @Query("SELECT id FROM apps WHERE lastUpdatedAt < :before")
    suspend fun getIdsOlderThan(before: Long): List<String>

    /** isNew 정책: 첫 발견 7일 경과 시 NEW 해제. 와치리스트 진입 조건 */
    @Query("UPDATE apps SET isNew = 0 WHERE isNew = 1 AND firstSeenAt < :weekAgo")
    suspend fun clearStaleNew(weekAgo: Long): Int

    /** 콤마 구분자 시절에 깨진 Setapp CDN 스크린샷 정리 (v1.1 1회성 복구) */
    @Query("UPDATE apps SET screenshotUrls = NULL WHERE screenshotUrls LIKE '%cdn-cgi%'")
    suspend fun clearBrokenCdnScreenshots(): Int

    @Query(
        """SELECT * FROM apps
        WHERE (descriptionKo IS NULL AND descriptionSnippet IS NOT NULL)
           OR (releaseNotesKo IS NULL AND releaseNotes IS NOT NULL)
           OR (longDescriptionKo IS NULL AND longDescription IS NOT NULL
               AND length(longDescription) <= 4000)
           OR (descriptionSnippet LIKE '%' || char(10) || '%'
               AND (descriptionKo IS NULL OR descriptionKo NOT LIKE '%' || char(10) || '%'))
        ORDER BY lastUpdatedAt DESC LIMIT :limit""",
    )
    suspend fun getUntranslated(limit: Int): List<App>

    /** 번역 부분 업데이트. null인 쪽은 기존값 보존 (전체 덮어쓰기 번역 소실 방지, P0-1) */
    @Query(
        """UPDATE apps SET descriptionKo = COALESCE(:descKo, descriptionKo),
        releaseNotesKo = COALESCE(:notesKo, releaseNotesKo),
        longDescriptionKo = COALESCE(:longKo, longDescriptionKo) WHERE id = :id"""
    )
    suspend fun updateKo(id: String, descKo: String?, notesKo: String?, longKo: String? = null)

    /** 미번역 행 수 (인사이트 번역 커버리지용, T-131 개행 복구분 포함) */
    @Query(
        """SELECT COUNT(*) FROM apps
        WHERE (descriptionKo IS NULL AND descriptionSnippet IS NOT NULL)
           OR (releaseNotesKo IS NULL AND releaseNotes IS NOT NULL)
           OR (longDescriptionKo IS NULL AND longDescription IS NOT NULL
               AND length(longDescription) <= 4000)
           OR (descriptionSnippet LIKE '%' || char(10) || '%'
               AND (descriptionKo IS NULL OR descriptionKo NOT LIKE '%' || char(10) || '%'))"""
    )
    suspend fun countUntranslated(): Int

    // ---------- 트렌드 집계 (T-041) ----------

    @Query("SELECT category AS name, COUNT(*) AS cnt FROM apps GROUP BY category ORDER BY cnt DESC")
    suspend fun countByCategory(): List<NameCount>

    @Query("SELECT license AS name, COUNT(*) AS cnt FROM apps GROUP BY license")
    suspend fun countByLicense(): List<NameCount>

    /** 수집처별 앱 수 (대표 sourceId 기준) */
    @Query("SELECT sourceId AS name, COUNT(*) AS cnt FROM apps GROUP BY sourceId")
    suspend fun countBySource(): List<NameCount>

    @Query("SELECT COUNT(*) FROM apps WHERE firstSeenAt >= :since")
    suspend fun countNewSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM apps WHERE lastUpdatedAt >= :since AND isNew = 0")
    suspend fun countUpdatedSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM apps WHERE tags LIKE '%AI-Agent%'")
    suspend fun countAiTag(): Int

    @Query("SELECT COUNT(*) FROM apps WHERE tags LIKE '%MenuBar%'")
    suspend fun countMenuBarTag(): Int

    @Query("SELECT COUNT(*) FROM version_history WHERE detectedAt >= :since")
    suspend fun countVersionBumpsSince(since: Long): Int

    @Query("SELECT * FROM apps WHERE repoFullName = :repo LIMIT 1")
    suspend fun getByRepo(repo: String): App?

    /** T-071: 동일 이름 이개발사 중복 병합용 (정확 일치, 대소문자 무시) */
    @Query("SELECT * FROM apps WHERE LOWER(name) = LOWER(:name) ORDER BY firstSeenAt ASC")
    suspend fun findByNameIgnoreCase(name: String): List<App>

    /** 저장 배치용 이름 일괄 조회. 인자는 미리 소문자로 (P1-1) */
    @Query("SELECT * FROM apps WHERE LOWER(name) IN (:names) ORDER BY firstSeenAt ASC")
    suspend fun findByNamesLower(names: List<String>): List<App>

    @Query("DELETE FROM apps WHERE lastUpdatedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int

    /** 뉴스-앱 연동용 전체 이름 조회 (id + name만, R32) */
    @Query("SELECT id, name FROM apps")
    suspend fun getAllNames(): List<AppName>

    @Transaction
    @Query("SELECT * FROM apps WHERE id = :id")
    suspend fun getWithSources(id: String): AppWithSources?
}

/** 집계 결과 공용 */
data class NameCount(
    val name: String,
    val cnt: Int,
)

/** 뉴스-앱 연동용 이름 묶음 (R32) */
data class AppName(
    val id: String,
    val name: String,
)

/** 앱 + 출처 묶음 (상세 API 응답용) */
data class AppWithSources(
    @androidx.room.Embedded val app: App,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "appId")
    val sources: List<AppSourceMapping>,
)
