# PLAN_v4 — 프롬프트 저널 (`:services:promptjournal`) 신규 서비스

> 플랫폼: Android · v1.10.0 · 2026-09-18

---

## 1. 목표

AI 공급자(OpenRouter, NVIDIA NIM, Google AI Studio)를 선택하고, 모델을 골라,
프롬프트를 설정한 뒤 지정 시간에 자동 실행 → 응답을 저장 → 로컬 웹 페이지에서 조회하는
일일 AI 리포트 서비스.

기존 `mac`(맥줍줍) / `plan`(요금줍줍)과 동일한 격리·패턴을 따르되,
AI 호출 로직(`ai/` 패키지)은 이 모듈 전용으로 내부 배치.

---

## 2. 네이밍 & 모듈 정보

| 항목 | 값 |
|------|-----|
| 모듈 | `:services:promptjournal` |
| 네임스페이스 | `com.borasarang.promptjournaljupjup` |
| 리소스 접두사 | `pj_` |
| 포트 | 3002 (기본, 설정 변경 가능) |
| DB | `PromptJournalDatabase`, `prompt_executions` 테이블 |
| 설정 | `DataStore<Preferences>` 파일명 `pj_settings` |
| 알림 채널 | `jupjup_pj_server` |
| Runtime | `PromptJournalRuntime` (object) |
| HTTP 서버 | Ktor CIO 포트 3002, assets `pj_web/` |
| 에러코드 프리픽스 | `E-AND-REPORT-08xx` |

---

## 3. 데이터 모델

### 3.1 Entity

```kotlin
@Entity(tableName = "prompt_executions")
data class PromptExecution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,           // "openrouter" | "nim" | "google_ai_studio"
    val modelId: String,            // e.g. "google/gemini-2.0-flash-exp:free"
    val prompt: String,             // 사용자 프롬프트
    val response: String,           // AI 응답 원문
    val executedAt: Long,           // 실행 시각 (epoch ms)
    val durationMs: Long = 0,       // 실행 소요 시간
    val status: String,             // "SUCCESS" | "FAILED" | "PENDING"
    val errorMessage: String? = null
)
```

### 3.2 DAO

```kotlin
@Dao
interface PromptExecutionDao {
    @Query("SELECT * FROM prompt_executions ORDER BY executedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<PromptExecution>

    @Query("SELECT * FROM prompt_executions WHERE id = :id")
    suspend fun getById(id: Long): PromptExecution?

    @Insert
    suspend fun insert(execution: PromptExecution): Long

    @Query("DELETE FROM prompt_executions WHERE executedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM prompt_executions")
    suspend fun count(): Int
}
```

### 3.3 DataStore Settings

```kotlin
data class PjSettings(
    val port: Int = 3002,
    val autoStart: Boolean = false,
    val provider: String = "openrouter",
    val modelId: String = "",
    val prompt: String = DEFAULT_PROMPT,
    val scheduleType: String = "daily",    // "once" | "daily"
    val scheduleValue: String = "09:00",   // HH:mm
    val enabled: Boolean = true
)
```

---

## 4. AI 클라이언트 구조 (`ai/` 패키지 — promptjournal 내부)

```
ai/
├── AiProvider.kt          // enum { OPENROUTER, NIM, GOOGLE_AI_STUDIO }
├── AiClient.kt            // interface { suspend fun complete(prompt: String): Result<String> }
├── OpenRouterClient.kt    // https://openrouter.ai/api/v1/chat/completions
├── NimClient.kt           // https://integrate.api.nvidia.com/v1/chat/completions
├── GoogleAiStudioClient.kt // https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
└── AiClientFactory.kt     // AiProvider → AiClient 매핑
```

- **인증 토큰**: DataStore에 저장, `AiClient` 생성 시 주입
- **무료 모델 목록**: 초기 하드코드 → 추후 런타임 API 조회로 확장
- **웹서치(그라운딩)**: MVP 미지원. Google AI Studio만 차후 확장 대상

---

## 5. HTTP 라우트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/` | `pj_web/index.html` 서빙 |
| GET | `/api/health` | `{"status":"ok","service":"promptjournal"}` |
| GET | `/api/executions` | 최근 실행 목록 (limit 쿼리) |
| GET | `/api/executions/{id}` | 단건 상세 |
| POST | `/api/execute` | 즉시 실행 (provider/modelId/prompt 오버라이드 가능) |
| GET | `/api/settings` | 현재 설정 조회 |
| POST | `/api/settings` | 설정 변경 |
| DELETE | `/api/executions/{id}` | 실행 기록 삭제 |

---

## 6. 스케줄링

| 타입 | WorkRequest | 비고 |
|------|-------------|------|
| `once` | `OneTimeWorkRequest` | 지정 시각 1회 |
| `daily` | `PeriodicWorkRequest(24h)` | `setInitialDelay`로 다음 실행 시각 맞춤 |

- `PromptJournalWorker`: AI 호출 → `PromptExecutionRepository.save()` → 알림 발송
- 중복 실행 차단: `ExistingWorkPolicy.KEEP` + semaphore 패턴
- `enabled=false` 시 스케줄 전체 취소

---

## 7. 웹 페이지 (`pj_web/`)

- **목록 뷰**: 날짜별 카드 (공급자·모델·상태·실행 시각)
- **상세 뷰**: 프롬프트/응답 전체, 복사 버튼
- **즉시 실행**: `/api/execute` POST 트리거
- **설정 패널**: 공급자/모델/프롬프트/스케줄 수정

---

## 8. 앱 통합 (ServiceRegistry 확장)

```kotlin
// Services.kt 수정
enum class Service { MAC, PLAN, PROMPTJOURNAL }

// ServiceRegistry.fragment() — ServiceTab.SETTINGS 등에 분기 추가
// ReportServiceAdapter 구현체 추가
// DashboardFragment: PROMPTJOURNAL 카드 자동 표시
```

---

## 9. 구현 단계

| 단계 | 작업 | 산출물 | 검증 |
|------|------|--------|------|
| **PJ1** | 모듈 스캐폴드 + `settings.gradle.kts` + `build.gradle.kts` | 컴파일 | `:services:promptjournal:assembleDebug` |
| **PJ2** | DB · DataStore · Runtime · DebugLogger | 초기화 로그 | 단위 테스트 |
| **PJ3** | AI 클라이언트 3종 + 팩토리 + 설정 연동 | Mock 테스트 | Worker 테스트 |
| **PJ4** | HTTP 서버 + 라우트 + 정적 에셋 | `http://IP:3002` 200 | 실기 health |
| **PJ5** | WorkManager 스케줄러 + 워커 | 스케줄 등록 로그 | 실기 알림·DB 저장 |
| **PJ6** | UI 프래그먼트 4종 + ServiceAdapter + Registry | 대시보드 카드 | connected |
| **PJ7** | 웹 페이지 (index.html + JS) | 전체 E2E | 실기 크롤→웹 확인 |
| **PJ8** | 빌드·단위·lint·실기 검증 + 1.10.0 bump | `build_and_run.sh` 통과 | health 200×3, 크래시 0 |
| **PJ9** | 문서 갱신 (DESIGN·CHANGELOG·README·TODO·세션 로그) | — | — |

---

## 10. 주의 사항

- `pj_` 리소스 접두사 필수 (mac_, plan_ 충돌 방지)
- DB 마이그레이션은 up/down 분리, Room 인덱스명 `index_<table>_<col>` 규칙
- AI 토큰은 커밋·로그에 절대 노출 금지
- FGS `foregroundServiceType="dataSync"` — app 매니페스트에서 `tools:node="merge"`로 주입
- Connected 테스트 시 DB 초기화됨 (재수집으로 복구)

> **상태: 서비스 삭제됨 (2026-09).** 아래 내용은 역사 기록. 재사용 조각은 `:services:common` 승격됨.
