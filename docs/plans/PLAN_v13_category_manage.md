# PLAN_v13 — 카테고리 중심 + 2단계 관리 (R27)

> 피드는 카테고리 중심(왼쪽), 관리는 1차 사이트 → 2차 게시판 메뉴로 분리.

## 1. 정보 구조 (사용자 확정)

- **피드**: 왼쪽 = 전체 + 카테고리 10개(건수). 상단 카테고리 탭 삭제(중복).
  왼쪽 하단 접이식 "언론사" 섹션으로 사이트 필터 유지.
- **1차(사이트)**: 클리앙·뽐뿌·… 8개, 관리 진입점. 소스 ≠ 사이트 유지
  (소스 = `클리앙 > 알뜰구매` 같은 게시판 단위 크롤링 단위 — 병합안 폐기).
- **2차(게시판)**: 사이트별 보드 목록 → 선택(on/off) → 카테고리 → 주기(15/30/60/120, 기본 30).

## 2. 서버

- `GET /api/sites`: domain 그룹 `{name, sourceIds, boardCount, postCount, enabled}`.
  사이트명 정적 맵 (`CommunitySites`).
- `GET /api/board-catalog(?domain=)`: V2 기반 추천 게시판 (자동 탐색 불가 — 사이트별 구조 상이).
- `SiteBoard.intervalMinutes` (DB v4, 기본 30) + `MIGRATION_3_4`.
- 스케줄 보드 단위 전환: `crawl_board_{id}` unique, 워커 입력 boardId,
  `triggerBoard` (신규 보드 즉시 1회), `scheduleBoard`/`cancelBoard`,
  소스 토글은 소속 보드 전체 처리. 구 `crawl_{source}` 작업명 잔재는 1회 전환(DataStore flag)으로 정리.
- 로그 표시명 `사이트 › 보드`. 백필은 일일 요약 워커로 이동(전체 25 상한).
- batch 확장: 항목별 `sourceId` + `intervalMinutes` 검증, 적용 후 스케줄 동기화 +
  신규 보드 즉시 수집. `GET /api/boards`에 `intervalMinutes` 포함.

## 3. 웹

- 왼쪽: 카테고리 라디오 목록(건수) + 접이식 언론사 체크 + **사이트 관리** 버튼.
- 1차 모달: 사이트 8개(보드수·글수) → 클릭 → 2차 모달.
- 2차 모달: 타이틀 `{사이트} 게시판 관리`, 행 상태 라벨(수집중/일시멈춤),
  카테고리·주기 select, 삭제, 추천 목록 select(자동 입력)+직접 입력, 적용/적용 후 즉시 수집.

## 4. 검증

- 단위 15종+app 6종, lint 0, `node --check`.
- 실기: sites/catalog API, batch 주기 변경(60→30往復), 보드 단위 수집 로그(`›` 표기),
  중복 수렴 유지(new=0), v4 마이그레이션 무사.
- Android 앱 화면 변경 없음.
