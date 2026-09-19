# CHANGELOG — JupJup

## [Unreleased]
> 플랫폼: AND · 모델 관리 수정 + Zen 추가 + NIM 삭제 (R23, PLAN_v10)
- **불일치 수정**: 편집폼에 저장값과 다른 모델이 표시되던 문제 (카탈로그에 없던 nemotron이 첫 항목으로 둔갑).
  저장 ID가 목록에 없으면 `(목록에 없음)` placeholder로 그대로 표시, 자동 치환 금지
- **카탈로그 가드**: 정적 base 분리 보관·삭제 금지, 프롬프트 참조 ID 삭제 금지, 미등록 투입 ID 유지
- **OpenCode Zen 공급자**: `chat/completions` 클라이언트 + 무료 8종 정적 + `-free`만 동기화
- **NIM 삭제**: 코드·웹·테스트에서 제거 (사용 프롬프트 0건, 실행기록 표기는 유지)
- 검증: ModelCatalogTest 7/7, 실기 providers OR 6·ZEN 8·NIM 소멸, 프롬프트 값 유지

> 플랫폼: AND · 프롬프트팩토리 → 프롬프트 저널 개명 (R22)
- **표시 이름**: `프롬프트팩토리` → `프롬프트 저널` 전수 (화면·웹·문서·로그), 영문 `Prompt Factory` → `Prompt Journal`
- **코드 식별자**: `:services:promptjournal` 모듈, `com.borasarang.promptjournaljupjup` 패키지,
  `PromptJournal{Runtime,Database,Scheduler,Worker}` · `Pj{Routes,AssetRoutes,ServiceAdapter,Settings}` ·
  리소스 `pf_*` → `pj_*` (레이아웃·문자열·색상·ID) · `PROMPTJOURNAL` enum · `pj_web` 에셋 · 알림 채널 `jupjup_pj_server`
- **데이터 승계** (1회 마이그레이션): `promptfactory.db(+journal/shm/wal)` → `promptjournal.db`,
  DataStore `pf_{settings,api_keys,models}` → `pj_*`, 구 Work(`promptfactory_prompt` 태그·`pf_prompt_{id}`) 취소 후 재예약
- 실기 검증: 빌드·단위·lint 통과, 3002 health `promptjournal` 응답, 프롬프트 2건·실행기록·설정 승계 확인

> 플랫폼: MAC · 맥줍줍 웹 포털 리디자인 (PLAN_v9, Apple 네이티브 톤)
- **사이드바 네비게이션**: 타임라인/Watchlist/통계 분리, 필터를 `출처`(GitHub/MAS/Homebrew 카운트)·`유형`(전체/오픈소스/프리/유료) 그룹화
- **카드 시스템**: 48px 아이콘 + 제목 + 설명 2줄 클램프, 언어 컬러닷(Swift/파이썬/JS/TS/Rust/Go/C++), NEW 도트, ★별점 간결화
- **그리드**: 3열 반응형(1100px↓ 2열, 720px↓ 1열), rounded-2xl, 소프트 섀도우, hover 떠오름
- **모달 재구성**: 64px 히어로 아이콘 + 메타 pill + CTA(홈페이지/GitHub Repo) 상단 배치, 탭(소개/특징/새기능) 구조
- **톤**: 배경 #f5f5f7, Pretendard 폰트, 다크모드 지원, macOS 메뉴바 앱답게 둥글고 가볍게
- **모바일**: ≤720px에서 사이드바 오버레이 드로어 전환, 터치 타겟 44px 이상
- **핫픽스**: 모달 포커스 링(닫기 버튼으로 초기 포커스) · 통계 막대 미표시(클래스 불일치) · 알림센터 깨진 아이콘(숨김 처리) ·
  소요시간 표기(`9시간 9분` + `YYYY-MM-DD HH:mm`) · 모달 상단 고정(6vh) · 카드 언어닷 세로 중앙 ·
  사이드바 배지 전체 카운트 고정 + 카테고리 타임라인 하위 이전(타임라인 선택 시만 표시) ·
  좁은 화면 잘림(그리드 `minmax(0,1fr)` + 카드/탑바 오버플로) · 다크모드 모달 글자색(dialog UA override) ·
  버전 테이블 셀 마크다운 렌더링

> 플랫폼: AND · 프롬프트 저널 뉴스룸 리디자인 (PLAN_v7, 외부 키트 적용)
- **Masthead**: `PROMPT JOURNAL / 프롬프트 저널` serif + 오늘 날짜 + 제N호 박스, Tailwind CDN + Noto Serif KR
- **발행 아카이브**: 왼쪽 리스트에 제N호·조간/석간/단신 + 헤드라인 요약, BREAKING/휴간 배지
- **기사 지면**: 응답 첫 줄 H1 헤드라인 + kicker + `제N호 발행 · 날짜 조간 · 취재 N초` 메타, factbox 얇은 border
- **용어 전환**: 최신호 발행/기사 복사/이 호 폐기/취재 지침서/취재원 관리·동기화·투입
- **설정 서랍 2탭**: 브리핑 채널(폼) / 취재원 관리(공급자·키·모델)
- **다듬기 (R17)**: 시드 프롬프트에 헤드라인 지침(첫 줄 35자 이내 평문), BREAKING은 신규 모델 있을 때만(없으면 휴간),
  지면 메타에서 SUCCESS/모델명 제거(모델명은 툴팁), 아카이브 요약 2줄 클램프
- **채널 목록**: 설정 Tab1에 채널 카드 (발행주기·편집/폐간, `DELETE /api/prompts/{id}` 연동)
- **v2 다듬기 (R18)**: H1 35자 추출(레거시 긴 첫줄 대응, 단어 경계+…) + kicker에 일일 브리핑 날짜,
  아카이브 요약=인사이트 1줄+페이드, 서비스 표→카드형(모델 배지+ID 복사+비고+↗출처),
  요약 표 paper(#fafaf7) 배경, 휴간 회색점, 인사이트 lead serif 17px/1.7, keep-all, masthead 간격 축소
- **역슬래시 (R19)**: LLM이 뱉는 `\모델ID\`를 코드 칩으로 (본문·아카이브 요약·서비스 카드+ID 복사),
  빈 응답·라벨뿐인 첫줄의 H1은 `신규 무료 모델 없음`으로
- **모델 투입 상태 영속화 (R20)**: `ModelEnabledStore` (DataStore `pj_models`) — 해제/투입이 재시작 후에도 유지.
  `merge` 수정으로 갱신 시 명시 해제가 부활하지 않음. `ModelCatalogTest` 4종.
- 백엔드·API·DB 변경 없음 (pj_web 3파일 + 시드 프롬프트 문구만)

> 플랫폼: AND · 프롬프트 저널 웹 v3 리포트 중심 개편 (PLAN_v6)

### 변경
- **웹 v3**: 상태카드·새로고침·3탭 삭제 → 슬림 헤더(로고+상태점+마지막실행+⚙️) + 프롬프트 칩 + 2열 마스터-디테일(PC 왼쪽 날짜·오른쪽 본문, 모바일 전체화면)
- **모달 2층 폐지**: 기록보기 불가(상세 모달이 프롬프트 모달 뒤에 깔림) 근본 해결 — 실행 항목 인라인 렌더
- **마크다운**: mac_web T-142 그대로 이식 + GFM 표 패치, `<br>`·`<URL>` 보존, `*라벨:*` 굵게, alert→toast
- **설정 서랍**: ⚙️ 우측 서랍에 프롬프트 폼 + 공급자·모델(검색·모두사용/해제·갱신 `added` 수정)
- **시드 프롬프트 출력 형식**: 표는 변경 요약에만, 서비스별 목록은 `###`+불릿 1모델1줄
- 본문 최대폭 1200→1600px, 헤더·칩을 본문 폭에 정렬

## [1.11.0] - 2026-09-18
> 플랫폼: AND · 프롬프트 저널 다중 프롬프트 재설계

### 신규
- **프롬프트 CRUD**: `Prompt` 엔티티 + DB v2 마이그레이션 (MIGRATION_1_2, 기존 실행기록 초기화)
  - 제목·본문·공급자·모델·스케줄(daily/once·시각)·활성화·이전결과주입
- **다중 프롬프트 관리**: 프롬프트별 스케줄 개별 예약 (`pj_prompt_{id}`), 실행 결과 리스트
- **이전 결과 주입**: `usePreviousResult` ON이면 직전 SUCCESS 응답 원문을 `[어제까지 기록]`에 삽입
- **공급자별 API 키 관리**: `ProviderKeyStore` (DataStore `pj_api_keys`) — 웹에서 등록/교체
- **모델 카탈로그**: `ModelCatalog` (AIModelTalk 패턴 이식) — 런타임 목록 갱신 + 모델 활성 토글
- **웹 v2 (조회+관리)**: 프롬프트 탭(인사이트+카드→상세 결과), 실행 기록, 관리 탭(프롬프트 폼+공급자·키·모델)
- **인사이트 API**: `/api/insights` 프롬프트별 최근 SUCCESS 500자 요약
- **시드 프롬프트**: MD(`무료AI모델-일일리포트-프롬프트.md`) 원문 1개 자동 등록
- 앱 홈: 프롬프트 등록·활성 건수 표시 (설정·공급자 관리 스텁화)

### 구조
- 라우트 v2: `prompts` CRUD / `prompts/{id}/executions` / `execute(promptId)` / `providers`(key·models·refresh·enabled)
- 스케줄러·워커: 프롬프트id 기반 재설계
- `PjSettings` 축소(port/autoStart만) — 단일 프롬프트 필드 제거
- `PjServiceAdapter`: 활성 프롬프트 수 표시, `triggerImmediate(first.id)`

### 개선 (S23 실기 발견)
- 모달 닫기 × 터치 타겟 44→48px
- 탭 전환 시 프롬프트/상세 모달 자동 닫힘

### 테스트
- 빌드 `:app:assembleDebug` SUCCESS · 단위 테스트 SUCCESS · lint **BUILD SUCCESSFUL**
- 실기 검증 (10.207.33.235:5555, 1.11.0): 시드 1건 자동 등록(**MD 원문과 diff 일치**),
  API키 등록→즉시 실행 **SUCCESS ×2** (id11 84.8s / id12 56.8s),
  **이전 결과 주입 확인**(id12 prompt에 직전 SUCCESS 삽입), `/api/insights` 정상,
  웹 UI 데스크톱+모바일(390×844) agent-browser 검증 PASS (콘솔 에러 0)
- versionName 1.11.0 (versionCode 13)

## [1.10.0] - 2026-09-18
> 플랫폼: AND · 프롬프트 저널 신규 서비스

### 신규
- **`:services:promptjournal` 모듈** 추가 (네임스페이스 `com.borasarang.promptjournaljupjup`, 포트 3002)
- **AI 클라이언트 3종**: OpenRouter, NVIDIA NIM, Google AI Studio — 각각 OkHttp + kotlinx.serialization 기반
- **Room DB**: `PromptExecution` 엔티티 + `PromptExecutionDao` (실행 기록 저장/조회/삭제)
- **DataStore 설정**: 공급자/모델/프롬프트/스케줄/API키/활성화 여부 영속 저장
- **WorkManager 스케줄러**: 매일/1회 실행 스케줄 + 즉시 실행 트리거
- **Ktor HTTP 서버** (포트 3002): `/api/health`, `/api/executions`, `/api/execute`, `/api/settings`, `/api/models`
- **웹 포털** (`pj_web/`): 실행 기록 목록/상세, 설정 변경, 모델 목록, 즉시 실행
- **UI 4종 프래그먼트**: 홈/공급자관리/설정/알림
- **ServiceAdapter + ServiceRegistry** 연동: 대시보드 3열 카드 표시
- **MainActivity 세그먼트** 3열 확장 (맥줍줍/요금줍줍/프롬프트 저널)
- 기본 프롬프트: 무료 AI 모델 트래킹 리포트 (참고 파일 내장)
- `PromptJournalRuntime` object: DB/DataStore/스케줄러 초기화

### 구조
- app build.gradle.kts: `:services:promptjournal` 의존성 추가
- JupJupApplication: `PromptJournalRuntime.initialize()` 호출
- Services.kt: `Service.PROMPTJOURNAL` enum + fragment 분기
- strings.xml: `nav_pj`, `dashboard_pj_title` 추가

### 테스트
- 빌드: `:app:assembleDebug` + `:services:promptjournal:assembleDebug` **BUILD SUCCESSFUL**
- lint: `:app:lintDebug` **BUILD SUCCESSFUL** (오류 0)
- 단위 테스트: `:services:promptjournal:testDebugUnitTest` **BUILD SUCCESSFUL**
- 실기 검증 (S22, 1.10.0): pj 대시보드 카드 표시·서버 3002 기동·health 200·**AI 실행 SUCCESS 3건** (nemotron-3-super-120b 35.9s 등)·autoStart 자동 기동 확인
- **실기 중 버그 수정**: `PjServiceAdapter.setServerRunning` 토글 역전 (running=true 시 start 호출) → Mac/Plan과 동일하게 stop으로 수정, 대시보드 pj 카드 추가

## [1.9.0] - 2026-09-14
> 플랫폼: AND · 잔여 정리 (리팩토링 7단계)

### 정리
- 에러코드 조회/저장 분리: `E-AND-DB-0404` 신규 ("데이터를 불러오지 못했습니다").
  조회 9곳(설정·홈·알림×2, 대시보드, 앱정보 포트, mac 소스 목록) 전환, 변경 16곳은 `0402` 유지
- escape/envelope 통일: plan 로컬 절단 `escapeJson` 삭제 → 공용 무손실로,
  plan `statsRoute` 에러를 `respondError`로 (바이트 동일 출력)
- mac stats 캐시: `StatsCache`를 `:services:common`으로 승격 (`common.cache`),
  mac `overview·trends·collect·insightsInput` 캐시 + `saveApps·purgeSource·cleanup` 무효화
- 알림·요약 본문 리소스화: 양 `NotificationService`에 Context 주입 + `getString`/`plurals`,
  `DailySummaryWorker` 2종, `describeStats` 2종(Context 인자). DB 저장 문구와 동일 출력
- 시드 영속화: mac `lastSeedStatus/StartedAt`을 DataStore에 저장·복원.
  재시작 시 `running` 유령 상태는 `idle`로 정정
- build-logic: `jupjup.base` convention plugin (SDK·컴파일·패키징·단위테스트) + 4 모듈 적용.
  AGP 9 `CommonExtension` 프로퍼티식, 버전은 카탈로그가 진실. `jupjup.room`은 분리 유지

### 테스트
- 검증: 실기(R5CT215F4QK) 단위 131/131, lint 오류 0,
  설치 후 health 200×2·mac stats 4종 200·시드 상태 영속 확인·크래시 0

## [1.8.0] - 2026-09-14
> 플랫폼: AND · 빌드·문자열·잔정리 (리팩토링 6단계)

### 정리
- SDK 단일 진실: `compileSdk/minSdk`를 카탈로그로 (`libs.versions.toml`, 4 모듈)
- 권한 일원화: 9종을 `:app` 소유로 (mac/plan 삭제, 머지 확인).
  `SystemForegroundService`는 라이브러리 단독 lint 때문에 3곳 유지
- 빈 catch 12곳 로그화 (연속실패 기록·실패푸시·DB close·CookieManager·알림 액션 6종).
  의도적 무시(종료·채널·폴백·재귀방지)는 제외
- `SourceLocks.release` 유휴 락 제거, 시드 취소 시 `idle` 복구 + 시작시각 기록
- Toast·채널·푸시제목 17종 리소스화 (`mac_/plan_` 체계. 포맷·DB값·파서용 제외)
- 문서 유령 코드 2종 정리 (v1.0.0 항목의 미등록 코드 표기 제거)

### 테스트
- 검증: 실기(R5CT215F4QK) 단위 131/131, lint 오류 0, 설치 후 health 200×2, 크래시 0
- 후속 (범위 밖): build-logic convention plugin, `E-AND-DB-0402` 조회/저장 분리,
  요약·알림 본문 리소스화, 시드 상태 영속화, escape/envelope 통일

## [1.7.0] - 2026-09-14
> 플랫폼: AND · 성능·DB (리팩토링 5단계)

### 성능
- N+1 제거: plan `getPlans` 1+N → 2쿼리 (`getByPlanIds` 배치 + 메모리 조인),
  `saveCrawlResults` 배치 (`getByIds`), 소스관리 양쪽 배치 (`getRecentBySourceIds`),
  `getCollectionHealth` 배치 + `MAX(collectedAt)` 재사용
- plan 인덱스 2종 (`isNew+firstCollectedAt`, `networkType+mvnoNetwork+price`) + 마이그레이션 4→5
- `logResult` 원자화 (양쪽 insert+상태갱신 `withTransaction`)
- plan `serveAsset` IO 격리 + 로그, `getLocalIp` Main 호출 제거 (About·홈·FGS 알림 캐시),
  `isServiceRunning` 공용 타임아웃 헬퍼로 교체

### 테스트
- 신규: `PlanBatchTest` 2종 (배치 1회·빈페이지 미조회)
- 검증: 실기(R5CT215F4QK) 단위 131/131, lint 오류 0, 마이그레이션 승계 확인 (381건 유지·인덱스 생성),
  수집 E2E 1회, health 200×2, 크래시 0
- 참고: 첫 마이그레이션 시도에서 Room 인덱스명 불일치 크래시 → `index_plans_*` 규칙명으로 수정 후 정상

## [1.6.0] - 2026-09-14
> 플랫폼: AND · God object 분리 (리팩토링 4단계)

### 구조
- 양 `HttpServerService` (895·873줄) → 도메인별 Route 확장 분리 (에셋·아이템·수집·통계·알림·설정 + JSON 매퍼).
  본체는 생명주기+배선만. `app()`·`restartServer()`·`scope`·`currentPort`만 internal 공개, 동작 동결
- plan `StatsRepository` (546줄) → `StatsCache`(TTL 5분·시간주입 테스트 가능) + 집계 + `Models.kt`(모델 이동)

### 수정
- plan 토글 미존재 200+enabled:false → 404 (mac 동일). `toggle(id): Boolean?` 신규, 기존 `toggleEnabled` 유지
- plan sync 미존재 sourceId 무조건 202 → 404 (`respondNotFound`, mac 동일). 포털은 `{}` 전송이라 영향 없음
- plan 알림 상세 깨진 `detailJson` 500 승격 → `{}` 폴백 (mac 동일)
- plan 설정 포트 변경 시 라우트 스레드 직접 `restartServer()` (최대 3s 블로킹) → `scope.launch` (mac 동일)
- 미캐시 4종(`getCrawlTrend, getNewPlanTrend, getValueRanking, getCollectionHealth`) 캐시 적용 (파라미터 키)

### 테스트
- 신규: `StatsCacheTest` 6종 (TTL·히트·무효화 + ranking/health 리포지토리 히트) + `SourceToggleTest` 2종 +
  4a `Mac/PlanServerJsonTest` 9종
- 검증: 실기(R5CT215F4QK) 단위 129/129, lint 오류 0, 설치 후 health 200×2, 토글·sync 404 확인,
  stats 9종 200, 수집 중 무효화 정상 동작 확인, 크래시 0

## [1.5.0] - 2026-09-14
> 플랫폼: AND · app 추상화 + ViewModel 테스트 (리팩토링 3단계)

### 구조
- `ServiceAdapter` 신규 (Mac/Plan 구현체): 대시보드 상태 조회·수집 트리거·중지/재개·서버 토글의 서비스 분기 흡수
- `ServiceRegistry` 신규 (`Services.kt`): 어댑터 맵 + fragment 팩토리 — MainActivity 이중 when 제거
- `DashboardViewModel`: 어댑터 맵 주입(기본값=Registry) + `ioDispatcher` 주입(기본값=IO) — 8개 복사 메서드 → svc 키 4종으로 축소
- 공통 `NetUtils.isPortOpen(port, timeoutMs=500)`: 무타임아웃 소켓 hang 제거

### 테스트
- `DashboardViewModelTest` 6종 신규 (JVM, Robolectric 불필요): 병합·2초 재조회·재귀 없음·서버/수집 위임
- 검증: 실기(R5CT215F4QK) 단위 app 6/6 + mac·plan 106/106, lint 오류 0, 설치 후 health 3000·3001 HTTP 200, 크래시 0

## [1.4.0] - 2026-09-14
> 플랫폼: AND · :services:common 추출 (리팩토링 2단계)

### 구조
- `:services:common` 신규 (namespace `com.borasarang.common`, 리소스 접두 `jup_`)
- `JupLog` 코어: logcat(디버그만) + 파일 로그(항상, `files/logs/<태그>.log`, 512KB 로테이션) — 릴리스 로그 소실 해결. 양 `DebugLogger`는 시그니처 동일 파사드로 위임
- `NetUtils`·`SourceLocks`·DataStore 팩토리(`SettingsStores`, 파일명 승계) 이관, 양 모듈 삭제
- 서버 JSON 헬퍼 단일화 (`escapeJson` 무손실·`receiveJsonObject`·`respondError/NotFound`·`putIfNotNull`·`pathId/Long`) — mac privates 삭제, plan 20곳 치환 (동작 동일 지점만)
- 제외 (후속 단계): `TimeUtils` (주기 옵션 상이), `BootReceiver` (3단계), Entity (5단계), plan toggle/sync 의미·stats envelope (4단계)

### 수정
- 공통 모듈 lint 권한 오류 수정 (매니페스트에 INTERNET·NETWORK_STATE·WIFI_STATE 선언)
- 검증: 실기(R5CT215F4QK) 단위·lint 통과, 설치 후 health 3000·3001 HTTP 200, 파일 로그 2종 생성, 설정 승계 확인, API 응답 동등성(400·404·sources·settings) 확인, 크래시 0

## [1.3.2] - 2026-09-14
> 플랫폼: AND · P0 안전 수정 (리팩토링 1단계)

### 수정
- plan DB 폴백 데드코드 수정 (`resetInstance` 후 재생성) + 재생성 전 원본 백업 (mac과 동일)
- 설정 범위 검증: 보관기간 1~365일 상수 신설, 양 서비스 settings POST에서 포트·보관기간·watchdog 범위 벗어나면 400 (`E-AND-VALID-0501/0502`), `saveSettings`에도 coerce 이중 방어
- plan 수동수집 워커 폭증 차단: unique KEEP + 20초 stagger + `SourceLocks` 상호배제 이식 (mac 패턴)
- `build_and_run.sh`: `pipefail` 추가 + 빌드 전 기존 APK 제거 (stale 설치 방지) + 단위에 `:app` 포함
- 검증: 실기(R5CT215F4QK) 단위·lint 통과, 설치 후 health 3000·3001 HTTP 200, 설정 오입력 400 4종 확인, `/api/sync` 3연타 중복 적재 없음, 크래시 0

## [1.3.1] - 2026-09-14
> 플랫폼: AND · 대시보드 서버 상태 표시 레이스 수정

### 수정
- 대시보드 새로고침이 서버 포트 바인드 전에 소켓 체크해서 "중지됨"으로 뜨던 문제 수정 — 중지 표시 시 2초 뒤 1회 자동 재조회 (`DashboardViewModel.refresh(retry)`, 재귀 없음)
- 서버 시작/중지 토글 직후 상태도 추적 재조회가 자동 보정, 재조회 진입 로그(`서버 미기동 감지 — 2000ms 뒤 재조회`) 추가
- 검증: 실기(R5CT215F4QK) 콜드스타트에서 재조회 로그 + 최종 실행중 표시 확인, 포털 3000·3001 `/api/health` 모두 HTTP 200, 크래시 0

## [1.3.0] - 2026-09-13
> 플랫폼: AND · 서비스-우선 내비게이션 개편

### UI/UX
- 상단 **서비스 전환(맥줍줍/요금줍줍)** 상시 표시 — 전환 시 대시보드로 강제 이동, 현재 서비스가 항상 명시됨
- 하단 5탭: 대시보드(통합) / 홈(서비스 상세) / 수집 소스 / 알림 / 설정
- 드로어 제거 — 앱 정보(포트·버전)는 툴바 `⋮` 메뉴로 이동
- **인사이트 → 대시보드** 명칭 변경 + 활성 서비스 카드 강조 (스트로크 + "현재" 배지)
- 앱바 타이틀 = 항상 현재 서비스명
- **앱 정보 다이얼로그 개편**: 앱 아이콘·버전 배지, 서비스별 포털 주소(IP:포트), 하단 GitHub 링크(`github.com/BoraSarang/JupJup`, 브라우저/에러 처리 포함)

### 수정
- material 1.12.0에 SegmentedButton 미포함 확인 → `MaterialButtonToggleGroup` 세그먼트형으로 구현 (버전 고정 규칙 유지)
- 깨진 connected 테스트 이관 시 잔재 정리 (드로어 메뉴·헤더·아이콘 5종 삭제)
- 검증: 실기(R5CT215F4QK) assembleDebug·단위 테스트·lint 통과, `:app:connectedDebugAndroidTest` 4/4 + `:services:mac` 6/6 통과, 포털 3000·3001 `/api/health` 모두 HTTP 200, 크래시 0

## [1.2.0] - 2026-09-13
> 플랫폼: AND · 시리즈 인사이트 시작 화면

### UI/UX
- 시작 화면 **인사이트** 신규: 맥줍줍·요금줍줍 두 서비스 카드를 한 화면에 병렬 표시 (상태 도트·접속 주소·통계 3개)
- 카드에서 직접 **[지금 수집]·[수집 중지/재개]·[서버 시작/중지]** 조작 (서비스 화면 진입 없이 제어)
- 하단 첫 탭 `서버 상태` → `인사이트`로 교체 (`ic_tab_insight` 신규), 드로어에 `줍줍 시리즈` 항목 추가 (`ic_nav_series` 신규)
- 앱바 타이틀: 인사이트면 시리즈명, 그 외 탭은 선택된 서비스명

### 수정
- 인사이트 빌드 오류 수정: `TimeUtils.formatRelative` 참조 정정, M3 Filled 버튼 스타일명 정정 (`Widget.Material3.Button`), 라이브러리 색상 non-transitive R 참조, `refreshData()` 추가
- `InsightViewModel` 예외 처리: silent catch 제거 → 서비스별 `DebugLogger` + 에러코드 (`E-AND-CRAWL-0201/0211/0221`, `E-AND-DB-0402`) 매핑, 화면 진입 로그(`인사이트 화면 진입`) 추가
- 오타 수정: `줄줍` → `줍줍` (README 제목 포함 5곳)
- 검증: 실기(R5CT215F4QK) assembleDebug·단위 테스트·`:app:lintDebug` 통과 (경고 51건, 오류 0 — 인사이트 관련 6건은 기존 패턴과 동일한 경미 경고), 설치 후 크래시 없음
- 검증(connected·E2E): `:app:connectedDebugAndroidTest` 2/2 + `:services:mac` 6/6 통과, 포털 3000·3001 `/api/health` 모두 HTTP 200, 크래시 0, 인사이트 진입 로그 확인

## [1.0.0] - 2026-09-13
> 플랫폼: AND · 통합 (mac+plan)

### 통합 (처음이벤트)
- 두 독립 앱(MacJupJup·PlanJupJup)을 단일 APK `JupJup`으로 통합
- Gradle 멀티모듈: `:app` + `:services:mac` + `:services:plan`
- 서비스별 격리: 포트(mac 3000 / plan 3001)·DB·설정(DataStore)·알림채널·리소스(mac_/plan_ 접두사)
- `Application` → `{Prefix}JupJupRuntime`(object) 전환, 통합 `JupJupApplication` 하나로 두 서비스 기동
- 하단 서비스 네비(맥줍줍/요금줍줍) + 기능 탭(홈/수집 소스/알림/설정) 통합 UI
- 루트 `build_and_run.sh` 단일 빌드 디스패처
- GitHub Pages·landing 제거 → README 한/영 + docs/ 만

### 마이그레이션
- 데이터 마이그레이션 없음 (신규 설치 기준). 기존 앱 데이터 이관 미지원.
- 메모리/성능 영향: 두 서버 동시 기동으로 약 2배 서버 프로세스(각자 CIO 엔진), DB 2개

### 수정 (1.0.1)
- **크래시 #1**: 두 서비스의 DataStore 파일명이 동일(`settings`) → `mac_settings`/`plan_settings`로 격리
- **크래시 #2**: WorkManager `SystemForegroundService`에 `foregroundServiceType="dataSync"` 누락 → app manifest에서 `tools:node="merge"`로 주입
- 검증: 실기(LB-R5CT215F4QK) 포털 3000·3001 모두 HTTP 200, 크래시 없음

## [1.1.0] - 2026-09-13
> 플랫폼: AND · 통합 UI 개편

### UI/UX
- 상단 기능 탭(TabLayout) 제거 → 하단 바 좌측 **햄버거** + 기능 **드로어**(홈/수집 소스/알림/설정)로 대체
- 하단 바: 서비스 전환(맥줍줍/요금줍줍)은 유지, Material Toolbar 스타일로 정돈
- 상단 앱바 추가: 현재 서비스명 표시 (기존 홈 내부 로고/타이틀 중복 제거)

### 수정 (1.1.1)
- 탐색 구조 재개편: 상단 앱바 햄버거 = **서비스 드로어**(맥줍줍/요금줍줍), 하단 = **기능 탭**(서버 상태·수집 소스·알림·설정) — 하단 우측 서비스 메뉴 제거
- "홈" 탭 → "서버 상태"로 이름 변경 (화면 유지), 서버 상태 탭 전용 아이콘(`ic_tab_server`) 신규
- 각 서비스 홈에 **수집 중지/시작 토글**(일시정지): 중지 시 실행·예약 수집 전체 취소 + `crawl_enabled` 영속 저장(앱 재시작 후 유지), 재개 시 주기 스케줄 재예약
- 각 웹 포털에 파비콘(`favicon.svg`, 원본 앱 아이콘 벡터 기반) + `HttpServerService` 라우트 (`ContentType.Image.SVG`, `/favicon.svg`)
- 파비콘 서비스별 분리 시안(원본 요소 활용): 맥줍줍=파란 그라디언트+코인 링 중심(수집), 요금줍줍=차콜 슬레이트+막대 3/기준선 중심(요금 비교)+상단 코인 링
- `error_message_ko.json`: `E-AND-CRAWL-0221` (수집 중지/재개 상태 저장 실패) 추가
- **서버 상태 표시 버그 수정**: 홈(서버 상태) refresh가 기동 완료 전 스냅샷(false)에 고정 → 서버 기동 대기 폴링(최대 5회·1초 간격, `isServiceRunning`)으로 시작 직후 상태 정확 반영
- 검증: 실기(R5CT215F4QK) assembleDebug·단위 테스트·lint 통과, 크래시 0
- 런처 아이콘 교체: 제공 아이콘 팩 적용 (블루 런처 + 적응형 흰 배경/투명 정면)
- 콘텐츠 정돈: 서비스 홈 카드 구성 유지하되 앱바·하단바로 영역을 구획, 16dp 패딩 유지