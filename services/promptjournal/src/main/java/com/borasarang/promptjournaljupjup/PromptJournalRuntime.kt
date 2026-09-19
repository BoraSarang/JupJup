package com.borasarang.promptjournaljupjup

import android.content.Context
import com.borasarang.promptjournaljupjup.data.db.PromptJournalDatabase
import com.borasarang.promptjournaljupjup.data.db.entity.Prompt
import com.borasarang.promptjournaljupjup.data.preferences.PreferencesManager
import com.borasarang.promptjournaljupjup.data.preferences.ModelEnabledStore
import com.borasarang.promptjournaljupjup.data.preferences.ProviderKeyStore
import com.borasarang.promptjournaljupjup.data.repository.PromptExecutionRepository
import com.borasarang.promptjournaljupjup.data.repository.PromptRepository
import com.borasarang.promptjournaljupjup.ai.ModelCatalog
import com.borasarang.promptjournaljupjup.server.HttpServerService
import com.borasarang.promptjournaljupjup.util.DebugLogger
import com.borasarang.promptjournaljupjup.worker.PromptJournalScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PromptJournalRuntime {

    private val initLock = Any()

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: PromptJournalDatabase
        private set
    lateinit var promptRepository: PromptRepository
        private set
    lateinit var promptExecutionRepository: PromptExecutionRepository
        private set
    lateinit var preferences: PreferencesManager
        private set
    lateinit var providerKeys: ProviderKeyStore
        private set
    lateinit var scheduler: PromptJournalScheduler
        private set

    val context: Context
        get() = appContext

    val isInitialized: Boolean
        get() = initialized

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            initialized = true
        }
        DebugLogger.init(appContext)
        DebugLogger.i("앱", "프롬프트 저널 시작")

        migrateLegacyFiles()

        database = try {
            PromptJournalDatabase.getInstance(appContext)
        } catch (e: Exception) {
            DebugLogger.e("앱", Constants.ERR_DB_INIT_FAILED, "DB 열기 실패, 백업 후 재생성: ${e.message}", e)
            backupDatabaseFile()
            PromptJournalDatabase.resetInstance()
            PromptJournalDatabase.getInstanceFallback(appContext)
        }
        promptRepository = PromptRepository(database)
        promptExecutionRepository = PromptExecutionRepository(database)
        preferences = PreferencesManager.getInstance(appContext)
        providerKeys = ProviderKeyStore.getInstance(appContext)
        scheduler = PromptJournalScheduler(appContext)

        ModelCatalog.init()
        ModelCatalog.attachStore(ModelEnabledStore.getInstance(appContext))

        appScope.launch(Dispatchers.IO) {
            ModelCatalog.restoreEnabled()
            seedIfEmpty()

            val settings = preferences.getSettings()
            if (settings.autoStart) {
                DebugLogger.i("앱", "자동 시작 설정 켜짐 — 서버 시작")
                HttpServerService.start(appContext)
            } else {
                DebugLogger.i("앱", "자동 시작 꺼짐 — 서버 미시작")
            }

            scheduler.migrateLegacyWork()
            scheduler.rescheduleAll()
        }
    }

    /**
     * 개명(프롬프트팩토리 → 프롬프트 저널) 1회 마이그레이션: 구 파일명 데이터를 신 파일명으로 승계.
     * DB 본체+저널 형제 파일, DataStore 3종. 신 파일이 이미 있으면(재실행) 스킵.
     */
    private fun migrateLegacyFiles() {
        val dsDir = java.io.File(appContext.filesDir, "datastore")
        renameFile(java.io.File(dsDir, "pf_settings.preferences_pb"), java.io.File(dsDir, "pj_settings.preferences_pb"))
        renameFile(java.io.File(dsDir, "pf_api_keys.preferences_pb"), java.io.File(dsDir, "pj_api_keys.preferences_pb"))
        renameFile(java.io.File(dsDir, "pf_models.preferences_pb"), java.io.File(dsDir, "pj_models.preferences_pb"))
        val dbDir = appContext.getDatabasePath(PromptJournalDatabase.DB_NAME).parentFile ?: return
        for (suffix in listOf("", "-journal", "-shm", "-wal")) {
            renameFile(
                java.io.File(dbDir, "promptfactory.db$suffix"),
                java.io.File(dbDir, "${PromptJournalDatabase.DB_NAME}$suffix"),
            )
        }
    }

    private fun renameFile(src: java.io.File, dst: java.io.File) {
        try {
            if (!src.exists() || dst.exists()) return
            dst.parentFile?.mkdirs()
            if (src.renameTo(dst)) {
                DebugLogger.i("앱", "구 데이터 승계 ${src.name} → ${dst.name}")
            } else {
                DebugLogger.w("앱", "구 데이터 승계 실패 ${src.absolutePath}")
            }
        } catch (e: Exception) {
            DebugLogger.w("앱", "구 데이터 승계 실패 ${src.name}: ${e.message}")
        }
    }

    /** 첫 실행 시 MD 프롬프트 1개 시드 등록 */
    private suspend fun seedIfEmpty() {
        if (promptRepository.count() > 0) return
        val seed = Prompt(
            title = "무료 AI 모델 통합 일일 리포트",
            content = SEED_PROMPT,
            provider = "OPENROUTER",
            modelId = "nvidia/nemotron-3-super-120b-a12b:free",
            scheduleType = "daily",
            scheduleValue = "09:00",
            enabled = true,
            usePreviousResult = true,
        )
        promptRepository.save(seed)
        DebugLogger.i("앱", "시드 프롬프트 1개 등록: 무료 AI 모델 통합 일일 리포트")
    }

    private fun backupDatabaseFile() {
        try {
            val src = appContext.getDatabasePath(PromptJournalDatabase.DB_NAME)
            if (!src.exists()) return
            val dir = java.io.File(appContext.filesDir, "db-backup").apply { mkdirs() }
            val dst = java.io.File(dir, "${PromptJournalDatabase.DB_NAME}.${System.currentTimeMillis()}.bak")
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
            DebugLogger.i("앱", "DB 백업 완료 ${dst.absolutePath} (${dst.length()}B)")
        } catch (e: Exception) {
            DebugLogger.e("앱", Constants.ERR_DB_INIT_FAILED, "DB 백업 실패: ${e.message}", e)
        }
    }

    private const val SEED_PROMPT = """너는 AI 모델 트래킹 애널리스트야. 오늘 날짜 기준으로 아래 서비스들의
"완전 무료(Free)" 모델 현황과 관련 뉴스를 조사해서 한국어 리포트를
작성해줘.

[조사 대상 서비스]
기본으로 아래 서비스들을 전부 확인하고, 그 외에 요즘 화제가 되는
유사 서비스(무료 API/무료 크레딧으로 LLM을 제공하는 곳)가 있으면
추가로 찾아서 포함해줘:
- OpenCode Zen (https://opencode.ai/docs/zen/)
- OpenRouter 무료 모델 (":free" 접미사 붙은 모델들, https://openrouter.ai/models?max_price=0 또는 공식 모델 목록)
- NVIDIA NIM / build.nvidia.com (무료 API 크레딧으로 제공되는 모델들)
- 그 외 확인해볼 것: Google AI Studio(Gemini 무료 티어), Groq, Cerebras
  Cloud, SambaNova Cloud, Together AI 무료 티어, Cloudflare Workers AI,
  GitHub Models, Hugging Face Inference Providers 무료 티어 등
  → 이 목록에 없더라도 "오늘 새로 알게 된 무료 서비스"가 있으면
    반드시 별도로 언급해줘.

[조사 방법 원칙]
- 반드시 각 서비스의 공식 문서/공식 모델 목록 페이지를 웹서치·웹fetch로
  직접 확인해서 판단할 것. 오래된 블로그, 커뮤니티 위키, 서드파티 글의
  옛날 목록은 참고만 하고 "공식 기준 현재값"으로 덮어쓸 것.
- 완전 무료(rate limit 안에서 $0)인 모델만 "무료 모델"로 집계하고,
  "며칠간 무료 크레딧 제공" 같은 건 별도로 표시할 것.
- 스텔스(정체불명) 모델이 있으면 GitHub, Reddit, X(트위터) 등에서
  정체 추정 정보나 커뮤니티 반응을 추가로 찾아줘.

[서비스별 조사 항목]
각 서비스마다:
1. 현재 완전 무료 모델 전체 목록 (모델명 / ID / 제공사)
2. 전날 대비 신규 추가 / 제거(유료 전환 포함) / 스펙·정책 변경
3. 무료 제공 조건 (상시 무료 vs 한시적 프로모션 vs 사용량 제한형
   rate limit 무료)

[모델별 상세 분석]
모든 서비스를 통틀어, 새로 등장했거나 주목할 만한 모델에 대해:
- 모델 ID / 실제 제공사(스텔스면 "정체불명"으로 표기)
- 아키텍처·파라미터(공개된 경우), 컨텍스트 윈도우, 최대 출력 토큰
- 지원 입력 모달리티 (텍스트/이미지/오디오/영상)
- 핵심 강점 및 대표 사용처
- 데이터/프라이버시 정책 (학습 활용 여부, 제로 리테인 여부)

[통합 카테고리 분류 및 순위]
서비스 구분 없이 전체 무료 모델을 모아서 아래 카테고리로 분류하고
순위를 매겨줘 (모델명 옆에 어느 서비스에서 제공되는지 괄호로 표기,
동일 모델이 여러 서비스에 있으면 같이 표기):
- 💻 코딩 / 에이전틱 코딩
- 🧠 종합 추론·플래닝·오케스트레이션
- ⚡ 경량·고속(대량 반복 작업용)
- 📈 금융·증권 특화
- 💬 멀티모달·일반 채팅
- (그 외 눈에 띄는 특화 카테고리가 있으면 자유롭게 추가)
근거가 부족하면 "확인된 벤치마크 없음 / 커뮤니티 평가 기준"이라고
명시할 것. 해당 카테고리에 모델이 없으면 억지로 채우지 말고
"해당 없음"이라고 쓸 것.

[오늘의 뉴스 + 인사이트] ← 중요
아래 형식으로 날짜별 뉴스와 그에 대한 짧은 인사이트를 반드시 포함해줘:
- 오늘(또는 최근 1~2일) 발표된 신규 모델 출시, 가격 정책 변경,
  무료 티어 조건 변경, 서비스 장애/이슈 등을 뉴스 형태로 정리
  (예: "9월 X일 — OpenRouter, A모델 무료 티어 종료 발표")
- 각 뉴스 옆에 "왜 중요한지 / 실무에 어떤 영향이 있는지" 1~2줄
  인사이트를 덧붙일 것 (단순 사실 나열 금지)
- 특별한 뉴스가 없는 날은 "오늘은 주요 뉴스 없음"이라고 명시하고,
  대신 최근 트렌드(예: 무료 티어 모델들이 점점 대형화되는 추세 등)에
  대한 짧은 인사이트 한 줄을 제공할 것

[변경사항 요약 — 리포트 최상단에 배치]
전날 대비:
- 🆕 신규 추가된 무료 모델 (서비스명 포함)
- ❌ 무료 목록에서 빠진 모델 (서비스명 포함)
- 🔄 스펙/가격/정책이 바뀐 모델
전날 기록:
[어제까지 기록 — 없으면 이 줄과 아래 내용 삭제]

[출력 형식 — 모바일 화면 렌더링 기준, 반드시 준수]
0. 맨 첫 줄은 35자 이내의 헤드라인 1줄만 출력 (마크다운 기호 없이 평문).
   예: 신규 무료 모델 없음
1. 오늘의 뉴스 + 인사이트 (헤드라인 다음 줄부터, 불릿 목록)
2. 변경사항 요약 (없으면 "어제와 동일, 변경 없음" 명시,
   짧은 요약에만 마크다운 표 사용 가능 — 4행 이하, 셀당 20자 이하)
3. 서비스별 현재 무료 모델 목록 (표 사용 금지)
   - 서비스마다 ### 서비스명 소제목 + 모델별 불릿 1줄:
     `- \`모델ID\` (제공사) — 조건·제한 1줄`
   - 한 줄에 1개 모델만, 여러 모델을 한 줄·한 셀에 나열 금지
   - `<br>`으로 이어붙이기 금지, 반드시 줄바꿈+불릿으로 분리
4. 주목할 만한 모델 상세 분석 (모델마다 #### 모델명 소제목 + 불릿)
5. 통합 카테고리별 분류 + 순위 (카테고리마다 소제목 + 순위 불릿,
   모델명 옆에 서비스명 괄호 표기)
- 톤: 간결한 보고서체, 불필요한 서론/결론 생략
- 마크다운 표(|)는 2번 요약에만 사용, 나머지는 소제목+불릿
- 링크는 [이름](URL) 형식으로, 출처(URL) 병기
- 확인 안 된 내용은 추측이라고 명확히 표시"""
}