# PLAN v2.0 — 서비스-우선 내비게이션 개편 (JupJup)

> 날짜: 2026-09-13 · 플랫폼: Android · 상태: 완료
> 전제: v1.2.0 인사이트 완료 커밋 (`dfae5be`) 위에서 진행

## 1. 개요 (Why)

인사이트 탭에서 드로어로 서비스를 바꿔도 화면 변화가 없어 "변경됐는지 모르겠다"는 UX 결함.
원인: 드로어가 탭 이동(줍줍 시리즈)과 컨텍스트 변경(맥/요금)을 겸하고, 활성 서비스 표시가 없음.

## 2. 목표 (What)

- [ ] 상단 **SegmentedButton**으로 서비스 선택을 최상위·항시 명시
- [ ] 하단 5탭: 대시보드(통합) / 홈(서비스 상세) / 수집 소스 / 알림 / 설정
- [ ] 드로어에서 서비스 전환 제거 → 완전 제거, 설정 진입은 툴바 `⋮` 오버플로우로 이동
- [ ] 세그먼트 변경 시 대시보드로 강제 이동 (컨텍스트 모호성 제거)
- [ ] 수집/서버 액션은 대시보드+홈 양쪽 유지 (역할 분리: 대시보드=빠른 제어, 홈=상세+포털/복사)
- [ ] 인사이트 → 대시보드로 명칭 변경 (역할 명확화)

## 3. 구조 (How)

```
┌─────────────────────────────────────────────┐
│ 줍줍          [맥줍줍 ● 요금줍줍]              │  ← MaterialToolbar + SegmentedButton
│                                  [⋮]        │  ← 오버플로우 (설정 진입은 하단 탭 유지, 정보/버전)
├─────────────────────────────────────────────┤
│   FragmentContainer (서비스별 캐시)          │
├─────────────────────────────────────────────┤
│ [대시보드] [홈] [소스] [알림] [설정]         │  ← BottomNavigationView 5탭
└─────────────────────────────────────────────┘
```

### 3.1 상태 머신

- `currentService: MAC | PLAN` (기본 MAC), `currentTab: DASHBOARD | HOME | SOURCE | NOTIF | SETTINGS` (기본 DASHBOARD)
- 세그먼트 변경 → `currentTab = DASHBOARD`로 강제 이동 후 refresh
- 하단 탭 변경 → 서비스 유지, 해당 서비스 fragment 교체
- 대시보드는 서비스 무관 tag 고정 (`DASHBOARD`), 나머지는 `${service}_${tab}`
- 회전 복원: `onSaveInstanceState`에 service+tab 저장 (기존 패턴 유지)

### 3.2 격리·리소스 규칙 (AGENTS.local 유지)

- app 공용 리소스는 접두사 없음. `dashboard_*` 명명 사용 (insight_*에서 리네임)
- 서비스 모듈 리소스 `mac_`/`plan_` 접두사 유지, non-transitive R이므로 라이브러리 색상은 FQN 참조
- 에러코드: 기존 `E-AND-*` 재사용, 신규 카테고리 없음

## 4. 작업 분해

| 단계 | 항목 |
|---|---|
| N1 | 내비게이션 재구성: activity_main (Drawer 제거→AppBar+세그먼트), MainActivity 상태머신, bottom 5탭, toolbar 오버플로우 메뉴 |
| N2 | 대시보드/홈 정리: insight→dashboard 리네임(레이아웃·Fragment·VM·문자열·아이콘), 활성 카드 강조, 홈 액션 유지 |
| N3 | 폴리시+테스트: M3 스타일, 상태 복원, 단위/connected 갱신, 실기 검증 |
| N4 | 문서+DoD+커밋 |

## 5. 검증 기준

- `./build_and_run.sh build` 성공 + 실기 설치
- 단위 테스트 + lint 통과, connected (app 스모크 갱신)
- 실기(S22): 세그먼트↔탭 연동, 포털 3000/3001 HTTP 200, 크래시 0
- DebugPanel: ERROR 0, `[INFO] [대시보드]` 진입 로그 확인

## 6. 구현 메모 (2026-09-13 완료)

- **SegmentedButton 미사용**: material 1.12.0 AAR에 `segmentedbutton` 패키지 없음 (1.13+). 버전 고정 규칙 유지 → `MaterialButtonToggleGroup` + OutlinedButton 2개로 세그먼트형 구현. API 동일 (`addOnButtonCheckedListener`/`check`/`checkedButtonId`)이라 MainActivity 로직 변경 없음
- 리네임: `ui.insight` → `ui.dashboard`, `insight_*` → `dashboard_*`, `tab_insight` → `tab_dashboard`
- 삭제: `drawer_menu.xml`, `drawer_header.xml`, `ic_menu.xml`, `ic_nav_mac.xml`, `ic_nav_plan.xml`, `ic_nav_series.xml` (도트는 `dot_status.xml` oval로 교체)
- 신규: `toolbar_menu.xml` (⋮ 앱 정보), `dot_status.xml`, `badge_pill.xml`, `tab_home` ("홈", 아이콘은 기존 `ic_tab_server` 재사용)
- 버전: `versionCode` 3, `versionName` 1.3.0
