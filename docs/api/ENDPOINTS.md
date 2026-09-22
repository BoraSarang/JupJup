# ENDPOINTS — JupJup HTTP API

> 각 서비스는 독립 포트로 동작: **mac=3010 / plan=3020 / promptjournal=3030 / community=3040** (설정 변경 가능).
> 웹 포털 정적 에셋: `mac_web/` · `plan_web/` · `pj_web/` · `community_web/` (루트 `/`).
> **내부망 전용 (R43)**: 전 라우트 `lanOnly()` — 사설대역(10/172.16-31/192.168/169.254·localhost) 외 403.
> **관리 토큰 (R44)**: `/api` 쓰기(POST/PUT/DELETE)는 `X-Auth-Token` 필수(401).
> 토큰 페어링은 루프백 전용 `GET /api/admin/token` (기기 내 브라우저·adb forward).

## 공통 (서비스별 서버)
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/` | 웹 포털 index |
| GET | `/style.css` / `/app.js` | 정적 에셋 |
| GET | `/api/health` | 헬스체크 |

## Mac 서버 (`:3010`) — com.borasarang.macjupjup
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/apps` | 앱 목록/검색/필터/페이지네이션 |
| GET | `/api/apps/{id}` | 앱 상세 + 소스 + 버전 이력 |
| POST | `/api/apps/seed` | 수동 시드 (trackId/name) |
| GET | `/api/apps/seed/status` | 시드 상태 |
| GET | `/api/watchlist` | 수집 소스 목록 |
| POST | `/api/sources/{id}/toggle` | 소스 on/off |
| POST | `/api/sources/{id}/interval` | 수집 주기 변경(분, 15 이상, R43) |
| POST | `/api/sync` | 즉시 수집 |
| POST | `/api/translate` | 번역 즉시 실행 |
| GET | `/api/stats` / `stats/trends` / `stats/collect` / `stats/insights` | 통계 |
| GET | `/api/notifications...` | 알림 조회/읽음/삭제/정리 |
| GET·POST | `/api/settings` | 설정 조회·저장 (포트 등) |
| GET | `/api/news?main=&sub=&q=&page=&pageSize=` | 뉴스 목록 (R32, sub=전체는 해제) |
| GET | `/api/news/{id}` | 뉴스 상세 + 본문 + 관련 앱 (R32) |
| GET | `/api/main` | 대시보드 일괄 (오늘 수집·분야별 건수·최신 4+4+4·업데이트 앱 8) (R32) |

## Plan 서버 (`:3020`) — com.borasarang.planjupjup
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/plans` / `/api/plans/{id}` | 요금제 목록·상세 |
| GET | `/api/sources` | 수집 소스 목록 |
| POST | `/api/sources/{id}/toggle` | 소스 on/off |
| POST | `/api/sources/{id}/interval` | 수집 주기 변경(분, 허용값 내, R44) |
| POST | `/api/sync` | 즉시 수집 |
| GET | `/api/stats` | 요약 |
| GET | `/api/stats/overview` `/brands` `/networks` `/distribution` `/trends` `/value-ranking` `/collection-health` `/insights` | 대시보드 |
| GET | `/api/notifications...` | 알림 조회/읽음/삭제/정리 |
| GET·POST | `/api/settings` | 설정 조회·저장 |

> 상세 JSON 스키마는 각 서버 코드(`HttpServerService`) 주석 기준.

## Community 서버 (`:3040`) — com.borasarang.communityjupjup
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/categories` | 통합 카테고리 10종 |
| GET | `/api/sources` | 수집 소스 목록 + 게시글 수 |
| POST | `/api/sources/{id}/toggle` | 소스 on/off (스케줄 동기화) |
| GET | `/api/posts?category_id&source_id&q&sort&page` | 게시글 목록/검색/필터/페이지네이션 (source_id 복수 가능) |
| GET | `/api/posts/{id}` | 게시글 상세 + 출처/보드 |
| POST | `/api/posts/{id}/refresh` | 단일 게시글 상세 다시 가져오기 (요약·썸네일 보충) |
| GET | `/api/thumb?url=` | 썸네일 프록시 (CDN 핫링크 차단 우회, image만·3MB 상한) |
| GET | `/api/boards?source_id` | 게시판 목록 (on/off·카테고리·주기 포함) |
| POST | `/api/boards/batch` | 게시판 일괄 적용 (추가·수정·삭제, 항목별 sourceId·주기) |
| GET | `/api/sites` | 사이트(1차) 목록 (보드수·글수) |
| GET | `/api/board-catalog?domain=` | 사이트별 추천 게시판 목록 |
| POST | `/api/sources/{id}/purge` | 소스 게시글 비우기 (보드·설정 유지) |
| GET | `/api/ranking` | 24h 좋아요순 상위 20 |
| POST | `/api/sources/{id}/toggle` | 소스 on/off (스케줄 동기화) |
| POST | `/api/sync` | 즉시 수집 |
| POST | `/api/crawl/test` | 셀렉터 파싱 미리보기 5건 (어드민) |
| GET | `/api/logs` | 수집 로그 (어드민) |
| GET | `/api/stats` / `stats/overview` / `stats/collect` / `stats/trends` | 통계 |
| GET | `/api/notifications...` | 알림 조회/읽음/삭제/정리 |
| GET·POST | `/api/settings` | 설정 조회·저장 |