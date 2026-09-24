# ENDPOINTS — JupJup HTTP API

> 각 서비스는 독립 포트로 동작: **mac=3010 / plan=3020** (설정 변경 가능).
> 웹 포털 정적 에셋: `mac_web/` · `plan_web/` (루트 `/`).
> **내부망 전용 (R43)**: 전 라우트 `lanOnly()` — 사설대역(10/172.16-31/192.168/169.254·localhost) 외 403.
> **관리 인증 (R44 토큰 + R48 통합 ID/PW)**: `/api` 쓰기(POST/PUT/DELETE)는 인증 필수(401).
> 통합 PW 등록 시(앱 정보에서 전 서비스 동일 저장) `X-Admin-Id`/`X-Admin-Pw` 우선,
> 미등록 시 서비스별 `X-Auth-Token` 폴백. 토큰 페어링은 루프백 전용 `GET /api/admin/token`.
>
> 과거 프롬프트 저널(:3030)·커뮤니티(:3040) 엔드포인트는 서비스 삭제로 더 이상 제공하지 않음.

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