# PLAN v11 — 맥줍줍 출처 필터 실측화 (R24)

> 날짜: 2026-09-19 · 플랫폼: macOS (WebView) + Android 백엔드 · 상태: 완료
> 동기: 사이드바 출처가 하드코딩 placeholder(342/298/166) + 체크박스 무동작

## 1. 설계 결정

- 목업 3버킷(GitHub/MAS/Homebrew) 폐기: 실제 수집처 5종과 불일치 (Homebrew 소스 자체가 없음)
- 실제 5종 + 실측 카운트로 표시: `mas_discovery` MAS 키워드 발견, `github_search` GitHub 신규 저장소,
  `chart_rss` Mac 차트 RSS, `itunes_lookup` iTunes 버전 폴링, `github_releases` GitHub 릴리즈 추적
- 집계 기준: 대표 `sourceId` (다중 매핑 시 primary). `manual_seed` 등 미등록 ID는 표시명 폴백

## 2. 백엔드

- `AppDao.countBySource()` (GROUP BY sourceId, 스키마 변경 없음 → 마이그레이션 불필요)
- `listFiltered`/`countFiltered`에 `filterBySource` + `sourceIds` (기존 Boolean 플래그 패턴 재사용)
- `AppFilter.sourceIds: Set<String>` (기본 빈집합 = 전체)
- `TrendStats.bySource: List<SourceCount>` — 기존 `SourceCount`에 `sourceId = ""` 기본값 추가 (재선언 회피)
- `trends()`에서 crawlSource 이름 조인 + 개수 내림차순
- `/api/apps?sourceIds=a,b` 파싱, `/api/stats/trends`에 `bySource[]` 추가

## 3. 프론트 (mac_web)

- 출처 그룹 동적 렌더 (trends.bySource), 전체 선택/해제 = 필터 없음
- 목록 쿼리에 `sourceIds` csv 전송, 로고 리셋 포함

## 4. 검증

- 실측: bySource 합계 4489 = 타임라인 전체 일치, 필터 조합 3898 = 3816+82 일치
- 브라우저: 체크 해제 → 673개 (4489−3816) 일치
