# PERMISSIONS — JupJup

> 통합 앱이 사용하는 권한과 근거. (mac/plan 두 서비스 동일 권한 집합)

| 권한 | 근거 | 위험도 |
|---|---|---|
| INTERNET | Ktor 임베디드 서버 구동 + 크롤링 HTTP | 낮음 |
| ACCESS_NETWORK_STATE | 연결 상태 확인 (크롤러·와치독) | 낮음 |
| ACCESS_WIFI_STATE | 로컬 IP 표시 (포털 URL 안내) | 낮음 |
| FOREGROUND_SERVICE | 서버 포그라운드 서비스 | 낮음 |
| FOREGROUND_SERVICE_DATA_SYNC | dataSync 타입 FGS (Android 14+) | 낮음 |
| POST_NOTIFICATIONS | 수집 완료·실패 알림 (Android 13+ 런타임) | 낮음 |
| WAKE_LOCK | WorkManager 수집 작업 | 낮음 |
| RECEIVE_BOOT_COMPLETED | 재부팅 후 서버 자동 재시작 (autoStart) | 낮음 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 배터리 최적화 예외 (백그라운드 수집 안정화) | 중 — 설정 화면 유도 |

- 저장 데이터: Room DB(서비스별 파일) + DataStore(preferences) + 웹 에셋. 개인정보 수집 없음.
- 오직 로컬 서버(같은 Wi-Fi 브라우저) + 수집 대상 공개 소스와 통신.