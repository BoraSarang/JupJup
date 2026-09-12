# ENDPOINTS — JupJup HTTP API

> 각 서비스는 독립 포트로 동작: **mac=3000 / plan=3001** (설정 변경 가능).
> 웹 포털 정적 에셋: `mac_web/` · `plan_web/` (루트 `/`).

## 공통 (서비스별 서버)
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/` | 웹 포털 index |
| GET | `/style.css` / `/app.js` | 정적 에셋 |
| GET | `/api/health` | 헬스체크 |

## Mac 서버 (`:3000`) — com.borasarang.macjupjup
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/apps` | 앱 목록/검색/필터/페이지네이션 |
| GET | `/api/apps/{id}` | 앱 상세 + 소스 + 버전 이력 |
| POST | `/api/apps/seed` | 수동 시드 (trackId/name) |
| GET | `/api/apps/seed/status` | 시드 상태 |
| GET | `/api/watchlist` | 수집 소스 목록 |
| POST | `/api/sources/{id}/toggle` | 소스 on/off |
| POST | `/api/sync` | 즉시 수집 |
| POST | `/api/translate` | 번역 즉시 실행 |
| GET | `/api/stats` / `stats/trends` / `stats/collect` / `stats/insights` | 통계 |
| GET | `/api/notifications...` | 알림 조회/읽음/삭제/정리 |
| GET·POST | `/api/settings` | 설정 조회·저장 (포트 등) |

## Plan 서버 (`:3001`) — com.borasarang.planjupjup
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/plans` / `/api/plans/{id}` | 요금제 목록·상세 |
| GET | `/api/sources` | 수집 소스 목록 |
| POST | `/api/sources/{id}/toggle` | 소스 on/off |
| POST | `/api/sync` | 즉시 수집 |
| GET | `/api/stats` | 요약 |
| GET | `/api/stats/overview` `/brands` `/networks` `/distribution` `/trends` `/value-ranking` `/collection-health` `/insights` | 대시보드 |
| GET | `/api/notifications...` | 알림 조회/읽음/삭제/정리 |
| GET·POST | `/api/settings` | 설정 조회·저장 |

> 상세 JSON 스키마는 각 서버 코드(`HttpServerService`) 주석 기준.