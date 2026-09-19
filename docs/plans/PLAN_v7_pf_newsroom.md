# PLAN_v7 — 프롬프트팩토리 뉴스룸 리디자인 (v1.12.0)

> 3분 초안. 외부 키트(`prompt-factory-redesign-kit`: 00 프롬프트 + 01 목업 + 04 용어사전) 적용. 사용자 선택: 전체 리디자인 + Tailwind CDN.

## 1. 요구사항

1. 팩토리 메타포 → 정기간행물 발행 아카이브 메타포로 전환 (기능 유지, 용어·레이아웃만 변경).
2. Masthead: 좌 `PROMPT JOURNAL / 프롬프트 저널` serif, 우 오늘 날짜 + 제N호.
3. 왼쪽 30% 발행 아카이브 (날짜+조간/석간+호수+헤드라인 요약, 활성 검은 배경, BREAKING/휴간 배지).
4. 오른쪽 70% 기사 지면 (H1 헤드라인 추출 + kicker + 발행 메타 + factbox 표).
5. 채널 헤더 발행주기 Pill (일간/조간·석간/속보).
6. 설정 서랍 2탭 분리 (Tab1 브리핑 채널 / Tab2 취재원 관리).
7. 스타일: Black/White + Blue 1개, 헤드라인 serif, factbox 얇은 border, Tailwind CDN.
8. 모바일: 아카이브가 상단으로 접힘 (현행 1열과 동일).

## 2. 용어 매핑 (04 그대로, 코드 적용)

- 프롬프트팩토리 → 뉴스룸 / PROMPT JOURNAL · 프롬프트 저널
- 프롬프트 → 브리핑 채널 · 발행 주기 Pill
- 히스토리/실행 기록 → 발행 아카이브 / 지난 호
- 마지막 실행 … → 제N호 발행 · M월 d일 요일 조간 · 취재 59.4초
- 지금 실행 → 최신호 발행 · 속보 발행
- 응답 복사 → 기사 복사 · 기록 삭제 → 이 호 폐기
- 프롬프트 보기·수정 → 취재 지침서 보기
- 공급자·API 키·모델 → 취재원 관리 · 모델 갱신 → 취재원 동기화 · 사용 → 투입 · 모델 검색 → 취재원 검색

## 3. 호수·배지 규칙 (백엔드 변경 없음, 프론트 역산)

- 제N호: `executions` 역순 인덱스 (최신이 최대호, 삭제 시 당겨짐 — 확정 번호 필요하면 후속 백엔드 컬럼).
- 조간/석간: 실행 시각 05–11시 조간, 17–23시 석간, 그 외 단신. `scheduleType=hourly`면 속보.
- BREAKING: 최근 24h 내 SUCCESS 있음. 휴간: 없음. 채널 칩 OFF는 기존 유지.
- 발행주기 Pill: `daily HH:mm` → 일간 · 매일 HH:mm 발행, 12시간 → 조간·석간, 1시간 → 속보.

## 4. 변경 파일 (pf_web 3종만)

- `index.html`: Tailwind CDN script + Noto Serif KR import(폴백 serif 스택) + Masthead + Pill 슬롯 + 서랍 2탭 구조.
- `app.js`: 용어 치환 + `formatEdition()/periodBadge()/cyclePill()/headlineOf()` 신설 + H1/kicker 렌더 + 탭 전환.
- `style.css`: 신문 커스텀만 남김 (double-border, paper 질감, factbox, serif). Tailwind와 중복 레이아웃 삭제.
- 제외: 백엔드·DB·API·매니페스트·권한·에러코드 변경 없음. mac/plan_web 손대지 않음.

## 5. 검증

- `node --check app.js` + `./build_and_run.sh build` (에셋 패키징 확인).
- agent-browser 데스크톱 + 390px 모바일 스팟체크, 콘솔 에러 0.
- 실기 3002: 최신호 발행·기사 복사·이 호 폐기·취재 지침서 저장·취재원 동기화 확인.
