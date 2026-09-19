# PLAN v9.0 — 맥줍줍 웹 포털 리디자인 (Apple 네이티브 톤)

> 날짜: 2026-09-19 · 플랫폼: macOS (WebView) · 상태: 진행 중
> 전제: 현재 바닐라 JS 포털(`services/mac/src/main/assets/mac_web/`)을 사용자 제공 목업 기준으로 전면 재작성

## 1. 개요 (Why)

현재 포털 문제:
- 필터가 상단 2줄 칩 행으로 범람 → 계층 불명확
- 카드: 아이콘 24px(작음), 태그 4개 과밀, 설명 말줄임 없이 잘림
- 시각 신호: 별점/언어/NEW 모두 같은 파란 pill → 중요도 구분 불가
- 모달: 텍스트 덤프, 설치 버튼 하단 묻힘

목표: Apple 네이티브 톤으로 전체 재구성 → 사이드바 네비 + 48px 아이콘 카드 + 탭 구조 모달

## 2. 목표 (What)

- [ ] **사이드바 네비게이션**: 타임라인/Watchlist/통계 분리, 필터를 `출처`·`유형` 그룹화 + 카운트 표시
- [ ] **카드 시스템**: 48px 아이콘 + 제목 + 설명 2줄 클램프, 언어 컬러닷 + NEW 도트 + ★별점 간결화
- [ ] **그리드**: 3열(반응형 2열/1열), rounded-2xl, 소프트 섀도우, hover 떠오름
- [ ] **모달 재구성**: 64px 히어로 아이콘 + 메타 pill + CTA 상단 배치 + 탭(소개/특징/새기능)
- [ ] **톤**: 배경 #f5f5f7, Pretendard 기반, macOS 메뉴바 앱답게 둥글고 가볍게

## 3. 구조 (How)

### 3.1 레이아웃 (index.html)

```html
<body>
  <aside class="sidebar" role="navigation" aria-label="메인 네비게이션">
    <nav class="sidebar-nav">
      <ul role="list">
        <li><a href="#timeline" data-view="timeline">📋 타임라인 <span class="count">806</span></a></li>
        <li><a href="#watchlist" data-view="watchlist">👁 Watchlist <span class="count">12</span></a></li>
        <li><a href="#stats" data-view="stats">📊 통계</a></li>
      </ul>
    </nav>
    <section class="sidebar-filters" aria-label="필터">
      <fieldset><legend>출처</legend>
        <label><input type="checkbox" value="github"> GitHub (342)</label>
        <label><input type="checkbox" value="mas"> Mac App Store (298)</label>
        <label><input type="checkbox" value="homebrew"> Homebrew (166)</label>
      </fieldset>
      <fieldset><legend>유형</legend>
        <label><input type="radio" name="type" value=""> 전체</label>
        <label><input type="radio" name="type" value="OSS"> 오픈소스</label>
        <label><input type="radio" name="type" value="FREE"> 프리</label>
        <label><input type="radio" name="type" value="PAID"> 유료</label>
      </fieldset>
    </section>
  </aside>
  <main class="main-content" role="main">
    <header class="top-bar">
      <h1>맥줍줍</h1>
      <div class="top-bar-actions">
        <input type="search" placeholder="앱 검색 (cc-usage, Quota...)" id="globalSearch">
        <button id="langToggle">한국어</button>
        <button id="notifBell" aria-label="알림">🔔</button>
      </div>
    </header>
    <section id="timelineView" class="view-panel" role="region" aria-label="타임라인">...</section>
    <section id="watchlistView" class="view-panel hidden" aria-label="Watchlist">...</section>
    <section id="statsView" class="view-panel hidden" aria-label="통계">...</section>
  </main>
  <dialog id="appModal" class="app-modal" role="dialog" aria-modal="true" aria-labelledby="modalTitle">...</dialog>
</body>
```

### 3.2 CSS 토큰 (style.css)

```css
:root {
  --bg: #f5f5f7;
  --card: #ffffff;
  --text: #1d1d1f;
  --secondary: #86868b;
  --border: #d2d2d7;
  --primary: #0066ff;
  --oss: #34a853;
  --free: #1a73e8;
  --paid: #e65100;
  --radius-lg: 16px;
  --radius-xl: 20px;
  --shadow-sm: 0 1px 3px rgba(0,0,0,.04);
  --shadow-md: 0 4px 16px rgba(0,0,0,.08);
  --shadow-lg: 0 8px 32px rgba(0,0,0,.12);
  --font-sans: 'Pretendard', -apple-system, BlinkMacSystemFont, sans-serif;
}
```

### 3.3 카드 HTML 구조

```html
<article class="app-card" role="listitem" data-id="..." tabindex="0">
  <div class="card-head">
    <img class="app-icon" src="..." alt="" loading="lazy" width="48" height="48">
    <div class="card-title-row">
      <h3 class="app-name">cc-usage</h3>
      <span class="lang-dot" data-lang="python" style="--dot-color: #fbc02d" aria-label="Python"></span>
      <span class="new-dot" aria-label="NEW"></span>
    </div>
    <p class="app-dev">jesseduffield</p>
  </div>
  <div class="card-badges">
    <span class="badge license-oss">오픈소스</span>
    <span class="badge category">개발</span>
  </div>
  <p class="card-desc line-clamp-2">Claude Code 등 AI 코딩 도구의 토큰 사용량을 실시간으로 추적하는 터미널 대시보드...</p>
  <div class="card-meta">
    <span class="stars" aria-label="GitHub 스타 6.2k">★ 6.2k</span>
    <span class="version">v1.2.3</span>
    <a class="source-link" href="..." target="_blank" rel="noopener">GitHub</a>
  </div>
</article>
```

### 3.4 언어 컬러닷 매핑 (app.js)

```js
const LANG_COLORS = {
  swift: '#fa544b', python: '#fbc02d', javascript: '#f7df1e',
  typescript: '#3178c6', rust: '#dea584', go: '#00add8',
  cpp: '#00599c', default: '#86868b'
};
```

### 3.5 모달 구조 (탭 + 히어로)

```html
<dialog id="appModal" class="app-modal">
  <div class="modal-hero">
    <img class="modal-icon" src="..." alt="" width="64" height="64">
    <div class="modal-hero-text">
      <h2 id="modalTitle">앱 이름</h2>
      <p class="modal-dev">개발사</p>
      <div class="modal-meta-pills">
        <span class="pill lang" data-lang="python">Python</span>
        <span class="pill license-oss">오픈소스</span>
        <span class="pill stars">★ 6.2k</span>
        <span class="pill new">NEW</span>
      </div>
    </div>
    <div class="modal-cta">
      <a class="btn-primary" href="..." target="_blank">홈페이지</a>
      <a class="btn-secondary" href="..." target="_blank">GitHub Repo</a>
    </div>
  </div>
  <div class="modal-tabs" role="tablist">
    <button role="tab" aria-selected="true" data-tab="intro">소개</button>
    <button role="tab" data-tab="features">특징</button>
    <button role="tab" data-tab="changelog">새 기능</button>
  </div>
  <div class="modal-panels">
    <div role="tabpanel" id="panel-intro" class="active">...</div>
    <div role="tabpanel" id="panel-features" hidden>...</div>
    <div role="tabpanel" id="panel-changelog" hidden>...</div>
  </div>
</dialog>
```

## 4. 작업 분해

| 단계 | 항목 | 파일 |
|---|---|---|
| 1 | PLAN 문서 작성 + TODO 등록 | 이 문서, docs/TODO.md |
| 2 | 디자인 토큰 + 레이아웃 CSS 재작성 | style.css |
| 3 | 시맨틱 HTML 구조 재작성 | index.html |
| 4 | 앱 로직 전면 재작성 (사이드바/카드/모달/탭) | app.js |
| 5 | 빌드 + smoke/unit 테스트 + 실기 검증 | build_and_run.sh |
| 6 | 문서 갱신 (CHANGELOG, DESIGN, 세션 로그) | docs/ |

## 5. 검증 기준

- `./build_and_run.sh build` 성공
- `./build_and_run.sh test macos smoke` 통과
- `./build_and_run.sh test macos unit` 통과
- lint 오류 0
- 실기/시뮬레이터: 포털 3000 HTTP 200, 콘솔 에러 0, 크래시 0
- DebugPanel: ERROR 0, `[INFO] [FEATURE] 맥줍줍-리디자인` 진입 로그 확인
- 성능: 초기 로드 ≤2s, 그리드 60fps, 모달 열림 ≤300ms

## 6. 호환성 유지

- 기존 API 엔드포인트 100% 재사용 (`/api/apps`, `/api/apps/:id`, `/api/stats*`, `/api/notifications`, `/api/sync`, `/api/translate`)
- 에러코드 기존 `E-AND-*` 체계 재사용 (신규 없음)
- 설정·데이터 저장소 변경 없음 (WebView는 서버 API만 호출)

## 7. 위험 요소 & 대응

| 위험 | 대응 |
|---|---|
| 기존 JS(837줄) 완전 교체 시 회귀 | 함수 시그니처 유지(`loadTimeline`, `openDetail` 등)하되 내부만 재작성 |
| 모바일 사이드바 UX | ≤640px에서 오버레이 드로어 전환 (CSS + JS 토글) |
| 다크모드 미지원 | `prefers-color-scheme` 미디어쿼리 토큰 오버라이드 추가 (선택) |
| API 필드 변경 시 카드 깨짐 | 옵셔널 체이닝 + 폴백 강화 |

## 8. 완료 조건 (DoD)

- [ ] PLAN 문서 작성 완료
- [ ] TODO 등록 및 진행 표시
- [ ] index.html / style.css / app.js 3파일 전면 재작성 완료
- [ ] 빌드·테스트·실기 검증 모두 통과
- [ ] CHANGELOG.md `[Unreleased]` 섹션 갱신
- [ ] DESIGN.md 웹 포털 디자인 토큰/컴포넌트 명세 추가
- [ ] session 로그 8줄 요약 저장
- [ ] bd close