package com.borasarang.communityjupjup.data.seed

import com.borasarang.communityjupjup.data.db.CommunityDatabase
import com.borasarang.communityjupjup.data.db.entity.CrawlSource
import com.borasarang.communityjupjup.data.db.entity.SiteBoard
import com.borasarang.communityjupjup.util.category.CommunityCategories
import com.borasarang.communityjupjup.util.Constants
import com.borasarang.communityjupjup.util.DebugLogger
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * MVP 시드: 3개 사이트·5개 소스·보드 매핑 (V2 §2 중 차단 없는 곳만).
 * selectorConfigJson = 코드 기본값, 어드민 테스트 API로 조정 가능.
 * DB v1 기존 기기는 없는 소스만 추가(seedMissing).
 */
object InitialDataSeeder {

    private fun boardSelector(
        listRow: String,
        title: String,
        author: String,
        time: String,
        likes: String = "",
        views: String = "",
        comments: String = "",
        snippet: String = "",
        excludeRow: String = "",
        detailContent: String = "",
        thumbnail: String = "",
    ): String = buildJsonObject {
        put("listRow", listRow)
        put("title", title)
        put("author", author)
        put("time", time)
        put("likes", likes)
        put("views", views)
        put("comments", comments)
        put("snippet", snippet)
        put("excludeRow", excludeRow)
        put("detailContent", detailContent)
        put("thumbnail", thumbnail)
    }.toString()

    private fun source(
        id: String,
        name: String,
        domain: String,
        baseUrl: String,
        intervalMinutes: Int,
        selectorJson: String,
        enabled: Boolean = true,
        errorMessage: String? = null,
    ) = CrawlSource(
        id = id,
        name = name,
        type = Constants.TYPE_BOARD,
        domain = domain,
        baseUrl = baseUrl,
        enabled = enabled,
        intervalHours = intervalMinutes / 60,
        intervalMinutes = intervalMinutes,
        lastRunAt = null,
        lastStatus = Constants.STATUS_NEVER_RUN,
        errorMessage = errorMessage,
        selectorConfigJson = selectorJson,
    )

    private fun board(sourceId: String, boardId: String, boardName: String, boardUrl: String, categoryId: Int, intervalMinutes: Int = 30) =
        SiteBoard(
            sourceId = sourceId,
            boardId = boardId,
            boardName = boardName,
            boardUrl = boardUrl,
            categoryId = categoryId,
            intervalMinutes = intervalMinutes,
        )

    private fun seeds(): List<CrawlSource> = listOf(
        // 클리앙 실측 구조 (2026-09-21): div.list_item > a.list_subject, 공지(.notice) 제외,
        // 시각은 .timestamp(YYYY-MM-DD HH:mm:ss), 조회수는 k/M 단위
        source(
            id = Constants.SOURCE_CLIEN_PARK,
            name = "클리앙 모두의공원",
            domain = "clien.net",
            baseUrl = "https://www.clien.net",
            intervalMinutes = 30,
            selectorJson = boardSelector(
                listRow = "div.list_item",
                title = "a.list_subject, .list_title a",
                author = ".list_author .nickname, .nickname",
                time = ".list_time .timestamp, .list_time",
                likes = ".list_symph span",
                views = ".list_hit .hit, .list_hit",
                comments = ".rSymph05",
                excludeRow = ".notice",
                detailContent = ".post_content article, .post_article",
            ),
        ),
        source(
            id = Constants.SOURCE_CLIEN_JIRUM,
            name = "클리앙 알뜰구매",
            domain = "clien.net",
            baseUrl = "https://www.clien.net",
            intervalMinutes = 30,
            selectorJson = boardSelector(
                listRow = "div.list_item",
                title = "a.list_subject, .list_title a",
                author = ".list_author .nickname, .nickname",
                time = ".list_time .timestamp, .list_time",
                likes = ".list_symph span",
                views = ".list_hit .hit, .list_hit",
                comments = ".rSymph05",
                excludeRow = ".notice",
                detailContent = ".post_content article, .post_article",
            ),
        ),
        // 에펨코리아: JS 보안 시스템으로 HTTP 수집 불가 (실측 2026-09-21) — 비활성 보관
        source(
            id = Constants.SOURCE_FM_POTEN,
            name = "에펨코리아 포텐",
            domain = "fmkorea.com",
            baseUrl = "https://www.fmkorea.com",
            intervalMinutes = 30,
            selectorJson = boardSelector(
                listRow = "ul.bd_lst li, table.bd_lst tr",
                title = ".title a, td.title a",
                author = ".author, td.author",
                time = ".time, td.time",
                likes = ".m_no, td.m_no",
                views = ".readed_count, td.readed_count",
                comments = ".replyNum, td.replyNum",
            ),
            enabled = false,
            errorMessage = "JS 보안 시스템 — HTTP 수집 불가 (Playwright 필요)",
        ),
        source(
            id = Constants.SOURCE_FM_HUMOR,
            name = "에펨코리아 유머",
            domain = "fmkorea.com",
            baseUrl = "https://www.fmkorea.com",
            intervalMinutes = 30,
            selectorJson = boardSelector(
                listRow = "ul.bd_lst li, table.bd_lst tr",
                title = ".title a, td.title a",
                author = ".author, td.author",
                time = ".time, td.time",
                likes = ".m_no, td.m_no",
                views = ".readed_count, td.readed_count",
                comments = ".replyNum, td.replyNum",
            ),
            enabled = false,
            errorMessage = "JS 보안 시스템 — HTTP 수집 불가 (Playwright 필요)",
        ),
        source(
            id = Constants.SOURCE_RULI_BEST,
            name = "루리웹 베스트",
            domain = "ruliweb.com",
            baseUrl = "https://bbs.ruliweb.com",
            intervalMinutes = 30,
            selectorJson = boardSelector(
                listRow = "table.board_list_table tr",
                title = "td.subject a, .subject a",
                author = "td.writer, .writer",
                time = "td.time, time",
                likes = "td.recomd, .recomd",
                views = "td.hit, .hit",
                comments = "td.num_comment, .num_comment",
                detailContent = ".view_content",
            ),
        ),
        // 뽐뿌 국내게시판 (실측 2026-09-21): tr.baseList > a.baseList-title
        source(
            id = Constants.SOURCE_PPOMPPU,
            name = "뽐뿌 국내뽐뿌",
            domain = "ppomppu.co.kr",
            baseUrl = "https://www.ppomppu.co.kr",
            intervalMinutes = 30,
            selectorJson = boardSelector(
                listRow = "tr.baseList",
                title = "a.baseList-title",
                author = ".list_name",
                time = "time.baseList-time",
                likes = "td.baseList-rec",
                views = "td.baseList-views",
                comments = "span.baseList-c",
                thumbnail = "a.baseList-thumb img",
                detailContent = ".board-contents",
            ),
        ),
        // 디시인사이드 실시간베스트 (실측): tr.ub-content, 공지는 data-type으로 제외
        source(
            id = Constants.SOURCE_DC_BEST,
            name = "디시 실시간베스트",
            domain = "dcinside.com",
            baseUrl = "https://gall.dcinside.com",
            intervalMinutes = 30,
            selectorJson = boardSelector(
                listRow = "tr.ub-content",
                title = "td.gall_tit a",
                author = "td.gall_writer",
                time = "td.gall_date",
                likes = "td.gall_recommend",
                views = "td.gall_count",
                comments = "a.reply_num",
                excludeRow = "[data-type=icon_notice]",
                detailContent = ".view_content_wrap",
            ),
        ),
        // 보배드림 베스트 (실측): tr[itemscope] > td.pl14 a.bsubject
        source(
            id = Constants.SOURCE_BOBA_BEST,
            name = "보배드림 베스트",
            domain = "bobaedream.co.kr",
            baseUrl = "https://www.bobaedream.co.kr",
            intervalMinutes = 30,
            selectorJson = boardSelector(
                listRow = "tr[itemscope]",
                title = "td.pl14 a.bsubject",
                author = "td.author02 span.author",
                time = "td.date",
                likes = "td.recomm",
                views = "td.count",
                comments = ".totreply",
            ),
        ),
        // 더쿠 HOT (실측, XE 테이블): notices는 .notice 제외, 작성자 미표시
        source(
            id = Constants.SOURCE_THEQOO_HOT,
            name = "더쿠 HOT",
            domain = "theqoo.net",
            baseUrl = "https://theqoo.net",
            intervalMinutes = 30,
            selectorJson = boardSelector(
                listRow = "table.bd_lst tbody tr",
                title = "td.title a",
                author = "td.author",
                time = "td.time",
                likes = "",
                views = "td.m_no",
                comments = "a.replyNum",
                excludeRow = ".notice",
                detailContent = ".xe_content",
            ),
        ),
        // 오늘의유머 베스트오브베스트 (실측): tr.view
        source(
            id = Constants.SOURCE_OHU_BEST,
            name = "오늘의유머 베오베",
            domain = "todayhumor.co.kr",
            baseUrl = "http://todayhumor.co.kr",
            intervalMinutes = 30,
            selectorJson = boardSelector(
                listRow = "tr.view",
                title = "td.subject a",
                author = "td.name a",
                time = "td.date",
                likes = "td.oknok",
                views = "td.hits",
                comments = "span.list_memo_count_span",
            ),
        ),
    )

    private fun boards(): List<Triple<String, String, SiteBoard>> = listOf(
        Triple(
            Constants.SOURCE_CLIEN_PARK, "park",
            board(Constants.SOURCE_CLIEN_PARK, "park", "모두의공원", "https://www.clien.net/service/board/park", CommunityCategories.HUMOR),
        ),
        Triple(
            Constants.SOURCE_CLIEN_JIRUM, "jirum",
            board(Constants.SOURCE_CLIEN_JIRUM, "jirum", "알뜰구매", "https://www.clien.net/service/board/jirum", CommunityCategories.HOTDEAL),
        ),
        Triple(
            Constants.SOURCE_FM_POTEN, "poten",
            board(Constants.SOURCE_FM_POTEN, "poten", "포텐", "https://www.fmkorea.com/index.php?mid=poten", CommunityCategories.BREAKING),
        ),
        Triple(
            Constants.SOURCE_FM_HUMOR, "humor",
            board(Constants.SOURCE_FM_HUMOR, "humor", "유머", "https://www.fmkorea.com/index.php?mid=humor", CommunityCategories.HUMOR),
        ),
        Triple(
            Constants.SOURCE_RULI_BEST, "best",
            board(Constants.SOURCE_RULI_BEST, "best", "베스트", "https://bbs.ruliweb.com/best", CommunityCategories.BREAKING),
        ),
        Triple(
            Constants.SOURCE_PPOMPPU, "ppomppu",
            board(Constants.SOURCE_PPOMPPU, "ppomppu", "국내뽐뿌", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu", CommunityCategories.HOTDEAL),
        ),
        Triple(
            Constants.SOURCE_DC_BEST, "dcbest",
            board(Constants.SOURCE_DC_BEST, "dcbest", "실시간베스트", "https://gall.dcinside.com/board/lists/?id=dcbest", CommunityCategories.BREAKING),
        ),
        Triple(
            Constants.SOURCE_BOBA_BEST, "best",
            board(Constants.SOURCE_BOBA_BEST, "best", "베스트", "https://www.bobaedream.co.kr/list?code=best", CommunityCategories.HUMOR),
        ),
        Triple(
            Constants.SOURCE_THEQOO_HOT, "hot",
            board(Constants.SOURCE_THEQOO_HOT, "hot", "HOT", "https://theqoo.net/hot", CommunityCategories.BREAKING),
        ),
        Triple(
            Constants.SOURCE_OHU_BEST, "bestofbest",
            board(Constants.SOURCE_OHU_BEST, "bestofbest", "베스트오브베스트", "http://todayhumor.co.kr/board/list.php?table=bestofbest", CommunityCategories.HUMOR),
        ),
    )

    suspend fun seedIfEmpty(db: CommunityDatabase) {
        if (db.crawlSourceDao().getAll().isNotEmpty()) {
            seedMissing(db)
            return
        }
        DebugLogger.i("시드", "초기 수집 소스 시드 시작")
        db.crawlSourceDao().upsertAll(seeds())
        db.siteBoardDao().upsertAll(boards().map { it.third })
        DebugLogger.i("시드", "초기 수집 소스 시드 완료")
    }

    /** 기존 DB에 없는 신규 소스·보드만 추가 + 기존 소스 셀렉터/활성 실측 갱신 */
    suspend fun seedMissing(db: CommunityDatabase) {
        val dao = db.crawlSourceDao()
        val missing = seeds().filter { dao.getById(it.id) == null }
        if (missing.isNotEmpty()) {
            DebugLogger.i("시드", "신규 소스 추가: ${missing.map { it.id }}")
            dao.upsertAll(missing)
        }
        // 실측 조정 승계: 기존 행의 셀렉터·활성·사유를 최신 시드로 갱신
        for (s in seeds()) {
            if (dao.getById(s.id) != null && missing.none { it.id == s.id }) {
                dao.updateSelector(s.id, s.selectorConfigJson, s.enabled, s.errorMessage)
            }
        }
        val boardDao = db.siteBoardDao()
        val existing = boardDao.getAll().map { it.sourceId to it.boardId }.toSet()
        val missingBoards = boards().map { it.third }.filter { (it.sourceId to it.boardId) !in existing }
        if (missingBoards.isNotEmpty()) {
            DebugLogger.i("시드", "신규 보드 추가: ${missingBoards.map { it.boardId }}")
            boardDao.upsertAll(missingBoards)
        }
    }
}
