/* 맥줍줍 포털 — 목업 기준 다크 테마 (R33 전면 재작성, 바닐라 JS) */
(function () {
  'use strict';

  /* 기존 앱 카테고리 10종 유지 (절대 삭제 금지) */
  const CATEGORIES = ['생산성', '유틸리티', '보안·프라이버시', '미디어·엔터', '개발',
    '디자인·크리에이티브', '금융', '글쓰기·노트', '시스템최적화', '커뮤니케이션'];

  const NEWS_MAINS = [
    { id: 'mac', label: '맥 소식', icon: '🍎' },
    { id: 'ai', label: 'AI 소식', icon: '✦' },
    { id: 'sec', label: '보안 소식', icon: '🛡' }
  ];
  const NEWS_SUBS = {
    mac: ['전체', 'macOS', 'Apple Silicon', '앱·업데이트', '루머', '팁'],
    ai: ['전체', '모델 출시', '연구/논문', '도구/서비스', '비즈니스', '정책'],
    sec: ['전체', '취약점/CVE', '랜섬웨어', '개인정보', '국내', 'Apple보안']
  };
  /* 목업 11종 바로가기 → 앱 스토어 매핑 (params: license/category/sort/newOnly/updatedOnly) */
  const APP_CHIPS = [
    { label: '전체', params: {} },
    { label: '신규 등록', params: { newOnly: true, sort: 'newest' } },
    { label: '무료', params: { license: 'FREE' } },
    { label: '유료', params: { license: 'PAID' } },
    { label: '업데이트', params: { updatedOnly: true, sort: 'updated' } },
    { label: '생산성', params: { category: '생산성' } },
    { label: '개발자 도구', params: { category: '개발' } },
    { label: '유틸리티', params: { category: '유틸리티' } },
    { label: '크리에이티브', params: { category: '디자인·크리에이티브' } },
    { label: '라이프스타일', params: { category: '미디어·엔터' }, approx: true },
    { label: '세일중', params: { license: 'PAID', sort: 'updated' }, approx: true }
  ];
  /* 좌측 필터 사이드바 옵션 */
  const CAT_CHOICES = [
    { label: '전체', cat: '' }, { label: '생산성', cat: '생산성' }, { label: '개발자 도구', cat: '개발' },
    { label: '유틸리티', cat: '유틸리티' }, { label: '크리에이티브', cat: '디자인·크리에이티브' },
    { label: '라이프스타일', cat: '미디어·엔터' }
  ];
  const PRICE_CHOICES = [
    { label: '전체', license: '' }, { label: '무료', license: 'FREE' },
    { label: '유료', license: 'PAID' }, { label: '세일중', license: 'PAID', sale: true }
  ];
  const SORT_CHOICES = [
    { label: '인기순', sort: 'stars' }, { label: '최신순', sort: 'newest' },
    { label: '가격낮은순', sort: 'priceAsc' }, { label: '업데이트순', sort: 'updated' },
    { label: 'MAS 우선', sort: 'mas' }
  ];
  const MAIN_FILTERS = [
    { label: '전체', main: '', category: '' },
    { label: '맥', main: 'mac', category: '' },
    { label: 'AI', main: 'ai', category: '' },
    { label: '보안', main: 'sec', category: '' },
    { label: '생산성', main: '', category: '생산성' },
    { label: '개발자도구', main: '', category: '개발' },
    { label: '유틸리티', main: '', category: '유틸리티' },
    { label: '크리에이티브', main: '', category: '디자인·크리에이티브' }
  ];
  const ICON_BG = ['linear-gradient(135deg,#f97316,#ef4444)', 'linear-gradient(135deg,#3b82f6,#06b6d4)',
    'linear-gradient(135deg,#8b5cf6,#d946ef)', 'linear-gradient(135deg,#18181b,#3f3f46)',
    'linear-gradient(135deg,#22c55e,#15803d)', 'linear-gradient(135deg,#eab308,#f97316)'];

  const PAGE_SIZE = 20;
  const NEWS_PAGE_SIZE = 5;

  const NEWS_LAYOUT_KEY = 'macjupjup_news_layout';
  const state = {
    view: 'dashboard',
    storeSub: 'timeline',
    filters: { license: '', cat: '', q: '', sort: 'stars', page: 1, newOnly: false, updatedOnly: false },
    news: {
      main: 'mac', sub: '', page: 1, q: '', detailId: null, topTab: 'news',
      listIds: [], total: 0,
      layout: (function () { try { return localStorage.getItem(NEWS_LAYOUT_KEY) === 'B' ? 'B' : 'A'; } catch (e) { return 'A'; } })()
    },
    mainFilter: { main: '', category: '' },
    dashPage: 1,
    lang: 'ko',
    modal: { currentId: null, activeTab: 'intro' }
  };

  const $ = (id) => document.getElementById(id);
  const $$ = (sel, ctx = document) => ctx.querySelectorAll(sel);
  const $1 = (sel, ctx = document) => ctx.querySelector(sel);

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
  }
  function fmtDate(ts) {
    if (!ts) return '-';
    const d = new Date(ts);
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }
  /* ---------- 마크다운 렌더러 ---------- */
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
  const README_MARKER = '— README —';
  function splitBody(full) {
    if (!full) return '';
    const i = full.indexOf(README_MARKER);
    if (i >= 0) return full.slice(i + README_MARKER.length).trim() || full.slice(0, i).trim();
    return full;
  }
  function excerpt(full) {
    if (!full) return '';
    const i = full.indexOf(README_MARKER);
    if (i >= 0) return full.slice(0, i).trim();
    return full.trim();
  }
  function mdBlock(koText, enText) {
    const show = koText || enText || '';
    if (!show) return '';
    if (koText && enText && koText !== enText) {
      const key = 'm' + (++mdSeq);
      mdStore[key] = { ko: koText, en: enText, showing: state.lang };
      const first = state.lang === 'ko' ? koText : enText;
      return '<div class="md-body" data-mdtext data-key="' + key + '">' + md(first) + '</div>' +
        '<button class="badge" type="button" data-mdkey="' + key + '" style="margin-top:8px;padding:4px 10px;font-size:11px;">' + (state.lang === 'ko' ? '원문보기' : '번역 보기') + '</button>';
    }
    return '<div class="md-body">' + md(show) + '</div>';
  }
  /* ---------- 뉴스 본문 단락화 (단일 <p> 한줄 표시 방지) ---------- */
  function paraText(t) {
    var norm = String(t == null ? '' : t).replace(/\s+/g, ' ').trim();
    if (!norm) return '<p>본문이 없습니다. 원문에서 확인해주세요.</p>';
    var sens = norm.match(/[^.!?。！？]+[.!?。！？]+["'”’)]?\s*/g) || [norm];
    var parts = [];
    if (sens.length <= 1 && norm.length > 600) {
      var start = 0;
      while (start < norm.length) {
        var end = Math.min(start + 600, norm.length);
        if (end < norm.length) { var sp = norm.lastIndexOf(' ', end); if (sp > start + 100) end = sp; }
        parts.push(norm.slice(start, end).trim());
        start = end;
      }
    } else {
      var buf = '', cnt = 0;
      sens.forEach(function (s) {
        buf += (buf ? ' ' : '') + s.trim();
        cnt++;
        if (cnt >= 3 || buf.length >= 600) { parts.push(buf); buf = ''; cnt = 0; }
      });
      if (buf) parts.push(buf);
    }
    return parts.map(function (p) { return '<p>' + esc(p) + '</p>'; }).join('');
  }
  /* ---------- 뉴스 한/원문 표시 (R41: titleKo/summaryKo) ---------- */
  function newsTitle(n) { return (state.lang === 'ko' && n.titleKo) ? n.titleKo : (n.title || ''); }
  function newsSummary(n) {
    if (state.lang === 'ko' && n.summaryKo) return n.summaryKo;
    return n.summary || '';
  }
  function formatNewsBody(html) {
    if (!html) return '<p>본문이 없습니다. 원문에서 확인해주세요.</p>';
    if (!/<(p|h[1-6]|ul|ol|blockquote|pre|figure|img)\b/i.test(html)) return paraText(html);
    try {
      var div = document.createElement('div');
      div.innerHTML = html;
      var ps = div.querySelectorAll('p');
      var others = div.querySelectorAll('h1,h2,h3,h4,h5,h6,ul,ol,blockquote,pre,figure,img');
      if (ps.length === 1 && others.length === 0) {
        var txt = ps[0].textContent || '';
        if (txt.length > 800 && txt.indexOf('\n') < 0) return paraText(txt);
      }
      div.querySelectorAll('img').forEach(function (img) {
        img.setAttribute('referrerpolicy', 'no-referrer');
        img.setAttribute('loading', 'lazy');
      });
      return div.innerHTML;
    } catch (e) { return html; }
  }
  function fmtTime(ts) {
    if (!ts) return '';
    const diff = Date.now() - ts;
    const m = Math.floor(diff / 60000);
    if (m < 1) return '방금 전';
    if (m < 60) return m + '분 전';
    const h = Math.floor(m / 60);
    if (h < 24) return h + '시간 전';
    return Math.floor(h / 24) + '일 전';
  }
  function api(path) {
    return fetch(path).then(r => {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
  }
  function apiPost(path, body) {
    return fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body === undefined ? {} : body)
    }).then(r => {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
  }
  function apiDel(path) {
    return fetch(path, { method: 'DELETE' }).then(r => {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
  }
  /* ---------- R44 관리 토큰 + R48 통합 ID/PW: 쓰기 API 자동 첨부 + 401 시 입력·재시도 ---------- */
  (function () {
    const KEY = 'jupjup_admin_token_3010';
    const KEY_ID = 'jupjup_admin_id';
    const KEY_PW = 'jupjup_admin_pw';
    const PAIR_URL = 'http://127.0.0.1:3010/api/admin/token';
    function stored(k) { try { return localStorage.getItem(k) || ''; } catch (e) { return ''; } }
    function attach(headers) {
      headers['X-Auth-Token'] = stored(KEY);
      headers['X-Admin-Id'] = stored(KEY_ID);
      headers['X-Admin-Pw'] = stored(KEY_PW);
    }
    const origFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      const url = typeof input === 'string' ? input : (input && input.url) || '';
      const method = ((init && init.method) || (input && input.method) || 'GET').toUpperCase();
      if (url.indexOf('/api/') !== 0 || method === 'GET' || method === 'HEAD') return origFetch(input, init);
      const headers = {};
      if (init && init.headers) {
        if (init.headers.forEach) init.headers.forEach((v, k) => { headers[k] = v; });
        else for (const k in init.headers) headers[k] = init.headers[k];
      }
      attach(headers);
      const patched = Object.assign({}, init || {}, { method, headers });
      return origFetch(input, patched).then(r => {
        if (r.status !== 401) return r;
        const i = prompt('관리자 ID (토큰 사용 시 비워두기, 발급: ' + PAIR_URL + ')', stored(KEY_ID));
        if (i === null) return r;
        const v = prompt('관리자 PW (토큰 사용 시 토큰 입력)');
        if (v === null) return r;
        try {
          if ((i || '').trim()) {
            localStorage.setItem(KEY_ID, i.trim());
            localStorage.setItem(KEY_PW, (v || '').trim());
          } else {
            localStorage.setItem(KEY, (v || '').trim());
            localStorage.setItem(KEY_PW, '');
          }
        } catch (e) {}
        attach(patched.headers);
        return origFetch(input, patched);
      });
    };
  })();
  function toast(msg) {
    const t = $('toast');
    t.textContent = msg;
    t.hidden = false;
    t.classList.add('show');
    setTimeout(() => { t.classList.remove('show'); setTimeout(() => t.hidden = true, 200); }, 2200);
  }
  function iconBg(name) {
    let h = 0;
    for (const c of String(name || '?')) h = (h * 31 + c.charCodeAt(0)) % 997;
    return ICON_BG[h % ICON_BG.length];
  }
  function appIcon(a, cls) {
    if (a.iconUrl) return '<img class="' + cls + '" src="' + esc(a.iconUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">';
    const ch = (a.name || '?').trim().charAt(0).toUpperCase();
    return '<span class="' + cls + '" style="background:' + iconBg(a.name) + ';display:flex;align-items:center;justify-content:center;color:#fff;" aria-hidden="true">' + esc(ch) + '</span>';
  }
  function priceText(a) {
    if (a.license === 'FREE' || a.price === 0) return '<span class="hprice free">무료</span>';
    if (a.price > 0) return '<span class="hprice">₩' + Number(a.price).toLocaleString() + '</span>';
    if (a.license === 'PAID') return '<span class="hprice">유료</span>';
    return '<span class="hprice free">무료</span>';
  }
  /* 버전 표시 (비정상 값은 빈값) */
  function verText(a) {
    const v = (a.version || '').trim();
    if (/^[vV]?\d/.test(v)) return 'v' + v.replace(/^[vV]/, '');
    return '';
  }
  /* 제목에서 키워드 추출 (인기 태그용): 영문 4자+·숫자 포함·한글 3자+, 한/영 병합 (R50) */
  const TAG_STOP = new Set(['THE', 'AND', 'FOR', 'WITH', 'FROM', 'NEW', 'APP', 'MAC', 'PRO', 'YOU', 'YOUR',
    'WILL', 'NEXT', 'COME', 'USING', 'MAKE', 'SHOULD', 'GUIDE', 'BUYER', 'UPGRADE', 'RELEASE',
    'UPDATE', 'MONDAY', 'STARTING', 'BREAKOUT', 'STARTUP', 'ACCOUNTANT', 'BLOCKED', 'SAYS', 'SAY', 'GET',
    'BETTER', 'AFTER', 'ABOUT', 'INTO', 'OVER', 'UNDER', 'BETWEEN', 'BEFORE', 'WHILE', 'THIS', 'THAT',
    'HAVE', 'HAS', 'HOW', 'WHAT', 'WHY', 'WHO', 'WHEN', 'WHERE', 'CAN', 'NOT', 'ARE', 'WAS', 'WERE',
    'OUR', 'THEIR', 'THEM', 'THESE', 'THOSE', 'ITS', 'IT\'S', 'DON\'T', 'DOESN\'T', 'ISN\'T',
    '애플', '사용자', '지원', '가능', '공개', '발표', '출시', '업데이트', '새로', '통해', '위해',
    '그리고', '하지만', '이번', '최근', '통해', '관련', '대한', '위한', '있는', '없는', '있다']);
  const TAG_KEEP_SHORT = new Set(['Arc', 'M3', 'M4', 'M5', 'iOS', 'macOS', 'Safari', 'Xcode', 'Swift']);
  function extractTags(items) {
    const freq = {};
    items.forEach(n => {
      const sources = [n.title, n.titleKo].filter(Boolean);
      const words = sources.join(' ').match(/[A-Za-z][A-Za-z0-9+_.-]{1,}|[가-힣]{2,}/g) || [];
      const seen = new Set();
      words.forEach(w => {
        if (/[가-힣]/.test(w)) {
          if (w.length < 3 || seen.has(w)) return;
          if (TAG_STOP.has(w)) return;
          seen.add(w);
          freq[w] = (freq[w] || 0) + 1;
          return;
        }
        const up = w.toUpperCase();
        if (TAG_STOP.has(up) || seen.has(w)) return;
        if (!/[A-Z0-9]/.test(w)) return;
        if (w.length < 4 && !/\d/.test(w) && !TAG_KEEP_SHORT.has(w) && !TAG_KEEP_SHORT.has(w.toLowerCase())) return;
        seen.add(w);
        // 브랜드/제품형 토큰(내부 대문자·숫자 포함) 가중
        const brandish = /[a-z][A-Z]/.test(w) || /\d/.test(w) || TAG_KEEP_SHORT.has(w);
        freq[w] = (freq[w] || 0) + (brandish ? 2 : 1);
      });
    });
    return Object.keys(freq).sort((a, b) => freq[b] - freq[a]).slice(0, 10).map(k => ({ tag: k, count: freq[k] }));
  }

  /* ---------- 북마크 (로컬, R33) ---------- */
  const BM_KEY = 'macjupjup_bm';
  function getBm() {
    try { return JSON.parse(localStorage.getItem(BM_KEY) || '{}'); }
    catch (e) { return {}; }
  }
  function setBm(o) { try { localStorage.setItem(BM_KEY, JSON.stringify(o)); } catch (e) {} }
  function isBm(id) { return !!getBm()[id]; }
  function toggleBm(n) {
    const bm = getBm();
    if (bm[n.id]) { delete bm[n.id]; toast('북마크 해제'); }
    else {
      bm[n.id] = { id: n.id, title: n.title, titleKo: n.titleKo || null, summaryKo: n.summaryKo || null, sourceName: n.sourceName, main: n.main, sub: n.sub, publishedAt: n.publishedAt, thumbnailUrl: n.thumbnailUrl || null };
      toast('북마크 저장');
    }
    setBm(bm);
    updateBmCount();
    return !!bm[n.id];
  }
  function updateBmCount() {
    const c = Object.keys(getBm()).length;
    $('topBmCount').textContent = c;
  }

  /* ---------- 뷰 전환 ---------- */
  function switchView(view) {
    state.view = view;
    $$('#mainNav .navpill, #mobileNav .navpill').forEach(b => b.classList.toggle('active', b.dataset.view === view));
    ['dashboard', 'appstore', 'news'].forEach(v => {
      const el = v === 'dashboard' ? $('view-dashboard') : (v === 'appstore' ? $('view-appstore') : $('view-news'));
      const on = v === view;
      el.classList.toggle('active', on);
      el.hidden = !on;
    });
    if (view === 'dashboard') loadDashboard();
    else if (view === 'appstore') loadStore();
    else { applyNewsLayout(); loadNews(); }
    window.scrollTo(0, 0);
  }

  /* ================= 대시보드 ================= */
  let dashSeq = 0;
  function loadDashboard() {
    const seq = ++dashSeq;
    renderMainFilters();
    Promise.all([
      api('/api/main').catch(e => { console.error('[대시보드] /api/main 실패', e); return null; }),
      api('/api/watchlist').catch(e => { console.error('[대시보드] 소스 상태 실패', e); return []; }),
      api('/api/stats/trends').catch(e => { console.error('[대시보드] 트렌드 실패', e); return null; })
    ]).then(([main, sources, trends]) => {
      if (seq !== dashSeq) return;
      if (main) {
        renderDashboard(main, sources || [], trends);
        setNavCounts({ news: ((main.counts || {}).mac || 0) + ((main.counts || {}).ai || 0) + ((main.counts || {}).sec || 0) });
      }
      else $('dashHeroStats').textContent = '불러오기 실패';
    });
  }

  function renderMainFilters() {
    $('mainFilterChips').innerHTML = MAIN_FILTERS.map((f, i) =>
      '<button type="button" class="chip' + (state.mainFilter.main === f.main && state.mainFilter.category === f.category ? ' active' : '') +
      '" data-i="' + i + '">' + esc(f.label) + '</button>'
    ).join('') + '<span class="chipnote">필터는 아래 섹션에 동시 적용</span>';
    $$('#mainFilterChips .chip').forEach(b => {
      b.onclick = () => {
        const f = MAIN_FILTERS[parseInt(b.dataset.i, 10)];
        state.mainFilter = { main: f.main, category: f.category };
        state.dashPage = 1;
        renderMainFilters();
        loadDashboardGroups();
      };
    });
  }

  let dashCache = null;
  function renderDashboard(d, sources, trends) {
    dashCache = { main: d, sources, trends };
    const c = d.counts || {};
    const totalNews = (c.mac || 0) + (c.ai || 0) + (c.sec || 0);
    $('dashHeroStats').innerHTML = '오늘 수집된 앱 <b>' + (d.totalApps || 0) + '개</b> · 업데이트 <b>' +
      (d.updatedApps ? d.updatedApps.length : 0) + '개</b> · 뉴스 <b>' + totalNews + '개</b> · 맥 ' + (c.mac || 0) +
      ' / AI ' + (c.ai || 0) + ' / 보안 ' + (c.sec || 0);
    $('dashUpdatedAt').textContent = d.generatedAt ? ('마지막 업데이트: ' + new Date(d.generatedAt).toLocaleString('ko-KR')) : '';

    // 하이라이트 3카드: 최신 맥뉴스 / 최신 앱 / 최신 보안뉴스
    const topMac = (d.mac || [])[0], topApp = (d.updatedApps || [])[0], topSec = (d.sec || [])[0];
    let hl = '';
    if (topMac) {
      hl += '<article class="hl-card"><div>' +
        (topMac.thumbnailUrl ? '<img class="hl-thumb" src="' + esc(topMac.thumbnailUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' : '') +
        '</div><div class="hl-body"><div class="hl-top"><span class="hot">HOT</span><span>' +
        esc(topMac.sourceName) + ' · ' + fmtTime(topMac.publishedAt) + '</span></div>' +
        '<h3 class="hl-title">' + esc(topMac.title) + '</h3>' +
        '<p class="hl-desc">' + esc(topMac.summary || '') + '</p></div></article>';
    }
    if (topApp) {
      hl += '<article class="hl-card glow"><div class="hl-body"><div class="hl-top">' +
        appIcon(topApp, 'hl-icon') +
        '<b style="font-size:14px;color:var(--text)">' + esc(topApp.name) + '</b>' +
        (topApp.isNew ? '<span class="newpill">NEW</span>' : '') + '</div>' +
        '<div class="hl-top">' + esc([verText(topApp), topApp.category].filter(Boolean).join(' · ')) + '</div>' +
        '<p class="hl-desc">' + esc(topApp.category || '') + ' 앱 최신 소식을 확인하세요</p></div>' +
        '<span class="hl-price">' + (topApp.license === 'FREE' ? '무료' : '유료') + '</span></article>';
    }
    if (topSec) {
      hl += '<article class="hl-card"><div class="hl-body"><div class="hl-top">⚠️ <b style="color:var(--text)">보안 긴급</b><span> · ' +
        fmtTime(topSec.publishedAt) + '</span></div>' +
        '<h3 class="hl-title">' + esc(topSec.title) + '</h3>' +
        '<p class="hl-desc">' + esc(topSec.summary || '') + '</p>' +
        '<a class="hl-cta" href="' + esc(topSec.originalUrl) + '" target="_blank" rel="noopener">즉시 업데이트</a>' +
        '<div class="hl-src">' + esc(topSec.sourceName) + '</div></div></article>';
    }
    $('dashHighlights').innerHTML = hl || '<div class="empty-state">하이라이트 없음</div>';

    renderDashApps(d.updatedApps || []);
    loadDashboardGroups();
    renderSidebar(sources, trends, d);
  }

  function renderDashApps(apps) {
    const f = state.mainFilter;
    const list = f.category ? apps.filter(a => a.category === f.category) : apps;
    $('dashAppCount').textContent = list.length + '개';
    $('dashApps').innerHTML = list.map(a => {
      const ver = [verText(a), fmtTime(a.lastUpdatedAt)].filter(Boolean).join(' · ');
      return '<button type="button" class="hcard" data-id="' + esc(a.id) + '" role="listitem">' +
      '<div class="hcard-top">' + appIcon(a, 'hicon') +
      '<span class="catpill">' + esc(a.category || '') + '</span></div>' +
      '<b>' + esc(a.name) + '</b><div class="ver">' + esc(ver) + '</div>' +
      priceText(a) + '</button>';
    }).join('') || '<div class="empty-state">앱 없음</div>';
    $$('#dashApps .hcard').forEach(el => { el.onclick = () => openModal(el.dataset.id); });
  }

  function loadDashboardGroups() {
    if (!dashCache) return;
    const d = dashCache.main;
    const f = state.mainFilter;
    // R49: 대시보드 뉴스 행에도 관련앱 병합 (/api/main relatedApps)
    const withRel = (arr) => arr || [];
    const groups = [
      { main: 'mac', icon: '🍎', label: '맥 소식', items: withRel(d.mac || []), src: 'MacRumors · 9to5Mac · The Verge' },
      { main: 'ai', icon: '✦', label: 'AI 소식', items: withRel(d.ai || []), src: 'trawling.dev · Product Hunt · GeekNews' },
      { main: 'sec', icon: '🛡', label: '보안 소식', items: withRel(d.sec || []), src: 'BleepingComputer · The Hacker News' }
    ];
    const show = groups.filter(g => !f.main || g.main === f.main);
    ['mac', 'ai', 'sec'].forEach(m => { $('group-' + m).style.display = (!f.main || f.main === m) ? '' : 'none'; });
    show.forEach(g => {
      $('gsrc-' + g.main).textContent = g.src;
      $('gcount-' + g.main).textContent = g.items.length + '개';
      $('grows-' + g.main).innerHTML = g.items.map(n => newsRowHtml(n)).join('') || '<div class="empty-state">뉴스 없음</div>';
    });
    $$('#view-dashboard .nrow').forEach(el => {
      el.onclick = (ev) => {
        const mini = ev.target.closest('[data-app]');
        if (mini) { ev.stopPropagation(); openModal(mini.dataset.app); return; }
        state.news.main = el.dataset.main; state.news.sub = ''; state.news.detailId = el.dataset.id;
        if (state.news.layout !== 'B') setNewsLayout('B');
        switchView('news');
      };
    });
  }

  /* R51: HOT 규칙 — 6h NEW / 6~24h HOT / 초과 무표시 */
  function newsBadges(n) {
    const age = Date.now() - (n.publishedAt || 0);
    let html = '';
    if (age < 6 * 3600 * 1000) html += '<span class="newtag">NEW</span>';
    else if (age < 24 * 3600 * 1000) html += '<span class="hot">HOT</span>';
    return html;
  }

  function newsRowHtml(n) {
    const rel = (n.relatedApps || []).slice(0, 3);
    const relMore = (n.relatedApps || []).length - rel.length;
    return '<button type="button" class="nrow" data-id="' + esc(n.id) + '" data-main="' + esc(n.main) + '">' +
      (n.thumbnailUrl ? '<img class="nthumb" src="' + esc(n.thumbnailUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' : '') +
      '<span class="nbody"><span class="nmeta"><span class="subpill">' + esc(n.sub) + '</span>' +
      '<span>' + esc(n.sourceName) + ' · ' + fmtTime(n.publishedAt) + '</span>' +
      newsBadges(n) + '</span>' +
      '<span class="ntitle">' + esc(newsTitle(n)) + '</span>' +
      (newsSummary(n) ? '<span class="ndesc">' + esc(newsSummary(n)) + '</span>' : '') +
      (rel.length ? '<span class="hrow-rel">' + rel.map(a =>
        '<span class="relmini" data-app="' + esc(a.id) + '">' + appIcon(a, 'hicon') +
        '<span>' + esc(a.name) + '</span></span>'
      ).join('') + (relMore > 0 ? '<span class="relmore">+' + relMore + '</span>' : '') + '</span>' : '') +
      '</span>' +
      '<span class="narrow" aria-hidden="true">↗</span></button>';
  }

  function renderSidebar(sources, trends, d) {
    // 실시간 크롤링 (뉴스 소스 우선 최대 5)
    const news = (sources || []).filter(s => s.type === 'NEWS_RSS').slice(0, 5);
    const rows = (news.length ? news : (sources || []).slice(0, 5)).map(s => {
      const st = s.lastStatus === 'SUCCESS' ? ['ok', '수집 중'] : (s.lastStatus === 'FAILED' ? ['fail', '오류'] : ['wait', '대기']);
      return '<div class="srcrow"><span class="dot ' + st[0] + '"></span><span>' + esc(s.name) + '</span>' +
        '<span class="cnt">' + fmtTime(s.lastRunAt) + '</span><span class="st ' + st[0] + '">' + st[1] + '</span></div>';
    }).join('');
    $('sideSources').innerHTML = rows || '<div class="mini-empty">소스 없음</div>';
    $('sideToday').textContent = '오늘 총 ' + (d.todayNews || 0) + '개 수집';

    // 인기 태그 (제목 키워드 추출)
    const allNews = [...(d.mac || []), ...(d.ai || []), ...(d.sec || [])];
    const tags = extractTags(allNews);
    $('sideTags').innerHTML = tags.map(t => '<button type="button" class="tag" data-tag="' + esc(t.tag) + '">#' + esc(t.tag) + '<span class="mono">' + t.count + '</span></button>').join('') || '<span class="muted sm">태그 없음</span>';
    $$('#sideTags .tag').forEach(b => {
      b.onclick = () => {
        state.news.q = b.dataset.tag;
        state.news.page = 1;
        switchView('news');
      };
    });

    // 세일 중인 앱 — App 엔티티에 세일 필드 없음(R50 확인): 유료+최근 갱신 근사 셀렉션 유지
    const renderSale = (paid) => {
      $('sideSaleCount').textContent = paid.length + '개';
      $('sideSale').innerHTML = paid.map(a =>
        '<button type="button" class="salerow" data-id="' + esc(a.id) + '">' + appIcon(a, 'hicon') +
        '<span><b>' + esc(a.name) + '</b><small>' + esc(a.category || '') + ' · ' + fmtTime(a.lastUpdatedAt) + '</small></span>' +
        '<span class="saleprice">' + priceText(a) + '</span></button>'
      ).join('') || '<div class="mini-empty">세일 앱 없음</div>';
      $$('#sideSale .salerow').forEach(el => { el.onclick = () => openModal(el.dataset.id); });
    };
    const paid = (d.updatedApps || []).filter(a => a.license === 'PAID').slice(0, 3);
    if (paid.length) renderSale(paid);
    else api('/api/apps?license=PAID&page=1&pageSize=3&sort=mas').then(r => renderSale(r.apps || [])).catch(() => renderSale([]));

    // 카테고리 바로가기 (실측 카운트)
    const byCat = ((trends || {}).byCategory) || {};
    const tiles = [
      { icon: '⚡', label: '생산성', cat: '생산성' },
      { icon: '⬛', label: '개발자 도구', cat: '개발' },
      { icon: '🔧', label: '유틸리티', cat: '유틸리티' },
      { icon: '🎨', label: '크리에이티브', cat: '디자인·크리에이티브' },
      { icon: '🌿', label: '라이프스타일', cat: '미디어·엔터' },
      { icon: '✦', label: 'AI 도구', cat: '', tag: 'AI-Agent', count: (trends || {}).aiTagCount || 0 }
    ];
    $('sideCats').innerHTML = tiles.map((t, i) =>
      '<button type="button" class="cattile" data-i="' + i + '"><span class="ci">' + t.icon + '</span>' +
      '<span><b>' + esc(t.label) + '</b><small>' + (t.cat ? (byCat[t.cat] || 0) : t.count) + '개</small></span></button>'
    ).join('');
    $$('#sideCats .cattile').forEach(b => {
      b.onclick = () => {
        const t = tiles[parseInt(b.dataset.i, 10)];
        state.filters.category = t.cat;
        state.filters.tag = t.tag || '';
        state.filters.page = 1;
        switchView('appstore');
      };
    });
  }

  function subMainOf(sub) {
    for (const m of NEWS_MAINS) { if ((NEWS_SUBS[m.id] || []).includes(sub)) return m.id; }
    return 'mac';
  }
  /* ================= 앱 스토어 (목업 /apps) ================= */
  function switchStoreSub(sub) {
    state.storeSub = sub;
    $$('#view-appstore .subpill').forEach(b => {
      const on = b.dataset.sub === sub;
      b.classList.toggle('active', on);
      b.setAttribute('aria-selected', on);
    });
    if (sub === 'watchlist') applyChip('업데이트');
    else if (sub === 'timeline') applyChip('전체');
    else loadStore();
  }

  function loadStore() {
    if (state.storeSub === 'stats') loadStatsPanel();
    else loadStoreList();
    loadStoreChrome();
  }
  function storeQueryFromFilters() {
    const f = state.filters;
    const p = new URLSearchParams({ sort: f.sort, page: f.page, pageSize: PAGE_SIZE });
    if (f.license) p.set('license', f.license);
    if (f.cat) p.set('category', f.cat);
    if (f.q) p.set('q', f.q);
    if (f.newOnly) p.set('newOnly', 'true');
    if (f.updatedOnly) p.set('updatedOnly', 'true');
    if (state.storeSub === 'watchlist') { p.set('bumped', 'true'); p.set('updatedOnly', 'true'); }
    return '/api/apps?' + p.toString();
  }

  function buildStoreQuery() {
    return storeQueryFromFilters();
  }

  function applyChip(label) {
    const chip = APP_CHIPS.find(c => c.label === label) || APP_CHIPS[0];
    const pa = chip.params || {};
    const f = state.filters;
    f.license = pa.license || '';
    f.cat = pa.category || '';
    f.sort = pa.sort || 'stars';
    f.newOnly = !!pa.newOnly;
    f.updatedOnly = !!pa.updatedOnly || chip.label === '업데이트';
    f.page = 1;
    if (state.storeSub === 'stats') state.storeSub = 'timeline';
    syncStoreSub(chip.label);
    renderStoreChips();
    renderStoreSidebar();
    // 업데이트 칩은 Watchlist 탭과 동일 의미 → 서브탭 연동
    if (chip.label === '업데이트' && state.storeSub !== 'watchlist') {
      state.storeSub = 'watchlist';
      $$('#view-appstore .subpill').forEach(b => b.classList.toggle('active', b.dataset.sub === 'watchlist'));
    } else if (chip.label !== '업데이트' && state.storeSub === 'watchlist') {
      state.storeSub = 'timeline';
      $$('#view-appstore .subpill').forEach(b => b.classList.toggle('active', b.dataset.sub === 'timeline'));
    }
    loadStoreList();
  }

  function syncStoreSub(label) {
    const sub = label === '업데이트' ? 'watchlist' : (label && label !== '전체' ? 'timeline' : state.storeSub);
    state.storeSub = sub;
    $$('#view-appstore .subpill').forEach(b => {
      const on = b.dataset.sub === sub;
      b.classList.toggle('active', on);
      b.setAttribute('aria-selected', on);
    });
  }

  function renderStoreChips() {
    const f = state.filters;
    let active = '';
    for (const chip of APP_CHIPS) {
      const pa = chip.params || {};
      const match = (pa.license || '') === f.license &&
        (pa.category || '') === f.cat &&
        (!!pa.newOnly) === f.newOnly &&
        (!!pa.updatedOnly || chip.label === '업데이트') === (f.updatedOnly || state.storeSub === 'watchlist') &&
        (!pa.sort || pa.sort === f.sort);
      if (match) { active = chip.label; break; }
    }
    if (!active && !f.license && !f.cat && !f.newOnly && !f.updatedOnly && f.sort === 'stars') active = '전체';
    $('storeChips').innerHTML = APP_CHIPS.map(c =>
      '<button type="button" class="chip' + (c.label === active ? ' active' : '') + '" data-chip="' + esc(c.label) + '">' +
      esc(c.label) + (c.approx ? '<span class="mono">*</span>' : '') + '</button>'
    ).join('');
    $$('#storeChips .chip').forEach(b => {
      b.onclick = () => applyChip(b.dataset.chip);
    });
  }

  function renderStoreSidebar() {
    const f = state.filters;
    // 카테고리
    $('catChoices').innerHTML = CAT_CHOICES.map(c =>
      '<button type="button" class="' + (c.cat === f.cat ? 'active' : '') + '" data-cat="' + esc(c.cat) + '">' + esc(c.label) + '</button>'
    ).join('');
    // 가격
    const saleOn = f.license === 'PAID' && f.sort === 'updated';
    $('priceChoices').innerHTML = PRICE_CHOICES.map(p =>
      '<button type="button" class="' + (p.license === f.license && (p.sale ? saleOn : !p.sale) ? 'active' : '') + '" data-price="' + p.license + '">' + esc(p.label) + '</button>'
    ).join('');
    // 정렬
    $('sortChoices').innerHTML = SORT_CHOICES.map(s =>
      '<button type="button" class="' + (s.sort === f.sort ? 'active' : '') + '" data-sort="' + s.sort + '">' + esc(s.label) + '</button>'
    ).join('');
    $('catChoices').querySelectorAll('button').forEach(b => { b.onclick = () => setFilter({ cat: b.dataset.cat }, true); });
    $('priceChoices').querySelectorAll('button').forEach(b => {
      b.onclick = () => {
        const p = PRICE_CHOICES.find(o => o.license === b.dataset.price) || PRICE_CHOICES[0];
        setFilter(p.sale ? { license: p.license, sort: 'updated' } : { license: p.license });
      };
    });
    $('sortChoices').querySelectorAll('button').forEach(b => { b.onclick = () => setFilter({ sort: b.dataset.sort }); });
  }

  function setFilter(patch, catClear) {
    const f = state.filters;
    if (catClear) f.newOnly = false;
    Object.assign(f, patch, { page: 1 });
    renderStoreChips();
    renderStoreSidebar();
    loadStoreList();
  }

  let storeSeq = 0;
  let storeCache = null;
  function loadStoreList() {
    $('statsContent').innerHTML = '';
    const grid = $('appGrid'), empty = $('emptyStore');
    const seq = ++storeSeq;
    grid.innerHTML = '';
    empty.hidden = true;
    renderStoreChips();
    renderStoreSidebar();
    api(storeQueryFromFilters()).then(d => {
      if (seq !== storeSeq) return;
      storeCache = d;
      $('appsCountLabel').textContent = d.total;
      $('storeActiveCat').textContent = state.filters.cat || '전체';
      setNavCounts({ app: d.total });
      if (!d.apps.length) { empty.hidden = false; $('pagination').innerHTML = ''; return; }
      grid.innerHTML = d.apps.map(cardHtml).join('');
      bindCards(grid);
      renderPagination($('pagination'), d.page, d.pageSize, d.total, p => { state.filters.page = p; loadStoreList(); window.scrollTo(0, 0); });
    }).catch((e) => {
      console.error('[스토어] 목록 실패', e);
      if (seq !== storeSeq) return;
      empty.querySelector('.empty-state-title').textContent = '데이터를 불러오는데 실패했습니다';
      empty.hidden = false;
    });
  }

  let storeChromeSeq = 0;
  function loadStoreChrome() {
    const seq = ++storeChromeSeq;
    api('/api/main').then(d => {
      if (seq !== storeChromeSeq) return;
      const total = d.totalApps || 0;
      $('storeAppsLive').textContent = total + ' apps · 본체';
      setNavCounts({ app: total });
      // 티커 (최신 헤드라인)
      const heads = [...(d.mac || []), ...(d.ai || []), ...(d.sec || [])].slice(0, 8);
      $('storeTicker').innerHTML = heads.map(n =>
        '<span class="ticker-item"><b>' + esc(newsTitle(n)) + '</b><span class="srcbadge">' + esc(n.sourceName) + '</span></span>'
      ).join('') + (heads.map(n =>
        '<span class="ticker-item"><b>' + esc(newsTitle(n)) + '</b><span class="srcbadge">' + esc(n.sourceName) + '</span></span>'
      ).join(''));
      // 오늘의 픽 (업데이트 순 3건)
      const picks = (d.updatedApps || []).slice(0, 3);
      $('todayPicks').innerHTML = picks.length ? picks.map(a =>
        '<button type="button" class="pickrow" data-id="' + esc(a.id) + '">' + appIcon(a, 'hicon') +
        '<span style="min-width:0"><b>' + esc(a.name) + '</b><small>' + esc(verText(a)) + ' 업데이트</small></span></button>'
      ).join('') : '<div class="mini-empty">오늘 픽 없음</div>';
      $$('#todayPicks .pickrow').forEach(el => { el.onclick = () => openModal(el.dataset.id); });
    }).catch((e) => { console.error('[스토어] 크롬 실패', e); });
  }

  const LICENSE_LABEL = { OSS: '오픈소스', FREE: '프리', PAID: '유료' };
  const LICENSE_CLASS = { OSS: 'oss', FREE: 'free', PAID: 'paid' };

  function cardDesc(a) {
    const ko = a.descriptionKo || a.descriptionSnippet;
    const en = a.descriptionSnippet || a.descriptionKo;
    const raw = state.lang === 'ko' ? (ko || '') : (en || '');
    return raw ? excerpt(raw) : '';
  }
  function cardPrice(a) {
    if (a.license === 'FREE' || a.price === 0) return '<span class="ac-price free">무료</span>';
    if (a.price > 0) return '<span class="ac-price">₩' + Number(a.price).toLocaleString() + '</span>';
    if (a.license === 'PAID') return '<span class="ac-price">유료</span>';
    return '<span class="ac-price free">무료</span>';
  }
  function cardTags(a) {
    const arr = Array.isArray(a.tags) ? a.tags : (typeof a.tags === 'string' && a.tags ? a.tags.split(',') : []);
    return arr.map(t => (t || '').trim()).filter(t => t.length > 1 && t.length < 16)[0] || '';
  }

  function cardHtml(a) {
    const badges = (a.isNew ? '<span class="ac-badge new">NEW</span>' : '') +
      (String(a.prevVersion || '').trim() ? '<span class="ac-badge upd">UPD</span>' : '');
    const tag = cardTags(a);
    return '<article class="app-card" role="listitem" data-id="' + esc(a.id) + '" tabindex="0">' +
      '<div class="ac-top">' + appIcon(a, 'app-icon') +
      '<div class="ac-badges">' + badges + '</div></div>' +
      '<div class="app-name">' + esc(a.name) + '</div>' +
      '<div class="ac-sub"><span class="badge">' + esc(a.category || '') + '</span>' +
      (tag ? '<span class="tag">#' + esc(tag) + '</span>' : '') + '</div>' +
      (cardDesc(a) ? '<p class="card-desc">' + esc(stripMd(cardDesc(a))) + '</p>' : '<p class="card-desc muted">정보 없음</p>') +
      '<div class="ac-foot">' + cardPrice(a) +
      '<span class="mono">v' + esc(a.version || '-') + '</span></div>' +
      '<div class="ac-actions">' +
      '<button type="button" class="ac-btn primary" data-open="' + esc(a.id) + '">자세히</button>' +
      '<button type="button" class="ac-btn ghost" data-share="' + esc(a.id) + '">공유</button>' +
      '</div></article>';
  }

  function bindCards(container) {
    $$('.app-card', container).forEach(el => {
      const id = el.dataset.id;
      $$('[data-open]', el).forEach(b => { b.onclick = e => { e.stopPropagation(); openModal(id); }; });
      $$('[data-share]', el).forEach(b => {
        b.onclick = e => {
          e.stopPropagation();
          const item = storeCache && storeCache.apps.find(o => o.id === id);
          const name = item ? item.name : id;
          if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(id).then(() => toast(name + ' 링크 복사됨'));
          } else { toast(name + ' · ' + id); }
        };
      });
      el.onclick = () => openModal(id);
      el.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openModal(id); } };
    });
  }

  function renderPagination(box, page, pageSize, total, go) {
    const pages = Math.max(1, Math.ceil(total / pageSize));
    if (pages <= 1) { box.innerHTML = ''; return; }
    let html = '<button type="button" data-p="' + (page - 1) + '"' + (page <= 1 ? ' disabled' : '') + ' aria-label="이전">‹</button>';
    const set = {};
    [1, 2, page - 1, page, page + 1, pages - 1, pages].forEach(n => { if (n >= 1 && n <= pages) set[n] = true; });
    const arr = Object.keys(set).map(Number).sort((a, b) => a - b);
    let prev = 0;
    arr.forEach(n => {
      if (n - prev > 1) html += '<button type="button" disabled>…</button>';
      html += '<button type="button" data-p="' + n + '"' + (n === page ? ' class="active"' : '') + '>' + n + '</button>';
      prev = n;
    });
    html += '<button type="button" data-p="' + (page + 1) + '"' + (page >= pages ? ' disabled' : '') + ' aria-label="다음">›</button>';
    box.innerHTML = html;
    $$('button[data-p]', box).forEach(b => {
      if (b.disabled) return;
      b.onclick = () => go(parseInt(b.dataset.p, 10));
    });
  }

  function loadStatsPanel() {
    $('appGrid').innerHTML = '';
    $('pagination').innerHTML = '';
    $('emptyStore').hidden = true;
    const box = $('statsContent');
    box.innerHTML = '<div class="empty-state">통계 불러오는 중…</div>';
    api('/api/stats').then(s => api('/api/stats/trends').then(t => {
      const cats = Object.keys(t.byCategory || {}).sort((a, b) => t.byCategory[b] - t.byCategory[a]);
      const max = cats.length ? t.byCategory[cats[0]] : 1;
      box.innerHTML = '<div class="kpi-grid">' +
        kpi(s.totalApps, '수집 앱') + kpi(s.activeSources, '활성 소스') +
        kpi(t.newLast7d, '최근 7일 신규') + kpi(t.versionBumpsLast7d, '최근 7일 버전업') +
        kpi(formatBytes((s.netRxBytes || 0) + (s.netTxBytes || 0)), '30일 네트워크') + '</div>' +
        '<h3 class="stats-section-title">카테고리 분포</h3>' +
        cats.map(c => '<div class="bar-row"><span class="bar-name">' + esc(c) + '</span>' +
          '<span class="bar-track"><span class="bar-fill" style="width:' + Math.round(t.byCategory[c] / max * 100) + '%"></span></span>' +
          '<span class="bar-count">' + t.byCategory[c] + '</span></div>').join('');
    })).catch((e) => {
      console.error('[스토어] 통계 실패', e);
      box.innerHTML = '<div class="empty-state">통계를 불러오는데 실패했습니다</div>';
    });
  }
  function kpi(n, l) { return '<div class="kpi"><div class="kpi-value">' + n + '</div><div class="kpi-label">' + l + '</div></div>'; }
  function formatBytes(b) {
    b = Number(b) || 0;
    if (b < 1024) return b + 'B';
    const kb = b / 1024;
    if (kb < 1024) return kb.toFixed(1) + 'KB';
    const mb = kb / 1024;
    if (mb < 1024) return mb.toFixed(1) + 'MB';
    return (mb / 1024).toFixed(1) + 'GB';
  }

  /* ---------- 앱 모달 (기존 유지) ---------- */
  function openModal(id, silent) {
    const modal = $('appModal');
    ensureModalTabs();
    state.modal.currentId = id;
    if (!silent) {
      $('modalTitle').textContent = '불러오는 중…';
      $('modalIcon').removeAttribute('src');
      $('modalDev').textContent = '';
      $('modalMetaPills').innerHTML = '';
      $('modalCta').innerHTML = '';
      $('panelIntro').innerHTML = '';
      $('panelFeatures').innerHTML = '';
      $('panelChangelog').innerHTML = '';
      setActiveTab('intro');
    }
    if (typeof modal.showModal === 'function' && !modal.open) modal.showModal();
    api('/api/apps/' + encodeURIComponent(id)).then(a => { renderModal(a); })
      .catch((e) => { console.error('[모달] 실패', e); $('modalTitle').textContent = '불러오기 실패'; });
  }
  function closeModal() {
    const modal = $('appModal');
    if (typeof modal.close === 'function') modal.close();
    state.modal.currentId = null;
  }
  function ensureModalTabs() {
    const tabsBox = $('modalTabs');
    if (!$1('[role="tab"]', tabsBox)) {
      tabsBox.innerHTML =
        '<button class="modal-tab active" role="tab" aria-selected="true" data-tab="intro" type="button" id="tabIntro">소개</button>' +
        '<button class="modal-tab" role="tab" aria-selected="false" data-tab="features" type="button" id="tabFeatures">특징</button>' +
        '<button class="modal-tab" role="tab" aria-selected="false" data-tab="changelog" type="button" id="tabChangelog">새 기능</button>';
      $$('[role="tab"]', tabsBox).forEach(btn => { btn.onclick = () => setActiveTab(btn.dataset.tab); });
    }
  }
  function setActiveTab(tab) {
    state.modal.activeTab = tab;
    $$('#modalTabs [role="tab"]').forEach(btn => {
      const on = btn.dataset.tab === tab;
      btn.setAttribute('aria-selected', on);
      btn.classList.toggle('active', on);
    });
    $$('#modalPanels [role="tabpanel"]').forEach(p => {
      const on = p.id === 'panel' + tab.charAt(0).toUpperCase() + tab.slice(1);
      p.classList.toggle('active', on);
      p.hidden = !on;
    });
  }
  function renderModal(a) {
    $('modalTitle').textContent = a.name || '앱 상세';
    if (a.iconUrl) { $('modalIcon').style.display = ''; $('modalIcon').src = a.iconUrl; }
    else { $('modalIcon').style.display = 'none'; }
    $('modalDev').textContent = a.developer || '';
    const pills = [];
    if (a.license) pills.push('<span class="pill">' + esc(LICENSE_LABEL[a.license] || a.license) + '</span>');
    if (a.category) pills.push('<span class="pill">' + esc(a.category) + '</span>');
    if (a.stars != null) pills.push('<span class="pill">★ ' + a.stars + '</span>');
    if (a.isNew) pills.push('<span class="pill">NEW</span>');
    $('modalMetaPills').innerHTML = pills.join('');
    const cta = [];
    if (a.homepageUrl) cta.push('<a class="btn-primary" href="' + esc(a.homepageUrl) + '" target="_blank" rel="noopener">홈페이지</a>');
    if (a.repoFullName) cta.push('<a class="btn-secondary" href="https://github.com/' + esc(a.repoFullName) + '" target="_blank" rel="noopener">GitHub</a>');
    if (a.trackId) cta.push('<a class="btn-secondary" href="https://apps.apple.com/us/app/id' + a.trackId + '" target="_blank" rel="noopener">App Store</a>');
    $('modalCta').innerHTML = cta.join('');
    const koFull = a.descriptionKo || '';
    const enFull = a.descriptionSnippet || '';
    const introKo = excerpt(koFull) || excerpt(enFull);
    const introEn = excerpt(enFull) || excerpt(koFull);
    const funcKo = splitBody(koFull);
    const funcEn = splitBody(enFull);
    const funcShow = funcKo || funcEn;
    const introHtml = (introKo || introEn) ? mdBlock(introKo, introEn) : '<p>소개 정보가 없습니다.</p>';
    const funcHtml = (funcShow && funcShow !== (introKo || introEn)) ? mdBlock(funcKo, funcEn) : '';
    $('panelIntro').innerHTML = '<h4>소개</h4>' + introHtml + (funcHtml ? '<h4 style="margin-top:18px;">세부 설명</h4>' + funcHtml : '');
    const feats = [];
    if (a.averageRating != null) feats.push('평점 ' + a.averageRating + (a.ratingCount != null ? ' (' + a.ratingCount + '개)' : ''));
    if (a.version) feats.push('버전 ' + esc(a.version));
    if (a.sellerName) feats.push('판매: ' + esc(a.sellerName));
    $('panelFeatures').innerHTML = '<h4>특징</h4>' + (feats.length ? feats.map(f => '<p>' + f + '</p>').join('') : '<p>특징 정보가 없습니다.</p>');
    const notesKo = (a.releaseNotesKo || a.releaseNotesSummary) || '';
    const notesEn = (a.releaseNotesSummary || a.releaseNotesKo) || '';
    const notesShow = notesKo || notesEn;
    let newsHtml = '';
    if (notesShow) {
      newsHtml = mdBlock(notesKo, notesEn);
      if (a.version || (a.versions && a.versions.length)) {
        const rows = (a.versions || []).slice(0, 10).map(v => {
          const vlink = v.sourceUrl ? ' <a target="_blank" rel="noopener" href="' + esc(v.sourceUrl) + '">열기</a>' : '';
          return '<tr><td>' + esc(v.version) + '</td><td class="notes">' + md(v.notesSummary || '') + '</td><td>' + vlink + '</td></tr>';
        }).join('');
        newsHtml += '<table class="version-table"><tr><th>버전</th><th>새 기능</th><th>링크</th></tr>' +
          (a.version ? '<tr><td><b>' + esc(a.version) + '</b> (현재)' + (a.prevVersion ? ' ← ' + esc(a.prevVersion) + ' 화' : '') + '</td><td class="notes">' + md(a.releaseNotesSummary || '') + '</td><td></td></tr>' : '') + rows + '</table>';
      }
    } else {
      newsHtml = '<p>버전 기록이 없습니다.</p>';
    }
    $('panelChangelog').innerHTML = '<h4>새 기능</h4>' + newsHtml;
  }

  /* ================= 뉴스 ================= */
  function switchNewsTop(tab) {
    state.news.topTab = tab;
    $$('#topTabs .toptab').forEach(b => {
      const on = b.dataset.top === tab;
      b.classList.toggle('active', on);
      if (on) b.setAttribute('aria-selected', 'true'); else b.removeAttribute('aria-selected');
    });
    if (tab === 'appstore') { switchView('appstore'); return; }
    if (tab === 'bookmarks') loadBookmarks();
    else { applyNewsLayout(); loadNews(); }
  }

  function switchNewsMain(main) {
    state.news.main = main;
    state.news.sub = '';
    state.news.page = 1;
    state.news.detailId = null;
    syncNewsMains();
    applyNewsLayout();
    loadNews();
  }

  function syncNewsMains() {
    $$('#newsMains .mainpill').forEach(b => {
      const on = b.dataset.main === state.news.main;
      b.classList.toggle('active', on);
      if (on) b.setAttribute('aria-selected', 'true'); else b.removeAttribute('aria-selected');
    });
    $('newsCrumb').textContent = '/news / ' + state.news.main;
    const subs = NEWS_SUBS[state.news.main] || ['전체'];
    const html = subs.map(s =>
      '<button type="button" class="chip' + ((state.news.sub || '전체') === s ? ' active' : '') +
      '" data-sub="' + esc(s) + '">' + esc(s) + '</button>'
    ).join('');
    $('newsSubChips').innerHTML = html;
    $('newsSubChips2').innerHTML = html;
    $$('#newsSubChips .chip, #newsSubChips2 .chip').forEach(b => {
      b.onclick = () => {
        state.news.sub = b.dataset.sub === '전체' ? '' : b.dataset.sub;
        state.news.page = 1;
        state.news.detailId = null;
        syncNewsMains();
        applyNewsLayout();
        loadNews();
      };
    });
    const label = NEWS_MAINS.find(m => m.id === state.news.main);
    $('nlistTitle').textContent = state.news.main.toUpperCase() + ' / ' + ((label && label.label) || '');
  }
  let newsSeq = 0;
  function setNewsLayout(layout) {
    state.news.layout = layout === 'B' ? 'B' : 'A';
    try { localStorage.setItem(NEWS_LAYOUT_KEY, state.news.layout); } catch (e) {}
    applyNewsLayout();
  }
  function applyNewsLayout() {
    const hybrid = state.news.layout === 'A';
    const h = $('newsHybrid'), n3 = $('news3');
    if (h) h.hidden = !hybrid;
    if (n3) n3.hidden = hybrid;
    $$('#layoutToggle .ltbtn').forEach(b => b.classList.toggle('active', b.dataset.layout === state.news.layout));
  }
  function loadNews() {
    syncNewsMains();
    applyNewsLayout();
    const seq = ++newsSeq;
    Promise.all([
      api('/api/main').catch(e => { console.error('[뉴스] /api/main 실패', e); return null; }),
      api(buildNewsQuery()).catch(e => { console.error('[뉴스] 목록 실패', e); return null; })
    ]).then(([main, list]) => {
      if (seq !== newsSeq) return;
      if (main) renderNewsChrome(main);
      if (state.news.layout === 'A') {
        if (main) renderCuration(main);
        if (list) renderHybridList(list);
      } else if (list) renderNewsList(list);
    });
  }

  /* R49: A형 — 큐레이션 8앱 상단 그리드 */
  function renderCuration(d) {
    const apps = (d.updatedApps || []).slice(0, 8);
    $('curCount').textContent = String(apps.length || 8);
    $('curationGrid').innerHTML = apps.map(a =>
      '<button type="button" class="curcard" data-id="' + esc(a.id) + '" role="listitem">' +
      appIcon(a, 'hicon') +
      '<b>' + esc(a.name) + '</b>' +
      '<span class="ver">' + esc([verText(a), a.category].filter(Boolean).join(' · ')) + '</span>' +
      priceText(a) + '</button>'
    ).join('') || '<div class="empty-state">큐레이션 없음</div>';
    $$('#curationGrid .curcard').forEach(el => { el.onclick = () => openModal(el.dataset.id); });
  }

  /* R49: A형 — 뉴스 행 + 인라인 관련앱 미니카드 */
  function renderHybridList(d) {
    const box = $('hybridList');
    if (!d.news.length) {
      box.innerHTML = '<div class="empty-state"><p class="empty-state-title">뉴스가 없습니다</p></div>';
      $('hybridPagination').innerHTML = '';
      return;
    }
    state.news.listIds = d.news.map(x => x.id);
    state.news.total = d.total || 0;
    box.innerHTML = d.news.map(n => {
      const rel = (n.relatedApps || []).slice(0, 3);
      const relMore = (n.relatedApps || []).length - rel.length;
      return '<button type="button" class="hrow" data-id="' + esc(n.id) + '" data-main="' + esc(n.main) + '" role="listitem">' +
        (n.thumbnailUrl ? '<img class="nthumb" src="' + esc(n.thumbnailUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' : '') +
        '<span class="hrow-body">' +
        '<span class="hrow-meta"><span class="subpill">' + esc(n.sub) + '</span>' +
        '<span>' + esc(n.sourceName) + ' · ' + fmtTime(n.publishedAt) + '</span>' +
        newsBadges(n) + '</span>' +
        '<span class="hrow-title">' + esc(newsTitle(n)) + '</span>' +
        (newsSummary(n) ? '<span class="hrow-desc">' + esc(newsSummary(n)) + '</span>' : '') +
        (rel.length ? '<span class="hrow-rel">' + rel.map(a =>
          '<span class="relmini" data-app="' + esc(a.id) + '">' + appIcon(a, 'hicon') +
          '<span>' + esc(a.name) + '</span></span>'
        ).join('') + (relMore > 0 ? '<span class="relmore">+' + relMore + '</span>' : '') + '</span>' : '') +
        '</span>' +
        '<span class="narrow" aria-hidden="true">↗</span></button>';
    }).join('');
    $$('#hybridList .hrow').forEach(el => {
      el.onclick = (ev) => {
        const mini = ev.target.closest('[data-app]');
        if (mini) { ev.stopPropagation(); openModal(mini.dataset.app); return; }
        state.news.detailId = el.dataset.id;
        if (el.dataset.main && el.dataset.main !== state.news.main) {
          state.news.main = el.dataset.main;
          state.news.sub = '';
          syncNewsMains();
        }
        setNewsLayout('B');
        applyNewsLayout();
        openNewsDetail(el.dataset.id);
      };
    });
    renderPagination($('hybridPagination'), d.page, d.pageSize, d.total, p => {
      state.news.page = p; loadNews(); window.scrollTo(0, 0);
    });
  }

  function renderNewsChrome(d) {
    const c = d.counts || {};
    $('ncount-mac').textContent = c.mac || 0;
    $('ncount-ai').textContent = c.ai || 0;
    $('ncount-sec').textContent = c.sec || 0;
    const total = (c.mac || 0) + (c.ai || 0) + (c.sec || 0);
    $('topNewsCount').textContent = total;
    setNavCounts({ news: total });
    // 티커 (최신 헤드라인)
    const heads = [...(d.mac || []), ...(d.ai || []), ...(d.sec || [])].slice(0, 8);
    const items = heads.map(n =>
      '<span class="ticker-item"><b>' + esc(newsTitle(n)) + '</b><span class="srcbadge">' + esc(n.sourceName) + '</span></span>'
    ).join('');
    $('tickerMove').innerHTML = items + items;
    // 우측 레일
    $('railSources').textContent = 12;
    $('railNew').textContent = d.todayNews || 0;
    $('railNewTime').textContent = '15분 전 갱신';
    api('/api/watchlist').then(srcs => {
      const rows = (srcs || []).filter(s => s.type === 'NEWS_RSS').slice(0, 6).map(s =>
        '<div class="railrow"><span class="dot' + (s.lastStatus === 'FAILED' ? ' wait' : '') + '"></span>' +
        '<span>' + esc(s.name) + '</span><span class="mono">' + fmtTime(s.lastRunAt) + '</span></div>'
      ).join('');
    $('railSrcRows').innerHTML = rows;
    }).catch(() => {});
    // 카테고리 구성안
    $('railCats').innerHTML = '<div class="railcats">' + NEWS_MAINS.map(m =>
      '<h5>' + esc(m.label) + '</h5><div class="chiprow">' + (NEWS_SUBS[m.id] || []).slice(1).map(s =>
        '<button type="button" class="chip" data-main="' + m.id + '" data-sub="' + esc(s) + '">' + esc(s) + '</button>'
      ).join('') + '</div>'
    ).join('') + '</div>';
    $$('#railCats .chip').forEach(b => {
      b.onclick = () => {
        state.news.main = b.dataset.main;
        state.news.sub = b.dataset.sub;
        state.news.page = 1;
        syncNewsMains();
        applyNewsLayout();
        loadNews();
        window.scrollTo(0, 0);
      };
    });
    renderSponsored();
  }

  /* R51: SPONSORED 슬롯 (인기순 1개, 스토어 이동) */
  let sponLoaded = false;
  function renderSponsored() {
    if (sponLoaded) return;
    sponLoaded = true;
    api('/api/apps?page=1&pageSize=1&sort=stars').then(d => {
      const a = d && d.apps && d.apps[0];
      const box = $('railSponsored');
      if (!box) return;
      if (!a) { box.innerHTML = '<p class="mini-empty">광고 슬롯 준비 중</p>'; return; }
      box.innerHTML = '<button type="button" class="sponrow" data-id="' + esc(a.id) + '">' +
        appIcon(a, 'hicon') +
        '<span><b>' + esc(a.name) + '</b><small>' + esc(a.category || '') + ' · ' + (a.license === 'FREE' ? '무료' : '유료') + '</small></span>' +
        '<span class="spon-cta">AD</span></button>';
      const b = box.querySelector('.sponrow');
      if (b) b.onclick = () => openModal(b.dataset.id);
    }).catch(() => {});
  }
  function buildNewsQuery() {
    const p = new URLSearchParams({ page: state.news.page, pageSize: NEWS_PAGE_SIZE });
    if (state.news.main) p.set('main', state.news.main);
    if (state.news.sub) p.set('sub', state.news.sub);
    if (state.news.q) p.set('q', state.news.q);
    return '/api/news?' + p.toString();
  }
  function logoText(src) {
    const m = { '9to5Mac': '9t5', 'MacRumors': 'MR', 'The Verge': 'VG', 'BleepingComputer': 'BC', 'The Hacker News': 'HN', '보안뉴스': '보안', '데일리시큐': '시큐', 'TechCrunch': 'TC', 'MarkTechPost': 'MT', 'OpenAI': 'AI' };
    for (const k of Object.keys(m)) { if ((src || '').includes(k)) return m[k]; }
    return (src || '?').slice(0, 2);
  }

  function renderNewsList(d) {
    const box = $('newsList');
    $('nlistTitle').textContent = state.news.main.toUpperCase() + ' / ' + d.total;
    if (!d.news.length) {
      box.innerHTML = '<div class="empty-state"><p class="empty-state-title">뉴스가 없습니다</p></div>';
      $('newsPagination').innerHTML = '';
      return;
    }
    state.news.listIds = d.news.map(x => x.id);
    state.news.total = d.total || 0;
    box.innerHTML = d.news.map(n =>
      '<button type="button" class="ncard' + (state.news.detailId === n.id ? ' sel' : '') + '" data-id="' + esc(n.id) + '">' +
      '<span class="ncard-top"><i class="srclogo">' + esc(logoText(n.sourceName)) + '</i>' +
      '<span>' + esc(n.sourceName) + '</span>' +
      newsBadges(n) +
      '<time>' + fmtTime(n.publishedAt) + '</time></span>' +
      '<span class="ncard-main"><span class="ncard-txt"><b>' + esc(newsTitle(n)) + '</b>' +
      (newsSummary(n) ? '<p>' + esc(newsSummary(n)) + '</p>' : '') + '</span>' +
      (n.thumbnailUrl ? '<img class="ncard-thumb" src="' + esc(n.thumbnailUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' : '') +
      '</span><span class="ncard-foot"><span class="subpill">' + esc(n.sub) + '</span>' +
      '<span class="star">☆</span></span></button>'
    ).join('');
    $$('#newsList .ncard').forEach(el => { el.onclick = () => openNewsDetail(el.dataset.id); });
    renderPagination($('newsPagination'), d.page, d.pageSize, d.total, p => { state.news.page = p; state.news.detailId = null; loadNews(); window.scrollTo(0, 0); });
    // 상세 유지/자동 선택
    if (pendingEdge) {
      const pick = pendingEdge === 'first' ? state.news.listIds[0] : state.news.listIds[state.news.listIds.length - 1];
      pendingEdge = null;
      if (pick) openNewsDetail(pick);
    }
    else if (state.news.detailId) openNewsDetail(state.news.detailId, true);
    else if (d.news.length && window.innerWidth > 1200) openNewsDetail(d.news[0].id, true);
  }

  let pendingEdge = null;
  function goNewsPage(target, edge) {
    const pages = Math.max(1, Math.ceil((state.news.total || 0) / NEWS_PAGE_SIZE));
    if (target < 1 || target > pages) return;
    state.news.page = target;
    pendingEdge = edge || null;
    state.news.detailId = null;
    applyNewsLayout();
    loadNews();
    window.scrollTo(0, 0);
  }

  function openNewsDetail(id, silent) {
    state.news.detailId = id;
    $$('#newsList .ncard').forEach(el => el.classList.toggle('sel', el.dataset.id === id));
    const box = $('newsDetail');
    if (!silent) box.scrollIntoView({ block: 'nearest' });
    // A형에서는 상세가 숨김 → B형 강제 전환
    if (state.news.layout === 'A') setNewsLayout('B');
    api('/api/news/' + encodeURIComponent(id)).then(n => {
      if (state.news.detailId !== id) return;
      renderNewsDetail(n);
      renderRailApps(n);
    }).catch((e) => {
      console.error('[뉴스] 상세 실패', e);
      box.innerHTML = '<div class="empty-state"><p class="empty-state-title">불러오기 실패</p></div>';
    });
  }

  function renderNewsDetail(n) {
    const box = $('newsDetail');
    const domain = (() => { try { return new URL(n.originalUrl).hostname; } catch (e) { return n.sourceName; } })();
    const on = isBm(n.id);
    const idx = state.news.listIds.indexOf(n.id);
    const lastOnPage = idx >= 0 && idx === state.news.listIds.length - 1;
    const firstOnPage = idx === 0;
    const totalPages = Math.max(1, Math.ceil((state.news.total || 0) / NEWS_PAGE_SIZE));
    const hasPrev = idx > 0 || (firstOnPage && state.news.page > 1);
    const hasNext = (idx >= 0 && idx < state.news.listIds.length - 1) || (lastOnPage && state.news.page < totalPages);
    box.innerHTML =
      '<span class="nd-crumb">' + esc(n.main.toUpperCase()) + ' / ' + esc(n.sub) + '</span>' +
      '<div class="hl-top"><span>' + esc(n.sourceName) + ' · ' + fmtTime(n.publishedAt) + '</span></div>' +
      '<h1 class="nd-title">' + esc(newsTitle(n)) + '</h1>' +
      (newsSummary(n) ? '<p class="nd-desc">' + esc(newsSummary(n)) + '</p>' : '') +
      '<div class="nd-actions">' +
      '<a class="btn-white" href="' + esc(n.originalUrl) + '" target="_blank" rel="noopener">원문 보기 ↗ <span class="mono">' + esc(domain) + '</span></a>' +
      '<button type="button" class="btn-ghost' + (on ? ' on' : '') + '" id="ndBm">' + (on ? '★ 북마크됨' : '☆ 북마크 저장') + '</button></div>' +
      (n.thumbnailUrl ? '<div class="nd-hero"><img src="' + esc(n.thumbnailUrl) + '" alt="" referrerpolicy="no-referrer">' +
        '<div class="nd-cap">THUMBNAIL · 원본 URL 표시&nbsp;&nbsp;&nbsp;' + esc(n.thumbnailUrl) + '</div></div>' : '') +
      '<div class="nd-body">' + formatNewsBody(n.contentHtml) + '</div>' +
      '<div class="policybox"><h4>본문 크롤링 정책</h4><ul>' +
      '<li>제목, 본문 텍스트, 원본 링크, 카테고리는 크롤링하여 표시</li>' +
      '<li>본문 이미지는 원본 서버 URL을 그대로 img src로 사용</li>' +
      '<li>출처: ' + esc(n.sourceName) + ' · robots.txt 준수</li></ul></div>' +
      '<nav class="nd-nav" aria-label="기사 이전/다음">' +
      '<button type="button" class="nd-prev' + (hasPrev ? '' : ' disabled') + '" data-dir="-1"' + (hasPrev ? '' : ' disabled') + '>‹ 이전 기사</button>' +
      '<span class="nd-navpos mono">' + (idx >= 0 ? (idx + 1) + ' / ' + state.news.listIds.length : '') + '</span>' +
      '<button type="button" class="nd-next' + (hasNext ? '' : ' disabled') + '" data-dir="1"' + (hasNext ? '' : ' disabled') + '>다음 기사 ›</button></nav>';
    $('ndBm').onclick = () => {
      const now = toggleBm(n);
      $('ndBm').textContent = now ? '★ 북마크됨' : '☆ 북마크 저장';
      $('ndBm').classList.toggle('on', now);
    };
    $$('.nd-nav [data-dir]', box).forEach(b => {
      b.onclick = () => {
        const i = state.news.listIds.indexOf(n.id) + (b.dataset.dir === '1' ? 1 : -1);
        if (i >= 0 && i < state.news.listIds.length) openNewsDetail(state.news.listIds[i]);
        else if (i < 0) { goNewsPage(state.news.page - 1, 'last'); }
        else { goNewsPage(state.news.page + 1, 'first'); }
      };
    });
  }

  function renderRailApps(n) {
    const box = $('railApps');
    const apps = n.relatedApps || [];
    box.innerHTML = apps.length ? apps.map(a =>
      '<button type="button" class="railapp" data-id="' + esc(a.id) + '">' + appIcon(a, 'hicon') +
      '<span><b>' + esc(a.name) + '</b><small>' + esc((a.category || '') + ' · ' + (a.version || '')) + '</small></span></button>'
    ).join('') : '<p class="muted sm">연동된 앱이 없습니다. 제목에 앱 이름이 포함되면 자동 연동됩니다.</p>';
    $$('#railApps .railapp').forEach(b => { b.onclick = () => openModal(b.dataset.id); });
  }

  function loadBookmarks() {
    const bm = getBm();
    const list = Object.values(bm).sort((a, b) => (b.publishedAt || 0) - (a.publishedAt || 0));
    $('nlistTitle').textContent = 'BOOKMARKS / ' + list.length;
    $('newsSubChips').innerHTML = '';
    $('newsSubChips2').innerHTML = '';
    // 북마크는 B형 목록 사용
    setNewsLayout('B');
    const box = $('newsList');
    if (!list.length) {
      box.innerHTML = '<div class="empty-state"><p class="empty-state-title">북마크가 없습니다</p><p class="empty-state-desc">뉴스 상세에서 ☆ 북마크 저장을 눌러보세요.</p></div>';
      $('newsPagination').innerHTML = '';
      $('newsDetail').innerHTML = '<div class="empty-state"><p class="empty-state-title">상세 없음</p></div>';
      return;
    }
    box.innerHTML = list.map(n =>
      '<button type="button" class="ncard" data-id="' + esc(n.id) + '">' +
      '<span class="ncard-top"><i class="srclogo">' + esc(logoText(n.sourceName)) + '</i>' +
      '<span>' + esc(n.sourceName) + '</span><time>' + fmtTime(n.publishedAt) + '</time></span>' +
      '<span class="ncard-main"><span class="ncard-txt"><b>' + esc(newsTitle(n)) + '</b></span>' +
      (n.thumbnailUrl ? '<img class="ncard-thumb" src="' + esc(n.thumbnailUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' : '') +
      '</span></button>'
    ).join('');
    $('newsPagination').innerHTML = '';
    $$('#newsList .ncard').forEach(el => { el.onclick = () => openNewsDetail(el.dataset.id); });
    if (list.length) openNewsDetail(list[0].id, true);
  }

  /* ---------- 알림 센터 (기존 유지) ---------- */
  function openNotifCenter() {
    const modal = $('appModal');
    ensureModalTabs();
    $('modalTitle').textContent = '알림 센터';
    $('modalIcon').style.display = 'none';
    $('modalDev').textContent = '';
    $('modalMetaPills').innerHTML = '';
    $('modalCta').innerHTML = '<button type="button" class="chip" id="notifReadAllBtn">모두 읽음</button>';
    $('panelIntro').innerHTML = '<div class="empty-state">불러오는 중…</div>';
    $('panelFeatures').hidden = true;
    $('panelChangelog').hidden = true;
    setActiveTab('intro');
    if (typeof modal.showModal === 'function') modal.showModal();
    const renderNotifs = d => {
      if (!d.notifications || !d.notifications.length) { $('panelIntro').innerHTML = '<div class="empty-state">알림이 없습니다</div>'; return; }
      $('panelIntro').innerHTML = d.notifications.map(n =>
        '<p class="nrow-admin"><span style="flex:1"><b>' + esc(n.type) + '</b><br>' + esc(n.summary) +
        '<br><small class="muted">' + fmtDate(n.createdAt) + (n.isRead ? '' : ' · <b>안읽음</b>') + '</small></span>' +
        (n.isRead ? '' : '<button type="button" data-nread="' + n.id + '">읽음</button>') +
        '<button type="button" data-ndel="' + n.id + '">삭제</button></p>'
      ).join('');
      $$('#panelIntro [data-nread]').forEach(b => { b.onclick = () => {
        apiPost('/api/notifications/' + b.dataset.nread + '/read').then(() => { toast('읽음 처리'); openNotifCenter(); }).catch(() => toast('실패'));
      }; });
      $$('#panelIntro [data-ndel]').forEach(b => { b.onclick = () => {
        apiDel('/api/notifications/' + b.dataset.ndel).then(() => { toast('삭제됨'); openNotifCenter(); }).catch(() => toast('실패'));
      }; });
    };
    api('/api/notifications?page=1&pageSize=20').then(renderNotifs).catch(() => { $('panelIntro').innerHTML = '<div class="empty-state">불러오기 실패</div>'; });
    const ra = $('notifReadAllBtn');
    if (ra) ra.onclick = () => {
      apiPost('/api/notifications/read-all').then(() => { toast('모두 읽음'); openNotifCenter(); }).catch(() => toast('실패'));
    };
  }

  /* ---------- 관리 서랍 (R43: 앱 설정·소스·도구 이관) ---------- */
  const ADMIN_INTERVALS = [15, 30, 60, 120, 360, 720, 1440, 10080];
  function openAdmin() {
    $('adminDrawer').hidden = false;
    $('adminBackdrop').hidden = false;
    loadAdminSettings();
    loadAdminSources();
  }
  function closeAdmin() {
    $('adminDrawer').hidden = true;
    $('adminBackdrop').hidden = true;
  }
  function loadAdminSettings() {
    api('/api/settings').then(s => {
      $('setPort').value = s.port;
      $('setRetention').value = String(s.retentionDays);
      $('setAutoStart').checked = !!s.autoStart;
      $('setWatchdog').value = s.watchdogIntervalSec;
      $('setTranslateKo').checked = !!s.translateKo;
      $('setNotifCrawl').checked = !!s.notifCrawlComplete;
      $('setNotifApp').checked = !!s.notifNewApp;
      $('setNotifNews').checked = !!s.notifNews;
      $('setNotifFail').checked = !!s.notifFailure;
      $('tokenState').textContent = '토큰 상태: ' + (s.githubTokenSet ? '등록됨 (미입력 시 유지)' : '미등록');
    }).catch(() => { $('tokenState').textContent = '토큰 상태: 불러오기 실패'; });
  }
  function saveAdminSettings(e) {
    e.preventDefault();
    const body = {
      port: parseInt($('setPort').value, 10),
      retentionDays: parseInt($('setRetention').value, 10),
      autoStart: $('setAutoStart').checked,
      watchdogIntervalSec: parseInt($('setWatchdog').value, 10),
      translateKo: $('setTranslateKo').checked,
      notifCrawlComplete: $('setNotifCrawl').checked,
      notifNewApp: $('setNotifApp').checked,
      notifNews: $('setNotifNews').checked,
      notifFailure: $('setNotifFail').checked
    };
    const tok = $('setToken').value.trim();
    if (tok) body.githubToken = tok;
    apiPost('/api/settings', body).then(s => {
      $('setToken').value = '';
      toast(s.port !== body.port ? '저장됨 (포트 변경은 서버 재시작 후 적용)' : '설정 저장됨');
      loadAdminSettings();
    }).catch(() => toast('설정 저장 실패'));
  }
  function loadAdminSources() {
    api('/api/watchlist').then(srcs => {
      if (!srcs.length) { $('adminSources').innerHTML = '<p class="tiny-note">소스 없음</p>'; return; }
      $('adminSources').innerHTML = srcs.map(s =>
        '<div class="srcrow"><span class="sname" title="' + esc(s.baseUrl || '') + '">' + esc(s.name) + '</span>' +
        '<span class="sstatus">' + esc(s.lastStatus || '') + '</span>' +
        '<select data-sint="' + esc(s.id) + '">' + ADMIN_INTERVALS.map(m =>
          '<option value="' + m + '"' + (s.intervalMinutes === m ? ' selected' : '') + '>' + m + '분</option>'
        ).join('') + '</select>' +
        '<button type="button" data-ssync="' + esc(s.id) + '">수집</button>' +
        '<button type="button" data-stoggle="' + esc(s.id) + '" class="' + (s.enabled ? 'on' : 'off') + '">' +
        (s.enabled ? 'ON' : 'OFF') + '</button></div>'
      ).join('');
      $$('#adminSources [data-stoggle]').forEach(b => { b.onclick = () => {
        apiPost('/api/sources/' + encodeURIComponent(b.dataset.stoggle) + '/toggle').then(r => {
          b.textContent = r.enabled ? 'ON' : 'OFF';
          b.className = r.enabled ? 'on' : 'off';
          toast('소스 ' + (r.enabled ? '켜짐' : '꺼짐'));
        }).catch(() => toast('토글 실패'));
      }; });
      $$('#adminSources [data-ssync]').forEach(b => { b.onclick = () => {
        apiPost('/api/sync', { sourceId: b.dataset.ssync }).then(() => toast('수집 요청됨')).catch(() => toast('수집 요청 실패'));
      }; });
      $$('#adminSources [data-sint]').forEach(sel => { sel.onchange = () => {
        apiPost('/api/sources/' + encodeURIComponent(sel.dataset.sint) + '/interval',
          { intervalMinutes: parseInt(sel.value, 10) }).then(() => toast('주기 변경됨')).catch(() => { toast('주기 변경 실패'); loadAdminSources(); });
      }; });
    }).catch(() => { $('adminSources').innerHTML = '<p class="tiny-note">불러오기 실패</p>'; });
  }

  /* ---------- 초기화 ---------- */
  function init() {
    // 메인 nav
    $$('#mainNav .navpill').forEach(b => { b.onclick = () => switchView(b.dataset.view); });
    $$('#mobileNav .navpill').forEach(b => { b.onclick = () => switchView(b.dataset.view); });
    $('brandLink').onclick = e => { e.preventDefault(); switchView('dashboard'); };
    // 앱스토어 서브탭
    $$('#view-appstore .subpill').forEach(b => { b.onclick = () => switchStoreSub(b.dataset.sub); });
    // 뉴스 TopTab
    $$('#topTabs .toptab').forEach(b => { b.onclick = () => switchNewsTop(b.dataset.top); });
    // 뉴스 main 탭
    $$('#newsMains .mainpill').forEach(b => { b.onclick = () => switchNewsMain(b.dataset.main); });
    // R49: A/B 레이아웃 토글 (localStorage 기억)
    $$('#layoutToggle .ltbtn').forEach(b => {
      b.onclick = () => {
        setNewsLayout(b.dataset.layout);
        state.news.detailId = null;
        state.news.page = 1;
        loadNews();
      };
    });
    // 메인 더보기
    $('dashAppsMore').onclick = () => switchView('appstore');
    $('dashLoadMore').onclick = () => {
      state.dashPage++;
      const f = state.mainFilter;
      ['mac', 'ai', 'sec'].forEach(m => {
        if (f.main && f.main !== m) return;
        const p = new URLSearchParams({ main: m, page: state.dashPage, pageSize: 4 });
        api('/api/news?' + p.toString()).then(d => {
          const box = $('grows-' + m);
          box.innerHTML += (d.news || []).map(n => newsRowHtml(n)).join('');
          $$('.nrow', box).forEach(el => {
            el.onclick = (ev) => {
              const mini = ev.target.closest('[data-app]');
              if (mini) { ev.stopPropagation(); openModal(mini.dataset.app); return; }
              state.news.main = el.dataset.main; state.news.sub = ''; state.news.detailId = el.dataset.id;
              if (state.news.layout !== 'B') setNewsLayout('B');
              switchView('news');
            };
          });
        }).catch((e) => { console.error('[대시보드] 더보기 실패', e); });
      });
    };
    // 검색
    let searchTimer = null;
    $('globalSearch').addEventListener('input', e => {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        const q = e.target.value.trim();
        if (state.view === 'news') { state.news.q = q; state.news.page = 1; applyNewsLayout(); loadNews(); }
        else if (state.view === 'appstore') { state.filters.q = q; state.filters.page = 1; loadStoreList(); }
        else if (q) { state.filters.q = q; switchView('appstore'); }
      }, 300);
    });
    document.addEventListener('keydown', e => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); $('globalSearch').focus(); }
    });
    // 정렬/필터 (앱 스토어 — 사이드바·칩은 renderStoreSidebar/renderStoreChips에서 배선)
    // 헤더
    $('notifBell').onclick = openNotifCenter;
    $('adminBtn').onclick = openAdmin;
    $('adminClose').onclick = closeAdmin;
    $('adminBackdrop').onclick = closeAdmin;
    $('adminSettingsForm').onsubmit = saveAdminSettings;
    $('adminSyncAll').onclick = () => {
      apiPost('/api/sync', {}).then(() => toast('전체 수집 요청됨')).catch(() => toast('수집 요청 실패'));
    };
    $('adminTranslateNow').onclick = () => {
      apiPost('/api/translate', {}).then(() => toast('번역 요청됨')).catch(() => toast('번역 요청 실패'));
    };
    $('adminNotifReadAll').onclick = () => {
      apiPost('/api/notifications/read-all').then(() => toast('모두 읽음')).catch(() => toast('실패'));
    };
    $('adminNotifCleanup').onclick = () => {
      apiPost('/api/notifications/cleanup').then(d => toast('정리됨 ' + (d.deleted || 0) + '건')).catch(() => toast('실패'));
    };
    $('adminSeedForm').onsubmit = e => {
      e.preventDefault();
      const q = $('seedQuery').value.trim();
      if (!q) return;
      const body = /^\d+$/.test(q) ? { trackId: q } : { name: q };
      apiPost('/api/apps/seed', body).then(() => {
        $('seedState').textContent = '등록 요청됨 — 5초 후 상태 확인';
        setTimeout(() => {
          api('/api/apps/seed/status').then(d => { $('seedState').textContent = '상태: ' + d.status; }).catch(() => {});
        }, 5000);
      }).catch(() => toast('시드 등록 실패'));
    };
    $('langToggle').onclick = () => {
      state.lang = state.lang === 'ko' ? 'en' : 'ko';
      $('langToggle').textContent = state.lang === 'ko' ? '한' : 'EN';
      toast(state.lang === 'ko' ? '한국어' : '원문');
      // 현재 보기 즉시 반영
    if (state.view === 'appstore') {
        if (state.storeSub === 'timeline' || state.storeSub === 'watchlist') loadStoreList();
        else loadStatsPanel();
      } else if (state.view === 'news') {
        if (state.news.detailId) openNewsDetail(state.news.detailId, true);
        else { applyNewsLayout(); loadNews(); }
      }
      if (state.modal.currentId) openModal(state.modal.currentId, true);
    };
    $('modalClose').onclick = () => { if (typeof $('appModal').close === 'function') $('appModal').close(); };
    $('appModal').addEventListener('click', e => { if (e.target === $('appModal')) $('appModal').close(); });
    document.addEventListener('click', e => {
      const btn = e.target.closest('[data-mdkey]');
      if (!btn) return;
      const t = mdStore[btn.dataset.mdkey];
      if (!t) return;
      t.showing = t.showing === 'ko' ? 'en' : 'ko';
      const box = btn.parentElement.querySelector('[data-mdtext][data-key="' + btn.dataset.mdkey + '"]');
      if (box) box.innerHTML = md(t.showing === 'ko' ? t.ko : t.en);
      btn.textContent = t.showing === 'ko' ? '원문보기' : '번역 보기';
    });
    // 카운트
    api('/api/apps?page=1&pageSize=1').then(d => {
      $('topAppCount').textContent = d.total;
      setNavCounts({ app: d.total });
    }).catch(() => {});
    updateBmCount();
    applyNewsLayout();
    switchView('dashboard');
    console.log('[INFO] [FEATURE] 맥줍줍 포털 로드 완료');
  }

  /* R51: 헤더 내비 카운트 배지 */
  function setNavCounts(part) {
    if (part.app != null) {
      ['navAppCount', 'navAppCountM'].forEach(id => { const el = $(id); if (el) el.textContent = part.app; });
    }
    if (part.news != null) {
      ['navNewsCount', 'navNewsCountM'].forEach(id => { const el = $(id); if (el) el.textContent = part.news; });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
