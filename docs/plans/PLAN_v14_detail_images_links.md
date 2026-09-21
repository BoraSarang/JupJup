# PLAN_v14 — 상세 이미지 전체·링크 보존 + 보관기간 확장 (R28)

> 상세 요약(500자)에 이미지 목록(최대 5)과 실제 링크(최대 3)를 메타로 보존.
> 본문 텍스트 저장 금지 정책 유지 — 이미지/링크는 원문 연결용 메타.

## 1. 저장

- `CommunityPost.imageUrls` JSON 배열 (DB v5, 기본 `[]`), `MIGRATION_4_5`.
- `PostDraft.imageUrls` + `DetailResult{summary, thumbnailUrl, imageCount, imageUrls, links}`
  + `BodyLink{text, href}` + `encodeImageUrls/decodeImageUrls` (비파괴 복호).
- `updateDetailByUrl`/`updateDetailByCanonical` → `COALESCE` 유지 보충(기등록 공란만 채움).
- refresh(단건 새로고침)는 사용자 명시 동작 → 파싱 결과 신뢰, 빈 값이면 기존값 클리어.
  `summary=""` 전달 시 COALESCE가 공백으로 확정 (이미지 전용 앵커 링크 잔재 정리).

## 2. 파서

- `parseDetail`: 텍스트 먼저 500자 예산(링크 블록 포함), `🔗 텍스트: URL` 블록 부착.
  링크가 없으면 예산 전액 텍스트.
- `extractLinks`: http(s)만·중복 제거·`text==href`/이미지 전용 앵커 제외(짤 슬라이드처럼
  이미지를 감싼 `<a>`는 실제 링크 아님).
- `pickContentImages`: 본문 이미지 최대 5장 (UI 조각·이모티콘 제외, 중복 제거).

## 3. 서버·웹

- `GET /api/posts` 목록에 `imageCount`, `/api/posts/{id}`에 `images[]` + `imageCount`.
- refresh 응답에 `images[]` 포함.
- 웹: 카드에 `🖼N` 뱃지, 상세 모달 다중 이미지 갤러리(`/api/thumb` 프록시 경유,
  최대 5, 52vh contain), 요약 내 `🔗` 줄을 `<a target=_blank>` 렌더링,
  `modal-box` max-height+overflow-auto + `.actions sticky` (버튼 잘림 방지).

## 4. 보관기간 확장 (리텐션)

- 설정 옵션 30/90 → **3/7/14/30/90**, 기본값 `DEFAULT_RETENTION_DAYS = 3`.
- 서버 유효범위(1..365) 유지, 웹 설정은 자유 입력 유지.

## 5. 검증

- 단위: 링크 3 상한·이미지 5 상한·500자 예산·이미지 앵커 제외·JSON 왕복 (community 13종)
- 실기(S22): refresh로 G마켓 글 이미지 2장 저장+갤러리 렌더링, 디시 글 링크 3개 클릭 렌더링,
  스티키 버튼·스크롤 확인, lint 0 / 단위 전부 SUCCESS