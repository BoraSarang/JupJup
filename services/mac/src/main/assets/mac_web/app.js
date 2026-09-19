/* 맥줍줍 포털 리디자인 — 바닐라 JS. 사이드바 네비 + 카드 그리드 + 탭 모달 */
(function () {
  'use strict';

  const CATEGORIES = ['생산성', '유틸리티', '보안·프라이버시', '미디어·엔터', '개발',
    '디자인·크리에이티브', '금융', '글쓰기·노트', '시스템최적화', '커뮤니케이션'];

  const LICENSE_LABEL = { OSS: '오픈소스', FREE: '프리', PAID: '유료' };
  const LICENSE_CLASS = { OSS: 'license-oss', FREE: 'license-free', PAID: 'license-paid' };

  const LANG_COLORS = {
    swift: '#fa544b', python: '#fbc02d', javascript: '#f7df1e',
    typescript: '#3178c6', rust: '#dea584', go: '#00add8',
    cpp: '#00599c', default: '#86868b'
  };

  const PAGE_SIZE = 20;

  const state = {
    view: 'timeline',
    sidebarCollapsed: false,
    sidebarMobileOpen: false,
    filters: {
      sources: [],
      allSources: [],
      licenseType: '',
      category: '',
      q: '',
      sort: 'newest',
      page: 1,
      watchMode: 'updated'
    },
    lang: 'ko',
    modal: { currentId: null, activeTab: 'intro' },
    stats: { collectDays: 14, collectSource: '' }
  };

  const $ = (id) => document.getElementById(id);
  const $$ = (sel, ctx = document) => ctx.querySelectorAll(sel);
  const $1 = (sel, ctx = document) => ctx.querySelector(sel);

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
  }

  function pick(ko, en) {
    if (state.lang === 'ko' && ko) return { text: ko, isKo: true };
    return { text: en || ko || '', isKo: false };
  }

  function fmtDate(ts) {
    if (!ts) return '-';
    const d = new Date(ts);
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  function fmtDateTime(ts) {
    if (!ts) return '-';
    const d = new Date(ts);
    return fmtDate(ts) + ' ' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  /* 날초 → 읽기 형태 (59초 / 3분 20초 / 9시간 10분) */
  function fmtDuration(totalSecs) {
    const s = Math.max(0, Math.round(totalSecs));
    if (s < 60) return s + '초';
    const m = Math.floor(s / 60);
    if (m < 60) {
      const r = s % 60;
      return r ? m + '분 ' + r + '초' : m + '분';
    }
    const h = Math.floor(m / 60);
    const rm = m % 60;
    return rm ? h + '시간 ' + rm + '분' : h + '시간';
  }

  function fmtSize(bytes) {
    if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(1) + 'GB';
    if (bytes >= 1048576) return Math.round(bytes / 1048576) + 'MB';
    return Math.round(bytes / 1024) + 'KB';
  }

  function api(path) {
    return fetch(path).then(r => {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
  }

  function toast(msg) {
    const t = $('toast');
    t.textContent = msg;
    t.hidden = false;
    t.classList.add('show');
    setTimeout(() => { t.classList.remove('show'); setTimeout(() => t.hidden = true, 200); }, 2200);
  }

  /* ---------- 사이드바 ---------- */
  function initSidebar() {
    const sidebar = $('sidebar');
    const toggle = $('sidebarToggle');
    const overlay = $('sidebarOverlay');
    const mainContent = $('mainContent');

    toggle.onclick = () => {
      if (window.innerWidth <= 720) {
        state.sidebarMobileOpen = !state.sidebarMobileOpen;
        sidebar.classList.toggle('mobile-open', state.sidebarMobileOpen);
        overlay.classList.toggle('visible', state.sidebarMobileOpen);
        toggle.setAttribute('aria-expanded', state.sidebarMobileOpen);
      } else {
        state.sidebarCollapsed = !state.sidebarCollapsed;
        sidebar.classList.toggle('collapsed', state.sidebarCollapsed);
        toggle.setAttribute('aria-expanded', !state.sidebarCollapsed);
      }
    };

    overlay.onclick = () => {
      state.sidebarMobileOpen = false;
      sidebar.classList.remove('mobile-open');
      overlay.classList.remove('visible');
      toggle.setAttribute('aria-expanded', 'false');
    };

    $$('.nav-link', sidebar).forEach(link => {
      link.onclick = (e) => {
        e.preventDefault();
        const view = link.dataset.view;
        if (view) switchView(view);
        if (window.innerWidth <= 720) {
          state.sidebarMobileOpen = false;
          sidebar.classList.remove('mobile-open');
          overlay.classList.remove('visible');
          toggle.setAttribute('aria-expanded', 'false');
        }
      };
      link.onkeydown = (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); link.click(); } };
    });

    // 출처 체크박스는 trends 로드 후 동적 렌더 — 변경 감지는 컨테이너 위임
    $('sourceFilters').addEventListener('change', () => {
      const checked = Array.from($$('#sourceFilters input[type="checkbox"]:checked')).map(c => c.value);
      const total = $$('#sourceFilters input[type="checkbox"]').length;
      // 전체 선택 또는 전체 해제 = 필터 없음 (빈 결과 함정 방지)
      state.filters.sources = (checked.length === 0 || checked.length === total) ? [] : checked;
      state.filters.page = 1;
      reloadCurrentView();
    });

    $$('#typeFilters input[type="radio"]').forEach(radio => {
      radio.onchange = () => {
        state.filters.licenseType = radio.value;
        state.filters.page = 1;
        reloadCurrentView();
      };
    });

    $$('#categorySubnav input[type="radio"]').forEach(radio => {
      radio.onchange = () => {
        state.filters.category = radio.value;
        state.filters.page = 1;
        // 카테고리는 타임라인 하위이므로 다른 뷰에서는 타임라인으로 이동
        if (state.view !== 'timeline') switchView('timeline');
        else reloadCurrentView();
      };
    });

    window.addEventListener('resize', () => {
      if (window.innerWidth > 720 && state.sidebarMobileOpen) {
        state.sidebarMobileOpen = false;
        sidebar.classList.remove('mobile-open');
        overlay.classList.remove('visible');
        toggle.setAttribute('aria-expanded', 'false');
      }
    });
  }

  /* 사이드바 배지 = 뷰별 전체 카운트 (검색/필터 결과 total이 아님).
   * 목록 로드 시 덮어쓰지 않고, 진입 시 1회 + 뷰 전환 시 갱신. */
  function loadNavCounts() {
    api('/api/apps?page=1&pageSize=1').then(d => {
      const el = $('navCountTimeline');
      if (el) el.textContent = d.total;
    }).catch(() => {});
    api('/api/apps?bumped=true&updatedOnly=true&page=1&pageSize=1').then(d => {
      const el = $('navCountWatchlist');
      if (el) el.textContent = d.total;
    }).catch(() => {});
  }

  /* 출처 필터 렌더 (trends.bySource 실측 — 전체 선택 상태 유지) */
  function renderSourceFilters(list) {
    const box = $('sourceFilters');
    const prevChecked = new Set(
      Array.from($$('#sourceFilters input[type="checkbox"]:checked')).map(c => c.value)
    );
    const firstRender = prevChecked.size === 0 && !box.dataset.ready;
    state.filters.allSources = list.map(s => s.sourceId);
    box.dataset.ready = '1';
    box.innerHTML = list.map(s => {
      const checked = firstRender || prevChecked.has(s.sourceId) ? ' checked' : '';
      return '<label class="filter-option">' +
        '<input type="checkbox" value="' + esc(s.sourceId) + '"' + checked + '>' +
        '<span>' + esc(s.sourceName) + '</span>' +
        '<small>' + s.count + '</small></label>';
    }).join('') || '<div class="empty-state" style="padding:12px 0;font-size:12px;">수집처 없음</div>';
  }

  /* ---------- 뷰 전환 ---------- */
  function switchView(view) {
    state.view = view;
    $$('.nav-link').forEach(l => l.classList.toggle('active', l.dataset.view === view));
    $$('.view-panel').forEach(p => {
      const isActive = p.id === view + 'View';
      p.classList.toggle('active', isActive);
      p.hidden = !isActive;
    });
    state.filters.page = 1;
    state.modal.currentId = null;
    closeModal();
    // 카테고리 서브네비는 타임라인 선택 시만 표시
    const subnav = $('categorySubnav');
    if (subnav) subnav.hidden = (view !== 'timeline');
    loadNavCounts();
    reloadCurrentView();
    window.scrollTo(0, 0);
  }

  function reloadCurrentView() {
    if (state.view === 'watchlist') loadWatchlist();
    else if (state.view === 'stats') loadStats();
    else loadTimeline();
  }

  /* ---------- 검색/정렬 ---------- */
  function bindSearchSort() {
    let searchTimer = null;
    $('globalSearch').addEventListener('input', e => {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        state.filters.q = e.target.value.trim();
        state.filters.page = 1;
        reloadCurrentView();
      }, 300);
    });

    $('sortSelect').onchange = e => {
      state.filters.sort = e.target.value;
      state.filters.page = 1;
      reloadCurrentView();
    };

    $$('.watch-mode-tab').forEach(btn => {
      btn.onclick = () => {
        state.filters.watchMode = btn.dataset.mode;
        state.filters.page = 1;
        $$('.watch-mode-tab').forEach(b => {
          b.classList.toggle('active', b === btn);
          b.setAttribute('aria-selected', b === btn);
        });
        loadWatchlist();
        window.scrollTo(0, 0);
      };
    });

    $$('.period-tab', $('statsContent')).forEach(btn => {
      btn.onclick = () => {
        state.stats.collectDays = parseInt(btn.dataset.days, 10) || 14;
        $$('.period-tab', $('statsContent')).forEach(b => b.classList.toggle('active', b === btn));
        loadStats();
      };
    });
  }

  /* ---------- 카드 렌더링 ---------- */
  function licenseBadge(a) {
    const cls = LICENSE_CLASS[a.license] || 'license-free';
    return '<span class="badge ' + cls + '">' + esc(LICENSE_LABEL[a.license] || a.license) + '</span>';
  }

  function categoryBadge(cat) {
    return '<span class="badge category">' + esc(cat || '') + '</span>';
  }

  function langDot(lang) {
    if (!lang) return '';
    const key = lang.toLowerCase();
    const color = LANG_COLORS[key] || LANG_COLORS.default;
    return '<span class="lang-dot" style="--dot-color:' + color + '" aria-label="' + esc(lang) + '"></span>';
  }

  function newDot(isNew) {
    return isNew ? '<span class="new-dot" aria-label="NEW"></span>' : '';
  }

  function iconHtml(a) {
    if (a.iconUrl) {
      return '<img class="app-icon" src="' + esc(a.iconUrl) + '" alt="" loading="lazy" width="48" height="48">';
    }
    const ch = (a.name || '?').trim().charAt(0).toUpperCase();
    return '<span class="app-icon" style="display:flex;align-items:center;justify-content:center;background:var(--primary);color:#fff;font-size:20px;font-weight:700;" aria-hidden="true">' + esc(ch) + '</span>';
  }

  function cardHtml(a) {
    const notes = pick(a.releaseNotesKo, a.releaseNotesSummary);
    const notesText = notes.text || pick(a.descriptionKo, a.descriptionSnippet).text || '';
    const notesHtml = notesText ? '<p class="card-desc line-clamp-2">' + esc(stripMd(notesText)) + '</p>' : '';

    const metaParts = [];
    if (a.stars != null) metaParts.push('<span class="stars" aria-label="GitHub 스타 ' + a.stars + '">★ ' + a.stars + '</span>');
    if (a.averageRating != null) metaParts.push('<span class="rating" aria-label="평점 ' + a.averageRating + '">⭐ ' + a.averageRating + '</span>');
    if (a.primaryLanguage) metaParts.push('<span class="lang" aria-label="언어 ' + esc(a.primaryLanguage) + '">' + esc(a.primaryLanguage) + '</span>');
    if (a.forks != null) metaParts.push('<span class="forks" aria-label="포크 ' + a.forks + '">⑂ ' + a.forks + '</span>');
    if (a.fileSize) metaParts.push('<span class="size">' + fmtSize(a.fileSize) + '</span>');

    const verHtml = a.version
      ? '<span class="version">' + (a.prevVersion ? '<s>' + esc(a.prevVersion) + '</s> ' : '') + esc(a.version) + '</span>'
      : '<span class="version">포착 ' + fmtDate(a.releaseDate || a.firstSeenAt) + '</span>';

    const sourceHtml = a.sourceUrl
      ? '<a class="source-link" href="' + esc(a.sourceUrl) + '" target="_blank" rel="noopener" aria-label="출처에서 보기: ' + esc(a.sourceName || '원문') + '">' + esc(a.sourceName || '원문') + '</a>'
      : '';

    return '<article class="app-card" role="listitem" data-id="' + esc(a.id) + '" tabindex="0">' +
      '<div class="card-head">' + iconHtml(a) +
      '<div class="card-title-row">' +
      '<h3 class="app-name">' + esc(a.name) + '</h3>' +
      langDot(a.primaryLanguage) + newDot(a.isNew) +
      '</div>' +
      '<p class="app-dev">' + esc(a.developer || '') + '</p>' +
      '</div>' +
      '<div class="card-badges">' + licenseBadge(a) + categoryBadge(a.category) + '</div>' +
      notesHtml +
      '<div class="card-meta">' + metaParts.join('') + verHtml + sourceHtml + '</div>' +
      '</article>';
  }

  function renderCards(container, apps) {
    container.innerHTML = apps.map(cardHtml).join('');
    bindCards(container);
  }

  function bindCards(container) {
    $$('.app-card', container).forEach(el => {
      el.onclick = () => openModal(el.dataset.id);
      el.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openModal(el.dataset.id); } };
      $$('a[target="_blank"]', el).forEach(a => {
        a.onclick = e => e.stopPropagation();
        a.onkeydown = e => e.stopPropagation();
      });
    });
  }

  /* ---------- 타임라인 ---------- */
  function buildTimelineQuery() {
    const p = new URLSearchParams({ sort: state.filters.sort, page: state.filters.page, pageSize: PAGE_SIZE });
    if (state.filters.licenseType) p.set('license', state.filters.licenseType);
    if (state.filters.category) p.set('category', state.filters.category);
    if (state.filters.q) p.set('q', state.filters.q);
    if (state.filters.sources.length) p.set('sourceIds', state.filters.sources.join(','));
    return '/api/apps?' + p.toString();
  }

  let timelineSeq = 0;
  function loadTimeline() {
    const grid = $('appGrid');
    const empty = $('emptyTimeline');
    const seq = ++timelineSeq;
    grid.innerHTML = '';
    empty.hidden = true;
    grid.hidden = true;

    api(buildTimelineQuery()).then(d => {
      if (seq !== timelineSeq) return;
      $('visibleCount').textContent = '검색 결과 ' + d.total + '개';
      if (!d.apps.length) {
        grid.hidden = true;
        empty.hidden = false;
        $('pagination').innerHTML = '';
        return;
      }
      grid.hidden = false;
      renderCards(grid, d.apps);
      renderPagination($('pagination'), d.page, d.pageSize, d.total, p => { state.filters.page = p; loadTimeline(); window.scrollTo(0, 0); });
    }).catch(() => {
      if (seq !== timelineSeq) return;
      grid.hidden = true;
      empty.querySelector('.empty-state-title').textContent = '데이터를 불러오는데 실패했습니다';
      empty.querySelector('.empty-state-desc').textContent = '서버가 실행 중인지 확인해주세요.';
      empty.hidden = false;
    });
  }

  /* ---------- Watchlist ---------- */
  function buildWatchlistQuery() {
    const p = new URLSearchParams({ sort: state.filters.sort, page: state.filters.page, pageSize: PAGE_SIZE, bumped: 'true' });
    if (state.filters.watchMode === 'updated') p.set('updatedOnly', 'true');
    if (state.filters.licenseType) p.set('license', state.filters.licenseType);
    if (state.filters.category) p.set('category', state.filters.category);
    if (state.filters.q) p.set('q', state.filters.q);
    if (state.filters.sources.length) p.set('sourceIds', state.filters.sources.join(','));
    return '/api/apps?' + p.toString();
  }

  function loadWatchlist() {
    const grid = $('watchGrid');
    const empty = $('emptyWatchlist');
    grid.innerHTML = '';
    empty.hidden = true;
    grid.hidden = true;

    api(buildWatchlistQuery()).then(d => {
      $('visibleCount').textContent = '검색 결과 ' + d.total + '개';
      if (!d.apps.length) {
        grid.hidden = true;
        empty.hidden = false;
        $('watchPagination').innerHTML = '';
        return;
      }
      grid.hidden = false;
      renderCards(grid, d.apps);
      renderPagination($('watchPagination'), d.page, d.pageSize, d.total, p => { state.filters.page = p; loadWatchlist(); window.scrollTo(0, 0); });
    }).catch(() => {
      grid.hidden = true;
      empty.querySelector('.empty-state-title').textContent = '데이터를 불러오는데 실패했습니다';
      empty.hidden = false;
    });
  }

  function renderPagination(box, page, pageSize, total, go) {
    const pages = Math.max(1, Math.ceil(total / pageSize));
    if (pages <= 1) { box.innerHTML = ''; return; }
    let html = '<button type="button" data-p="' + (page - 1) + '"' + (page <= 1 ? ' disabled' : '') + ' aria-label="이전">‹</button>';
    const nums = pageNums(page, pages);
    nums.forEach(n => {
      if (n === '…') { html += '<button type="button" disabled aria-hidden="true">…</button>'; return; }
      html += '<button type="button" data-p="' + n + '"' + (n === page ? ' class="active" aria-current="page"' : '') + '>' + n + '</button>';
    });
    html += '<button type="button" data-p="' + (page + 1) + '"' + (page >= pages ? ' disabled' : '') + ' aria-label="다음">›</button>';
    box.innerHTML = html;
    $$('button[data-p]', box).forEach(b => {
      if (b.disabled) return;
      b.onclick = () => go(parseInt(b.dataset.p, 10));
    });
  }

  function pageNums(page, pages) {
    const set = {};
    [1, 2, page - 1, page, page + 1, pages - 1, pages].forEach(n => { if (n >= 1 && n <= pages) set[n] = true; });
    const arr = Object.keys(set).map(Number).sort((a, b) => a - b);
    const out = [];
    let prev = 0;
    arr.forEach(n => { if (n - prev > 1) out.push('…'); out.push(n); prev = n; });
    return out;
  }

  /* ---------- 통계 ---------- */
  function loadStats() {
    const box = $('statsContent');
    box.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div><p class="empty-state-title">통계 불러오는 중…</p></div>';

    api('/api/stats').then(s => api('/api/stats/trends').then(t => ({ s, t }))).then(r => {
      box.innerHTML = baseStatsHtml(r.s, r.t) + '<div id="collectBox"></div>';
      loadInsightsBox();
      loadCollectBox();
    }).catch(() => {
      box.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div><p class="empty-state-title">통계를 불러오는데 실패했습니다</p></div>';
    });
  }

  function baseStatsHtml(s, t) {
    const cats = Object.keys(t.byCategory || {}).sort((a, b) => t.byCategory[b] - t.byCategory[a]);
    const maxCat = cats.length ? t.byCategory[cats[0]] : 1;
    const lic = t.byLicense || {};

    let html = '<div class="kpi-grid">' +
      kpi(s.totalApps, '수집 앱') + kpi(s.activeSources, '활성 소스') +
      kpi(t.newLast7d, '최근 7일 신규') + kpi(t.versionBumpsLast7d, '최근 7일 버전업') +
      kpi(t.aiTagCount, 'AI-Agent') + kpi(t.menuBarTagCount, 'MenuBar') +
      '</div>';

    html += '<h3 class="stats-section-title">카테고리 분포</h3>';
    cats.forEach(c => {
      const v = t.byCategory[c];
      html += '<div class="bar-row"><span class="bar-name">' + esc(c) + '</span>' +
        '<span class="bar-track"><span class="bar-fill" style="width:' + Math.round(v / maxCat * 100) + '%"></span></span>' +
        '<span class="bar-count">' + v + '</span></div>';
    });

    html += '<h3 class="stats-section-title">라이선스 분포</h3>' +
      '<div class="bar-row"><span class="bar-name">오픈소스</span>' + bar(lic.OSS || 0, s.totalApps) + '</div>' +
      '<div class="bar-row"><span class="bar-name">프리</span>' + bar(lic.FREE || 0, s.totalApps) + '</div>' +
      '<div class="bar-row"><span class="bar-name">유료</span>' + bar(lic.PAID || 0, s.totalApps) + '</div>';

    return html;
  }

  function loadInsightsBox() {
    const box = $('insightBox');
    if (!box) return;
    api('/api/stats/insights').then(ins => {
      if (!ins.insights || !ins.insights.length) return;
      box.innerHTML = '<h3 class="stats-section-title">💡 인사이트</h3><div class="insight-grid">' + ins.insights.map(n =>
        '<div class="insight-card"><div class="insight-icon">' + esc(n.icon) + '</div>' +
        '<div><b>' + esc(n.title) + '</b><p>' + esc(n.body) + '</p></div></div>'
      ).join('') + '</div>';
    }).catch(() => {});
  }

  var collectCache = [];
  function loadCollectBox() {
    const box = $('collectBox');
    if (!box) return;
    api('/api/stats/collect?days=' + state.stats.collectDays).then(c => {
      collectCache = c.days || [];
      box.innerHTML = '<h3 class="stats-section-title">📥 수집처별 일별 수집량 (' + state.stats.collectDays + '일)</h3>' + collectSectionHtml();
      bindCollectSection(box);
    }).catch(() => {});
  }

  function collectSources() {
    const map = {};
    collectCache.forEach(d => (d.bySource || []).forEach(s => {
      if (!map[s.sourceId]) map[s.sourceId] = { sourceId: s.sourceId, sourceName: s.sourceName, found: 0, newCount: 0, updated: 0 };
      map[s.sourceId].found += s.found;
      map[s.sourceId].newCount += s['new'] || 0;
      map[s.sourceId].updated += s.updated;
    }));
    return Object.keys(map).map(k => map[k]).sort((a, b) => b.found - a.found);
  }

  function collectScope(day) {
    if (!state.stats.collectSource) return { found: day.found, newCount: day['new'] || 0, updated: day.updated, name: '전체' };
    const hit = (day.bySource || []).find(s => s.sourceId === state.stats.collectSource);
    return hit ? { found: hit.found, newCount: hit['new'] || 0, updated: hit.updated, name: hit.sourceName } : { found: 0, newCount: 0, updated: 0, name: '' };
  }

  function collectSectionHtml() {
    const srcs = collectSources();
    const chips = '<button type="button" class="source-chip' + (!state.stats.collectSource ? ' active' : '') + '" data-src="">전체</button>' +
      srcs.map(s => '<button type="button" class="source-chip' + (state.stats.collectSource === s.sourceId ? ' active' : '') + '" data-src="' + esc(s.sourceId) + '">' + esc(s.sourceName) + '</button>').join('');
    const table = '<table class="collect-table"><tr><th>수집처</th><th>발견</th><th>신규</th><th>갱신</th></tr>' +
      srcs.map(s => '<tr data-src="' + esc(s.sourceId) + '"><td>' + esc(s.sourceName) + '</td><td class="count">' + s.found + '</td><td class="count">' + s.newCount + '</td><td class="count">' + s.updated + '</td></tr>').join('') + '</table>';
    return '<div class="source-chips" id="collectSources">' + chips + '</div>' + collectChart(collectCache) + table;
  }

  function bindCollectSection(box) {
    $$('#collectSources .source-chip', box).forEach(b => {
      b.onclick = () => { state.stats.collectSource = b.dataset.src || ''; loadStats(); };
    });
    $$('.collect-table tr[data-src]', box).forEach(tr => {
      tr.onclick = () => { state.stats.collectSource = tr.dataset.src; loadStats(); };
    });
  }

  function collectChart(days) {
    if (!days.length) return '<div class="empty-state">수집 기록이 없습니다</div>';
    const scoped = days.map(d => { const sc = collectScope(d); return { day: d.day, found: sc.found, newCount: sc.newCount, name: sc.name }; });
    const max = Math.max(1, ...scoped.map(d => d.found));
    const cols = scoped.map(d => {
      const hf = Math.max(2, Math.round(d.found / max * 100));
      const hn = d.newCount ? Math.max(2, Math.round(d.newCount / max * 100)) : 0;
      const label = String(d.day).slice(5);
      const title = d.day + ' ' + d.name + ' — 발견 ' + d.found + ' · 신규 ' + d.newCount;
      return '<div class="collect-day" title="' + esc(title) + '">' +
        '<div class="collect-bars"><span class="collect-bar-new" style="height:' + hn + '%"></span>' +
        '<span class="collect-bar-found" style="height:' + hf + '%"></span></div>' +
        '<span class="collect-label">' + esc(label) + '</span></div>';
    }).join('');
    return '<div class="collect-legend"><span class="legend-item"><span class="legend-swatch sw-found"></span>발견</span> <span class="legend-item"><span class="legend-swatch sw-new"></span>신규</span></div>' +
      '<div class="collect-chart">' + cols + '</div>';
  }

  function kpi(n, l) { return '<div class="kpi"><div class="kpi-value">' + n + '</div><div class="kpi-label">' + l + '</div></div>'; }
  function bar(v, total) {
    const pct = total ? Math.round(v / total * 100) : 0;
    return '<span class="bar-track"><span class="bar-fill" style="width:' + pct + '%"></span></span><span class="bar-count">' + v + '</span>';
  }

  /* ---------- 마크다운 렌더러 (기존 T-142) ---------- */
  function mdInline(s) {
    s = s.replace(/`([^`]+?)`/g, '<code>$1</code>');
    s = s.replace(/\*\*([^*]+?)\*\*/g, '<strong>$1</strong>');
    s = s.replace(/!\[([^\]]*?)\]\((https?:\/\/[^)\s]+?)\)/g, '<a target="_blank" rel="noopener" href="$2">🖼 $1</a>');
    s = s.replace(/\[([^\]]+?)\]\((https?:\/\/[^)\s]+?)\)/g, '<a target="_blank" rel="noopener" href="$2">$1</a>');
    return s;
  }

  function stripHtml(s) { return String(s == null ? '' : s).replace(/<\/?[a-zA-Z][^>\n]*>/g, ''); }

  function md(src) {
    const lines = esc(stripHtml(src)).split('\n');
    let html = '', inCode = false, codeBuf = [], listBuf = [], listTag = '';
    const flushList = () => { if (listBuf.length) { html += '<' + listTag + '>' + listBuf.join('') + '</' + listTag + '>'; listBuf = []; listTag = ''; } };
    lines.forEach(raw => {
      const line = raw.trim();
      if (/^```/.test(line)) { flushList(); if (inCode) { html += '<pre><code>' + codeBuf.join('\n') + '</code></pre>'; codeBuf = []; } inCode = !inCode; return; }
      if (inCode) { codeBuf.push(raw.replace(/^\s+|\s+$/g, '')); return; }
      if (!line) { flushList(); return; }
      const h = line.match(/^(#{1,6})\s+(.*)$/);
      if (h) { flushList(); html += (h[1].length <= 2 ? '<h4>' : '<h5>') + mdInline(h[2]) + (h[1].length <= 2 ? '</h4>' : '</h5>'); return; }
      if (/^(---|\*\*\*|___)\s*$/.test(line)) { flushList(); html += '<hr>'; return; }
      const q = line.match(/^&gt;\s?(.*)$/);
      if (q) { flushList(); html += '<blockquote>' + mdInline(q[1]) + '</blockquote>'; return; }
      const ul = line.match(/^[-*+]\s+(.*)$/);
      if (ul) { if (listTag !== 'ul') flushList(); listTag = 'ul'; listBuf.push('<li>' + mdInline(ul[1].replace(/^\[([ xX])\]\s+/, m => m.toLowerCase() === 'x' ? '☑ ' : '☐ ')) + '</li>'); return; }
      const ol = line.match(/^\d+[.)]\s+(.*)$/);
      if (ol) { if (listTag !== 'ol') flushList(); listTag = 'ol'; listBuf.push('<li>' + mdInline(ol[1]) + '</li>'); return; }
      flushList();
      html += '<p>' + mdInline(line) + '</p>';
    });
    flushList();
    if (inCode && codeBuf.length) html += '<pre><code>' + codeBuf.join('\n') + '</code></pre>';
    return html;
  }

  function stripMd(src) {
    return stripHtml(src)
      .replace(/```[\s\S]*?```/g, ' ')
      .replace(/`([^`]*?)`/g, '$1')
      .replace(/^#{1,6}\s+/gm, '')
      .replace(/\*\*([^*]+?)\*\*/g, '$1')
      .replace(/!\[([^\]]*?)\]\([^)]*?\)/g, '$1')
      .replace(/\[([^\]]+?)\]\([^)]*?\)/g, '$1')
      .replace(/^\s*>\s?/gm, '')
      .replace(/^\s*[-*+]\s+/gm, '')
      .replace(/^\s*\d+[.)]\s+/gm, '')
      .replace(/\s+/g, ' ').trim();
  }

  var mdStore = {}, mdSeq = 0;
  function mdBlock(koText, enText) {
    const show = koText || enText || '';
    if (!show) return '';
    if (koText && enText && koText !== enText) {
      const key = 'm' + (++mdSeq);
      mdStore[key] = { ko: koText, en: enText, showing: 'ko' };
      return '<div class="md-body" data-mdtext data-key="' + key + '">' + md(koText) + '</div>' +
        '<button class="badge" type="button" data-mdkey="' + key + '" style="margin-top:8px;padding:4px 10px;font-size:11px;">원문보기</button>';
    }
    return '<div class="md-body">' + md(show) + '</div>';
  }

  /* ---------- 모달 ---------- */
  function openModal(id) {
    const modal = $('appModal');
    ensureModalTabs();
    $('modalTitle').textContent = '불러오는 중…';
    $('modalIcon').style.display = '';
    $('modalIcon').removeAttribute('src');
    $('modalIcon').alt = '';
    $('modalDev').textContent = '';
    $('modalMetaPills').innerHTML = '';
    $('modalCta').innerHTML = '';
    $('panelIntro').innerHTML = '';
    $('panelFeatures').innerHTML = '';
    $('panelChangelog').innerHTML = '';
    setActiveTab('intro');
    state.modal.currentId = id;
    if (typeof modal.showModal === 'function') modal.showModal();
    // showModal이 첫 포커스 요소(소개 탭)에 포커스를 주어 파란 링이 생기므로 닫기 버튼으로 이동
    if (typeof $('modalClose').focus === 'function') $('modalClose').focus({ preventScroll: true });

    api('/api/apps/' + encodeURIComponent(id)).then(a => {
      renderModal(a);
    }).catch(() => {
      $('modalTitle').textContent = '불러오기 실패';
    });
  }

  function closeModal() {
    const modal = $('appModal');
    if (typeof modal.close === 'function') modal.close();
    state.modal.currentId = null;
  }

  function ensureModalTabs() {
    const tabsBox = $('modalTabs');
    tabsBox.style.display = '';
    if (!$1('[role="tab"]', tabsBox)) {
      tabsBox.innerHTML =
        '<button class="modal-tab" role="tab" aria-selected="true" data-tab="intro" type="button" id="tabIntro">소개</button>' +
        '<button class="modal-tab" role="tab" aria-selected="false" data-tab="features" type="button" id="tabFeatures">특징</button>' +
        '<button class="modal-tab" role="tab" aria-selected="false" data-tab="changelog" type="button" id="tabChangelog">새 기능</button>';
      $$('[role="tab"]', tabsBox).forEach(btn => {
        btn.onclick = () => setActiveTab(btn.dataset.tab);
      });
    }
  }

  function setActiveTab(tab) {
    state.modal.activeTab = tab;
    $$('#modalTabs [role="tab"]').forEach(btn => {
      const isActive = btn.dataset.tab === tab;
      btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
      btn.classList.toggle('active', isActive);
    });
    $$('#modalPanels [role="tabpanel"]').forEach(panel => {
      const isActive = panel.id === 'panel' + tab.charAt(0).toUpperCase() + tab.slice(1);
      panel.classList.toggle('active', isActive);
      panel.hidden = !isActive;
    });
  }

  function renderModal(a) {
    $('modalTitle').textContent = a.name || '앱 상세';
    // 아이콘 없으면 img 자체를 숨김 (빈 src 깨짐 방지 — img는 void 요소라 텍스트 폴백 불가)
    if (a.iconUrl) { $('modalIcon').style.display = ''; $('modalIcon').src = a.iconUrl; $('modalIcon').alt = ''; }
    else { $('modalIcon').style.display = 'none'; $('modalIcon').removeAttribute('src'); $('modalIcon').alt = ''; }
    $('modalDev').textContent = a.developer || '';

    const pills = [];
    if (a.primaryLanguage) pills.push('<span class="pill lang" style="--dot-color:' + (LANG_COLORS[a.primaryLanguage.toLowerCase()] || LANG_COLORS.default) + '">' + esc(a.primaryLanguage) + '</span>');
    if (a.license) pills.push('<span class="pill ' + LICENSE_CLASS[a.license] + '">' + esc(LICENSE_LABEL[a.license] || a.license) + '</span>');
    if (a.stars != null) pills.push('<span class="pill stars">★ ' + a.stars + '</span>');
    if (a.isNew) pills.push('<span class="pill new">NEW</span>');
    $('modalMetaPills').innerHTML = pills.join('');

    const cta = [];
    if (a.homepageUrl) cta.push('<a class="btn-primary" href="' + esc(a.homepageUrl) + '" target="_blank" rel="noopener">홈페이지</a>');
    if (a.repoFullName) cta.push('<a class="btn-secondary" href="https://github.com/' + esc(a.repoFullName) + '" target="_blank" rel="noopener">GitHub Repo</a>');
    else if (a.sourceUrl) cta.push('<a class="btn-secondary" href="' + esc(a.sourceUrl) + '" target="_blank" rel="noopener">출처 보기</a>');
    if (a.trackId) cta.push('<a class="btn-secondary" href="https://apps.apple.com/us/app/id' + a.trackId + '" target="_blank" rel="noopener">App Store</a>');
    $('modalCta').innerHTML = cta.join('');

    // ① 소개
    const marker = '— README —';
    function splitBody(full) { if (!full) return ''; if (full.indexOf(marker) >= 0) { const p = full.split(marker); return p.slice(1).join(marker).trim() || p[0].trim(); } return full; }
    function excerpt(full) { if (!full) return ''; if (full.indexOf(marker) >= 0) return full.split(marker)[0].trim().slice(0, 300); return full.length > 300 ? full.slice(0, 300) + '…' : full; }
    const koFull = a.descriptionKo || '';
    const enFull = a.descriptionSnippet || '';
    const introKo = excerpt(koFull);
    const introEn = excerpt(enFull);
    const funcKo = splitBody(koFull);
    const funcEn = splitBody(enFull);
    const funcShow = funcKo || funcEn;
    const introHtml = (introKo || introEn) ? mdBlock(introKo, introEn) : '<p style="color:var(--secondary);">소개 정보가 없습니다.</p>';
    const funcHtml = (funcShow && funcShow !== (introKo || introEn)) ? mdBlock(funcKo, funcEn) : '';

    // ③ 특징
    const feats = [];
    if (a.sellerName) feats.push('<div class="feature-item">판매: ' + esc(a.sellerName) + '</div>');
    if (a.averageRating != null) feats.push('<div class="feature-item">평점 ' + a.averageRating + (a.ratingCount != null ? ' (' + a.ratingCount + '개)' : '') + '</div>');
    if (a.stars != null) feats.push('<div class="feature-item">★ ' + a.stars + (a.forks != null ? ' · ⑂ ' + a.forks : '') + (a.issues != null ? ' · 이슈 ' + a.issues : '') + '</div>');
    if (a.primaryLanguage) feats.push('<div class="feature-item">' + esc(a.primaryLanguage) + '</div>');
    if (a.licenseName) feats.push('<div class="feature-item">라이선스: ' + esc(a.licenseName) + '</div>');
    if (a.fileSize) feats.push('<div class="feature-item">용량: ' + fmtSize(a.fileSize) + '</div>');
    if (a.minOs) feats.push('<div class="feature-item">최소 OS: ' + esc(a.minOs) + '</div>');
    if (a.contentRating) feats.push('<div class="feature-item">연령 등급: ' + esc(a.contentRating) + '</div>');
    if (a.category) feats.push('<div class="feature-item">' + esc(a.category) + '</div>');
    if (a.tags) feats.push('<div class="feature-item">태그: ' + esc(a.tags) + '</div>');
    const featHtml = feats.length ? '<div class="features-grid">' + feats.join('') + '</div>' : '<p style="color:var(--secondary);">특징 정보가 없습니다.</p>';

    // ⑤ 새 기능
    const newsPick = pick(a.releaseNotesKo, a.releaseNotes || a.releaseNotesSummary);
    let newsHtml = '';
    const hasHistory = a.version || (a.versions && a.versions.length) || newsPick.text;
    if (hasHistory) {
      const rows = (a.versions || []).slice(0, 10).map(v => {
        const vlink = v.sourceUrl ? ' <a target="_blank" rel="noopener" href="' + esc(v.sourceUrl) + '">열기</a>' : '';
        return '<tr><td>' + esc(v.version) + '</td><td class="notes">' + md(v.notesSummary || '') + '</td><td class="link-cell">' + vlink + '</td></tr>';
      }).join('');
      const newsKo = newsPick.isKo ? newsPick.text : '';
      const newsEn = newsPick.isKo ? (a.releaseNotes || a.releaseNotesSummary || '') : newsPick.text;
      newsHtml = (newsPick.text ? mdBlock(newsKo, newsEn) : '') +
        '<table class="version-table"><tr><th>버전</th><th>새 기능</th><th>링크</th></tr>' +
        (a.version ? '<tr><td class="version-current"><b>' + esc(a.version) + '</b> (현재)' + (a.prevVersion ? ' ← ' + esc(a.prevVersion) + ' 화' : '') + '</td><td class="notes">' + md(a.releaseNotesSummary || '') + '</td><td></td></tr>' : '') + rows + '</table>';
    } else {
      newsHtml = '<p style="color:var(--secondary);">버전 기록 없음 — ' + fmtDate(a.firstSeenAt) + ' 첫 포착, 다음 업데이트부터 기록됩니다.</p>';
    }

    $('panelIntro').innerHTML = '<div class="panel-section"><h4 class="panel-section-title">소개</h4><div class="panel-text">' + introHtml + '</div></div>' +
      (funcHtml ? '<div class="panel-section"><h4 class="panel-section-title">세부 설명</h4><div class="panel-text">' + funcHtml + '</div></div>' : '');

    $('panelFeatures').innerHTML = '<div class="panel-section"><h4 class="panel-section-title">특징</h4>' + featHtml + '</div>' +
      (a.screenshotUrls ? '<div class="panel-section"><h4 class="panel-section-title">스크린샷</h4><div class="shot-row">' + a.screenshotUrls.split(/\r?\n/).map(s => s.trim()).filter(Boolean).map(u => '<img src="' + esc(u) + '" alt="스크린샷" loading="lazy">').join('') + '</div></div>' : '');

    $('panelChangelog').innerHTML = '<div class="panel-section"><h4 class="panel-section-title">새 기능</h4>' + newsHtml + '</div>';
  }

  function initModalTabs() {
    ensureModalTabs();
    $$('#modalTabs [role="tab"]').forEach(btn => {
      btn.onclick = () => setActiveTab(btn.dataset.tab);
    });
    $('modalClose').onclick = closeModal;
    $('appModal').addEventListener('click', e => { if (e.target === $('appModal')) closeModal(); });
    document.addEventListener('keydown', e => { if (e.key === 'Escape' && !$('appModal').hidden) closeModal(); });

    document.addEventListener('click', e => {
      const btn = e.target.closest('[data-mdkey]');
      if (!btn) return;
      const t = mdStore[btn.dataset.mdkey];
      if (!t) return;
      const showingKo = t.showing === 'ko';
      t.showing = showingKo ? 'en' : 'ko';
      const box = btn.parentElement.querySelector('[data-mdtext][data-key="' + btn.dataset.mdkey + '"]');
      if (box) box.innerHTML = md(showingKo ? t.en : t.ko);
      btn.textContent = showingKo ? '한국어보기' : '원문보기';
    });
  }

  /* ---------- 헤더 ---------- */
  function loadHeader() {
    api('/api/stats').then(s => {
      $('totalCount') && ($('totalCount').textContent = '총 ' + s.totalApps + '개');
      if (s.lastCollectedAt) {
        const el = $('lastUpdated');
        if (el) el.textContent = '마지막 업데이트: ' + new Date(s.lastCollectedAt).toLocaleString();
      }
      loadNavCounts();
    }).catch(() => {});
    api('/api/stats/trends').then(t => {
      renderSourceFilters(t.bySource || []);
    }).catch(() => {
      $('sourceFilters').innerHTML = '<div class="empty-state" style="padding:12px 0;font-size:12px;">불러오기 실패</div>';
    });
  }

  /* ---------- 헤더 액션 ---------- */
  function bindHeaderActions() {
    // 로고 클릭 = 전체 초기화
    const logoLink = $('sidebar').querySelector('.sidebar-logo') || $('top-bar-title');
    if (logoLink) logoLink.onclick = () => {
      state.filters = { sources: [], allSources: state.filters.allSources, licenseType: '', category: '', q: '', sort: 'newest', page: 1, watchMode: 'updated' };
      $$('#sourceFilters input[type="checkbox"]').forEach(cb => cb.checked = true);
      $$('#typeFilters input[type="radio"]')[0].checked = true;
      $$('#categorySubnav input[type="radio"]')[0].checked = true;
      $('globalSearch').value = '';
      $('sortSelect').value = 'newest';
      switchView('timeline');
    };

    // 수집 버튼 (헤더에 없으므로 stats에서 처리)
    // 번역 버튼 (헤더에 없으므로 stats에서 처리)

    // 알림
    $('notifBell').onclick = () => { notifPage = 1; openNotifCenter(); };
  }

  /* ---------- 알림 센터 (기존 유지) ---------- */
  var notifPage = 1;
  const NOTIF_PAGE_SIZE = 20;
  function openNotifCenter() {
    const modal = $('appModal');
    $('modalTitle').textContent = '알림 센터';
    $('modalIcon').style.display = 'none';
    $('modalIcon').removeAttribute('src');
    $('modalDev').textContent = '';
    $('modalMetaPills').innerHTML = '';
    $('modalCta').innerHTML = '';
    $('modalTabs').style.display = 'none';
    $('panelIntro').innerHTML = '<div class="empty-state">불러오는 중…</div>';
    $('panelFeatures').hidden = true;
    $('panelChangelog').hidden = true;
    setActiveTab('intro');
    if (typeof modal.showModal === 'function') modal.showModal();
    if (typeof $('modalClose').focus === 'function') $('modalClose').focus({ preventScroll: true });
    api('/api/notifications?page=' + notifPage + '&pageSize=' + NOTIF_PAGE_SIZE).then(d => renderNotifList(d)).catch(() => { $('panelIntro').innerHTML = '<div class="empty-state">불러오기 실패</div>'; });
  }
  function renderNotifList(d) {
    if (!d.notifications.length) { $('panelIntro').innerHTML = '<div class="empty-state">알림이 없습니다</div>'; return; }
    let html = d.notifications.map(n => '<section class="detail-sec" data-notif="' + n.id + '" style="cursor:pointer;padding:12px;border-bottom:1px solid var(--border)"><h3 style="font-size:14px;margin:0 0 4px;">' + esc(n.type) + '</h3><p style="font-size:13px;color:var(--secondary);margin:0;">' + esc(n.summary) + '</p><small style="color:var(--secondary);">' + fmtDate(n.createdAt) + '</small></section>').join('');
    const pages = Math.max(1, Math.ceil((d.total || 0) / (d.pageSize || NOTIF_PAGE_SIZE)));
    if (pages > 1) {
      html += '<div class="pagination"><button type="button" id="notifPrev"' + (notifPage <= 1 ? ' disabled' : '') + '>‹ 이전</button>' +
        '<span> ' + notifPage + ' / ' + pages + ' </span>' +
        '<button type="button" id="notifNext"' + (notifPage >= pages ? ' disabled' : '') + '>다음 ›</button></div>';
    }
    $('panelIntro').innerHTML = html;
    const prev = $('notifPrev'), next = $('notifNext');
    if (prev) prev.onclick = () => { if (notifPage > 1) { notifPage--; openNotifCenter(); } };
    if (next) next.onclick = () => { notifPage++; openNotifCenter(); };
    $$('[data-notif]', $('panelIntro')).forEach(el => {
      el.onclick = () => {
        api('/api/notifications/' + el.dataset.notif).then(dd => {
          const t = dd.detail || {};
          let timeHtml = '';
          if (t.startedAt && t.finishedAt) {
            timeHtml = '<p><small>수집 시작 ' + fmtDateTime(t.startedAt) + ' → 완료 ' + fmtDateTime(t.finishedAt) +
              ' (소요 ' + fmtDuration((t.finishedAt - t.startedAt) / 1000) + ')</small></p>';
          } else { timeHtml = '<p><small>발견 시각 ' + fmtDateTime(dd.notification.createdAt) + '</small></p>'; }
          el.innerHTML = '<h3 style="font-size:14px;margin:0 0 4px;">' + esc(dd.notification.type) + '</h3><p style="font-size:13px;color:var(--secondary);margin:0;">' + esc(dd.notification.summary) + '</p>' + timeHtml;
        });
      };
    });
  }

  /* ---------- 언어 토글 ---------- */
  function bindLangToggle() {
    $('langToggle').onclick = () => {
      state.lang = state.lang === 'ko' ? 'en' : 'ko';
      $('langToggle').textContent = state.lang === 'ko' ? '한국어' : '원문';
      reloadCurrentView();
      if (state.modal.currentId) openModal(state.modal.currentId);
    };
  }

  /* ---------- 초기화 ---------- */
  function init() {
    initSidebar();
    bindSearchSort();
    initModalTabs();
    bindHeaderActions();
    bindLangToggle();
    loadHeader();
    loadTimeline();

    // 디버그 로그
    console.log('[INFO] [FEATURE] 맥줍줍-리디자인 로드 완료');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();