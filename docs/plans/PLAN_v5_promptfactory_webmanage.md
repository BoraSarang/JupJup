# PLAN_v5 — 프롬프트팩토리 다중 프롬프트 재설계

> 플랫폼: Android · v1.11.0 · 2026-09-18
> 부모: PLAN_v4_promptfactory.md (v1.10.0 단일 프롬프트 구조 → 다중 프롬프트 전환)

---

## 1. 배경 & 목표

v1.10.0의 "단일 프롬프트 1개 설정" 구조는 실제 요구사항과 달랐다.
요구사항 확정(사용자 인터뷰):

- **등록 프롬프트 목록** (제목/내용/공급자·모델/스케줄/활성화)을 **여러 개** 관리
- 프롬프트 제목 클릭 → **그 프롬프트의 수집 결과 리스트** 조회
- 웹 메인: 인사이트 + 최근 스케줄 결과
- **관리 주체 = 웹** (공급자·API키·모델·프롬프트 CRUD). **앱은 현황/상태만**, 스케줄러는 열심히 실행만
- 시드: `무료AI모델-일일리포트-프롬프트.md` 1개만 등록 (본문 원문 그대로)
- 직전 성공 결과를 다음 실행에 주입 (usePreviousResult)

---

## 2. 핵심 결정 (확정)

| 항목 | 결정 |
|------|------|
| 관리 주체 | 웹 (앱은 현황/스케줄만) |
| 기존 데이터 | 초기화하고 새 출발 (MB_1_2에서 실행기록 삭제) |
| 시드 프롬프트 | MD 프롬프트 1개만 (`무료 AI 모델 통합 일일 리포트`, OpenRouter · `nvidia/nemotron-3-super-120b-a12b:free`, 매일 09:00, 활성 ON, 이전 결과 주입 ON) |
| 이전 결과 주입 | 직전 SUCCESS 응답 원문을 `[어제까지 기록]` 자리에 삽입 |
| API 키 저장 | DataStore `pf_api_keys`, 공급자별 `api_key_$provider` (웹에서 등록) |

---

## 3. 데이터 모델 변경 (v1 → v2)

| 변경 | 내용 |
|------|------|
| `Prompt` (신규 엔티티) | id/title/content/provider/modelId/scheduleType(_daily_)/scheduleValue/`enabled`/`usePreviousResult`/createdAt/updatedAt |
| `PromptExecution` (변경) | `promptId: Long = 0` 필드 추가 |
| `PfSettings` (축소) | port/autoStart만 (단일 프롬프트 필드 전부 제거) |
| `ProviderKeyStore` (신규) | 공급자별 API 키 DataStore |

### MIGRATION_1_2
- `prompts` 테이블 CREATE
- `prompt_executions`에 `promptId INTEGER NOT NULL DEFAULT 0` 추가
- 기존 실행 기록 `DELETE` (초기화)

---

## 4. 모델 카탈로그 (AIModelTalk 패턴 이식)

`ModelCatalog` object — 공급자별 모델 목록:
- 기본 목록: 각 클라이언트 `supportedModels` (실측 검증된 무료 모델)
- 런타임 갱신: OpenRouter (`:free`만) / NIM / Google `/models` 조회 → 병합
- 병합 규칙: 기존 모델 유지 + 신규 원격 모델 자동 활성, 활성 상태 보존
- **활성 토글**: 프롬프트 등록 시 모델 select 목록 제어

---

## 5. 서버 라우트 v2

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/health` | 서버 상태 |
| GET/POST | `/api/prompts` | 프롬프트 목록/생성 |
| GET/PUT/DELETE | `/api/prompts/{id}` | 상세/수정/삭제 |
| GET | `/api/prompts/{id}/executions` | 해당 프롬프트 결과 리스트 |
| POST | `/api/prompts/{id}/execute` | 해당 프롬프트 즉시 실행 |
| GET | `/api/executions` · `/{id}` · DELETE `/{id}` | 전체 기록/상세/삭제 |
| POST | `/api/execute` | 호환 유지 (promptId 지정 or 활성 1번) |
| GET | `/api/insights` | 프롬프트별 최근 SUCCESS 요약 (500자) |
| GET | `/api/providers` | 공급자별 API키 여부 + 모델 목록 + 활성 |
| POST | `/api/providers/{name}/key` | API 키 등록/교체 |
| POST | `/api/providers/{name}/models/refresh` | 모델 목록 갱신 |
| POST | `/api/providers/{name}/models/{modelId}/enabled` | 모델 활성 토글 |
| GET/POST | `/api/settings` | port/autoStart |

---

## 6. 스케줄러 v2 (프롬프트별 개별 예약)

- WorkRequest 고유명: `pf_prompt_{id}` (`ExistingPeriodicWorkPolicy.REPLACE`)
- `rescheduleAll()`: DB 활성 프롬프트 전체 재예약 (시작 시 / 활성 변경 시)
- `scheduleOne(prompt)`: 등록/수정/토글 시 단건 싱크
- `triggerImmediate(promptId)`: 즉시 실행 (고유명 `pf_immediate_{id}`, KEEP)
- 워커: promptId 조회 → API키 확인 → usePreviousResult면 직전 SUCCESS `[어제까지 기록]` 주입 → AI 호출 → 실행 기록 저장(promptId 포함) → 알림

---

## 7. 웹 v2 (조회+관리)

- **프롬프트 탭**: 인사이트 카드 + 프롬프트 제목 카드 (활성 ON/OFF 배지, 공급자·모델·스케줄)
  - 카드 클릭 → **프롬프트 상세 모달**: 내용 + 결과 리스트 + 지금 실행/정지/편집
- **실행 기록 탭**: 전체 기록 (상세 모달에서 응답 복사/삭제)
- **관리 탭**: 프롬프트 CRUD 폼 + 공급자 카드 (API키 등록·모델 목록·모델 활성 토글·모델 갱신)
- 모바일 사파리 대응 유지 (기존 CSS 상속: 100dvh 모달, safe-area, 16px 입력, 48px 터치타겟, sticky 탭)

---

## 8. 앱 축소

- HomeFragment: 서버/마지막 실행/실행 건수 + **프롬프트 등록·활성 건수** (공급자·모델 → 제거)
- SettingsFragment·ProviderManageFragment: 웹 관리 안내 스텁 유지
- PfServiceAdapter: 활성 프롬프트 기준 표시, `triggerImmediate(first.id)`

---

## 9. 구현 단계 결과

| 단계 | 작업 | 상태 |
|------|------|------|
| PF5-1 | DB v2 + Prompt/DAO + ProviderKeyStore + 저장소 + ModelCatalog | ✅ |
| PF5-2 | 스케줄러·워커 프롬프트id별 + 주입 | ✅ |
| PF5-3 | 라우트 v2 + Runtime 시드 | ✅ |
| PF5-4 | 웹 v2 + 앱 축소 + 어댑터 | ✅ |
| PF5-5 | 빌드·테스트·lint·실기 검증 | ✅ |
| PF5-6 | 문서 갱신 + 1.11.0 bump | 진행 중 |

---

## 10. 실기 검증 결과 (S23, v1.11.0)

- 시드 프롬프트 등록 확인: `무료 AI 모델 통합 일일 리포트`
- 시드 본문 === MD 파일(무료AI모델-일일리포트-프롬프트.md 32~114행) **정확히 일치 (diff equal)**
- API 키 등록(OpenRouter) → 즉시 실행 → **SUCCESS** (id11: 84.8s / id12: 56.8s)
- **이전 결과 주입 확인**: id12의 prompt에 직전 SUCCESS 원문이 `[어제까지 기록]` 자리에 삽입됨 (injected: True)
- `/api/prompts`, `/api/insights`(인사이트 1건), `/api/providers`(키·모델·활성토글) 응답 정상
- 웹 UI agent-browser 검증: 데스크톱+모바일(390×844) PASS (insight/카드/기록/관리 탭·콘솔 에러 0)
- 터치 타겟 수정: `.modal-close` 44→48px / 탭 전환 시 모달 자동 닫힘 추가 → 재검증 PASS
- 빌드 `:app:assembleDebug` SUCCESS · 단위 테스트 SUCCESS · lint SUCCESS

---

## 11. 주의 사항

- `pf_` 리소스 접두사 유지
- API 키는 로그/커밋 노출 금지 (DataStore 분리, 웹 UI 마스킹)
- DB 마이그레이션 up/down 분리 (Room fallbackToDestructive 금지 기본)
- 모델 카탈로그는 메모리 기반 → 앱 재시작 시 기본 목록으로 복원, 실시간 갱신은 웹에서 실행