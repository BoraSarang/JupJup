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
    { label: '신규 등록', params: { newOnly: true, sort: 'firstSeen' } },
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
    { label: '최신순', sort: 'newest' }, { label: '인기순', sort: 'stars' },
    { label: '가격낮은순', sort: 'priceAsc' }, { label: '업데이트순', sort: 'updated' },
    { label: 'MAS 우선', sort: 'mas' }
  ];
  const ICON_BG = ['linear-gradient(135deg,#f97316,#ef4444)', 'linear-gradient(135deg,#3b82f6,#06b6d4)',
    'linear-gradient(135deg,#8b5cf6,#d946ef)', 'linear-gradient(135deg,#18181b,#3f3f46)',
    'linear-gradient(135deg,#22c55e,#15803d)', 'linear-gradient(135deg,#eab308,#f97316)'];

  /* 맥 게임 장르 (PLAN_v21, GameGenres.ORDER와 동일) */
  const GAME_GENRES = ['전체', '액션', '어드벤처', 'RPG', '전략', '시뮬레이션', '퍼즐',
    '캐주얼', '인디', '멀티', '레이싱·스포츠', '호러·서바이벌'];
  const GAME_SOURCES = [
    { label: '전체', value: '' },
    { label: 'Steam', value: 'steam' },
    { label: 'Epic', value: 'epic' }
  ];
  const GAME_SORTS = [
    { label: '최신순', value: 'newest' },
    { label: '평점순', value: 'rating' }
  ];

  const PAGE_SIZE = 20;
  const NEWS_PAGE_SIZE = 5;

  const NEWS_LAYOUT_KEY = 'macjupjup_news_layout';
  const FONT_KEY = 'macjupjup_font_px';
  const FONT_DEFAULT = 14, FONT_MIN = 12, FONT_MAX = 20;
  const state = {
    view: 'dashboard',
    storeSub: 'timeline',
    filters: { license: '', cat: '', q: '', sort: 'newest', page: 1, newOnly: false, updatedOnly: false, sourceId: '' },
    news: {
      main: 'mac', sub: '', page: 1, q: '', detailId: null, topTab: 'news',
      sourceId: '',
      listIds: [], total: 0,
      layout: (function () { try { return localStorage.getItem(NEWS_LAYOUT_KEY) === 'B' ? 'B' : 'A'; } catch (e) { return 'A'; } })()
    },
    dashPage: 1,
    lang: 'ko',
    modal: { currentId: null, activeTab: 'intro' },
    games: { genre: '', source: '', sourceId: '', sort: 'newest', page: 1 },
    community: { main: 'apple', page: 1, q: '', sourceId: '', detailId: null, listIds: [], total: 0 }
  };

  /* 수집 소스 목록 (watchlist 캐시 — 메뉴별 type으로 필터) */
  let filterSourcesCache = null;
  function loadFilterSources() {
    if (filterSourcesCache) return Promise.resolve(filterSourcesCache);
    return api('/api/watchlist').then(srcs => {
      filterSourcesCache = Array.isArray(srcs) ? srcs : [];
      return filterSourcesCache;
    }).catch(e => {
      filterSourcesCache = [];
      throw e;
    });
  }
  function sourcesForMenu(kind) {
    const all = filterSourcesCache || [];
    if (kind === 'news') return all.filter(s => s.type === 'NEWS_RSS');
    if (kind === 'community') return all.filter(s => s.type === 'COMMUNITY_BOARD');
    if (kind === 'games') {
      return all.filter(s => s.type === 'STEAM_FREETOMAC' || s.type === 'EPIC_FREE' || s.type === 'APPSTORRENT_GAMES');
    }
    return all.filter(s => s.type !== 'NEWS_RSS' && s.type !== 'COMMUNITY_BOARD' &&
      s.type !== 'STEAM_FREETOMAC' && s.type !== 'EPIC_FREE' && s.type !== 'APPSTORRENT_GAMES');
  }
  function renderSourceChoices(elId, kind, activeId, onPick) {
    const el = $(elId);
    if (!el) return;
    const draw = () => {
      const list = sourcesForMenu(kind);
      el.innerHTML = [{ id: '', name: '전체' }].concat(list).map(s =>
        '<button type="button" class="' + ((s.id || '') === (activeId || '') ? 'active' : '') +
        '" data-sid="' + esc(s.id || '') + '">' + esc(s.name || s.id) + '</button>'
      ).join('');
      el.querySelectorAll('button').forEach(b => {
        b.onclick = () => onPick(b.dataset.sid || '');
      });
    };
    if (filterSourcesCache) { draw(); return; }
    loadFilterSources().then(draw).catch(draw);
  }

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
  const SYSREQ_MARKER = '— SYSREQ —';
  function splitBody(full) {
    if (!full) return '';
    const i = full.indexOf(README_MARKER);
    if (i >= 0) return full.slice(i + README_MARKER.length).trim() || full.slice(0, i).trim();
    return full;
  }
  function excerpt(full) {
    if (!full) return '';
    const i = full.indexOf(README_MARKER);
    let s = i >= 0 ? full.slice(0, i).trim() : full.trim();
    if (s.length > 360) {
      const nl = s.indexOf('\n');
      if (nl > 40 && nl < 320) s = s.slice(0, nl).trim();
      else s = s.slice(0, 300).trim() + '…';
    }
    return s;
  }
  /** SYSREQ 마커 뒤는 시스템요건 HTML — 소개 본문과 분리 */
  function splitSysReq(full) {
    if (!full) return { body: '', sysreq: '' };
    const i = full.indexOf(SYSREQ_MARKER);
    if (i < 0) return { body: full.trim(), sysreq: '' };
    return {
      body: full.slice(0, i).trim(),
      sysreq: full.slice(i + SYSREQ_MARKER.length).trim()
    };
  }
  /** 스팀 bb HTML 요건 — 스크립트·이벤트 속성만 제거하고 구조 HTML 유지 */
  function sanitizeHtml(html) {
    if (!html) return '';
    return String(html)
      .replace(/<script[\s\S]*?<\/script>/gi, '')
      .replace(/<style[\s\S]*?<\/style>/gi, '')
      .replace(/\son\w+\s*=\s*"[^"]*"/gi, '')
      .replace(/\son\w+\s*=\s*'[^']*'/gi, '')
      .replace(/\son\w+\s*=\s*[^\s>]+/gi, '')
      .replace(/javascript:/gi, '');
  }
  function mdBlock(koText, enText) {
    const show = koText || enText || '';
    if (!show) return '';
    if (koText && enText && koText !== enText) {
      const key = 'm' + (++mdSeq);
      // 한글본문 기본 노출: descriptionKo 있으면 무조건 ko 우선 (toggleModalLang로 전환)
      mdStore[key] = { ko: koText, en: enText, showing: 'ko' };
      return '<div class="md-body" data-mdtext data-key="' + key + '">' + md(koText) + '</div>';
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
  /* 통화 표기: 서버 currency=USD 기준 $ (라운딩 2자리) */
  function usdPrice(n) {
    return '$' + (Math.round(Number(n) * 100) / 100).toFixed(2);
  }
  /* 유료/무료 배지 (license 미확정·OSS·price 0은 무료 처리) */
  function priceBadge(a) {
    if (a.price > 0 || a.license === 'PAID') return '유료';
    return '무료';
  }
  function priceText(a) {
    if (a.license === 'FREE' || a.price === 0) return '<span class="hprice free">무료</span>';
    if (a.price > 0) return '<span class="hprice">' + usdPrice(a.price) + '</span>';
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
    const bump = (w, weight) => { freq[w] = (freq[w] || 0) + weight; };
    items.forEach(n => {
      // 서버 태그 우선 (R50 news_articles.tags). 없으면 제목 키워드 추출 폴백
      if (n.tags) {
        String(n.tags).split(',').map(s => s.trim()).filter(Boolean).forEach(t => bump(t, 1));
        return;
      }
      const sources = [n.title, n.titleKo].filter(Boolean);
      const words = sources.join(' ').match(/[A-Za-z][A-Za-z0-9+_.-]{1,}|[가-힣]{2,}/g) || [];
      const seen = new Set();
      words.forEach(w => {
        if (/[가-힣]/.test(w)) {
          if (w.length < 3 || seen.has(w)) return;
          if (TAG_STOP.has(w)) return;
          seen.add(w);
          bump(w, 1);
          return;
        }
        const up = w.toUpperCase();
        if (TAG_STOP.has(up) || seen.has(w)) return;
        if (!/[A-Z0-9]/.test(w)) return;
        if (w.length < 4 && !/\d/.test(w) && !TAG_KEEP_SHORT.has(w) && !TAG_KEEP_SHORT.has(w.toLowerCase())) return;
        seen.add(w);
        // 브랜드/제품형 토큰(내부 대문자·숫자 포함) 가중
        const brandish = /[a-z][A-Z]/.test(w) || /\d/.test(w) || TAG_KEEP_SHORT.has(w);
        bump(w, brandish ? 2 : 1);
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
    const el = $('topBmCount');
    if (el) el.textContent = c;
  }

  /* ---------- 뷰 전환 ---------- */
  function switchView(view) {
    state.view = view;
    $$('#mainNav .navpill, #mobileNav .navpill').forEach(b => b.classList.toggle('active', b.dataset.view === view));
    const viewMap = {
      dashboard: 'view-dashboard',
      appstore: 'view-appstore',
      games: 'view-games',
      community: 'view-community',
      news: 'view-news'
    };
    Object.keys(viewMap).forEach(v => {
      const el = $(viewMap[v]);
      if (!el) return;
      const on = v === view;
      el.classList.toggle('active', on);
      el.hidden = !on;
    });
    if (view === 'dashboard') loadDashboard();
    else if (view === 'appstore') loadStore();
    else if (view === 'games') loadGames();
    else if (view === 'community') loadCommunity();
    else { applyNewsLayout(); loadNews(); }
    window.scrollTo(0, 0);
  }

  /* ================= 대시보드 ================= */
  let dashSeq = 0;
  function loadDashboard() {
    const seq = ++dashSeq;
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

  let dashCache = null;
  function renderDashboard(d, sources, trends) {
    dashCache = { main: d, sources, trends };
    const c = d.counts || {};
    const totalNews = (c.mac || 0) + (c.ai || 0) + (c.sec || 0);
    const gameN = c.games || 0;
    $('dashHeroStats').innerHTML = '오늘 수집된 앱 <b>' + (d.totalApps || 0) + '개</b> · 업데이트 <b>' +
      (d.updatedApps ? d.updatedApps.length : 0) + '개</b> · 뉴스 <b>' + totalNews + '개</b> · 게임 <b>' + gameN +
      '개</b> · 맥 ' + (c.mac || 0) + ' / AI ' + (c.ai || 0) + ' / 보안 ' + (c.sec || 0);
    $('dashUpdatedAt').textContent = d.generatedAt ? ('마지막 업데이트: ' + new Date(d.generatedAt).toLocaleString('ko-KR')) : '';
    setNavCounts({ games: gameN });

    // 하이라이트 4카드: 맥뉴스 / 앱 / 보안뉴스 / 맥 게임(이번 주)
    const topMac = (d.mac || [])[0], topApp = (d.updatedApps || [])[0], topSec = (d.sec || [])[0];
    const topGame = (d.games || [])[0];
    let hl = '';
    if (topMac) {
      hl += '<article class="hl-card" data-kind="news" data-main="mac" data-id="' + esc(topMac.id) + '" tabindex="0" role="link"><div>' +
        (topMac.thumbnailUrl ? '<img class="hl-thumb" src="' + esc(topMac.thumbnailUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' : '') +
        '</div><div class="hl-body"><div class="hl-top"><span class="hot">HOT</span><span>' +
        esc(topMac.sourceName) + ' · ' + fmtTime(topMac.publishedAt) + '</span></div>' +
        '<h3 class="hl-title">' + esc(newsTitle(topMac)) + '</h3>' +
        '<p class="hl-desc">' + esc(newsSummary(topMac)) + '</p></div></article>';
    }
    if (topApp) {
      hl += '<article class="hl-card glow" data-kind="app" data-id="' + esc(topApp.id) + '" tabindex="0" role="link"><div class="hl-body"><div class="hl-top">' +
        appIcon(topApp, 'hl-icon') +
        '<b style="font-size:1rem;color:var(--text)">' + esc(topApp.name) + '</b>' +
        (topApp.isNew ? '<span class="newpill">NEW</span>' : '') + '</div>' +
        '<div class="hl-top">' + esc([verText(topApp), topApp.category].filter(Boolean).join(' · ')) + '</div>' +
        '<p class="hl-desc">' + esc(topApp.category || '') + ' 앱 최신 소식을 확인하세요</p></div>' +
        '<span class="hl-price">' + priceBadge(topApp) + '</span></article>';
    }
    if (topSec) {
      const badge = newsBadges(topSec) || '<span class="hot">HOT</span>';
      hl += '<article class="hl-card" data-kind="news" data-main="sec" data-id="' + esc(topSec.id) + '" tabindex="0" role="link"><div>' +
        (topSec.thumbnailUrl ? '<img class="hl-thumb" src="' + esc(topSec.thumbnailUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' : '') +
        '</div><div class="hl-body"><div class="hl-top">' + badge + '<span>' +
        esc(topSec.sourceName) + ' · ' + fmtTime(topSec.publishedAt) + '</span></div>' +
        '<h3 class="hl-title">' + esc(newsTitle(topSec)) + '</h3>' +
        '<p class="hl-desc">' + esc(newsSummary(topSec)) + '</p></div></article>';
    }
    if (topGame) {
      hl += '<article class="hl-card glow" data-kind="games" data-id="' + esc(topGame.id) + '" tabindex="0" role="link">' +
        '<div class="hl-body"><div class="hl-top">' +
        (topGame.iconUrl ? '<img class="hl-thumb" style="width:52px;height:52px" src="' + esc(topGame.iconUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' : '') +
        '<span class="hot">GAME</span><span>' + esc(topGame.store || '') + ' · 주간</span></div>' +
        '<h3 class="hl-title">' + esc(topGame.name) + '</h3>' +
        '<p class="hl-desc">' + esc((topGame.genres || []).join(' · ') || '무료 맥 게임') + '</p></div>' +
        '<span class="hl-price"><span class="w0">FREE</span></span></article>';
    }
    $('dashHighlights').innerHTML = hl || '<div class="empty-state">하이라이트 없음</div>';
    $$('#dashHighlights .hl-card').forEach(el => {
      const go = (ev) => {
        if (ev && ev.target && ev.target.closest('a')) return;
        if (el.dataset.kind === 'app') { openModal(el.dataset.id); return; }
        if (el.dataset.kind === 'games') { switchView('games'); return; }
        openDashboardNews(el.dataset.main, el.dataset.id);
      };
      el.onclick = go;
      el.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); go(e); } };
    });

    renderDashApps(d.updatedApps || []);
    renderDashGames(d.games || [], gameN);
    loadDashboardGroups();
    renderSidebar(sources, trends, d);
  }

  function renderDashApps(apps) {
    const list = apps;
    $('dashAppCount').textContent = list.length + '개';
    $('dashApps').innerHTML = list.map(a => {
      const ver = [verText(a), fmtTime(a.releaseDate || a.lastUpdatedAt)].filter(Boolean).join(' · ');
      return '<button type="button" class="hcard" data-id="' + esc(a.id) + '" role="listitem">' +
      '<div class="hcard-top">' + appIcon(a, 'hicon') +
      '<span class="catpill">' + esc(a.category || '') + '</span></div>' +
      '<b>' + esc(a.name) + '</b><div class="ver">' + esc(ver) + '</div>' +
      priceText(a) + '</button>';
    }).join('') || '<div class="empty-state">앱 없음</div>';
    $$('#dashApps .hcard').forEach(el => { el.onclick = () => openModal(el.dataset.id); });
  }

  function renderDashGames(games, total) {
    const el = $('dashGames');
    const countEl = $('dashGameCount');
    if (!el) return;
    countEl.textContent = (total || games.length) + '개';
    el.innerHTML = games.map(g => gameMiniHtml(g)).join('') ||
      '<div class="empty-state">게임 없음 — 첫 수집 후 표시됩니다</div>';
    bindGameCards(el);
    setNavCounts({ games: total || games.length });
  }

  function loadDashboardGroups() {
    if (!dashCache) return;
    const d = dashCache.main;
    // R49: 대시보드 뉴스 행에도 관련앱 병합 (/api/main relatedApps)
    const withRel = (arr) => arr || [];
    const groups = [
      { main: 'mac', icon: '🍎', label: '맥 소식', items: withRel(d.mac || []), src: 'MacRumors · 9to5Mac · The Verge' },
      { main: 'ai', icon: '✦', label: 'AI 소식', items: withRel(d.ai || []), src: 'trawling.dev · Product Hunt · GeekNews' },
      { main: 'sec', icon: '🛡', label: '보안 소식', items: withRel(d.sec || []), src: 'BleepingComputer · The Hacker News' }
    ];
    ['mac', 'ai', 'sec'].forEach(m => { $('group-' + m).style.display = ''; });
    groups.forEach(g => {
      $('gsrc-' + g.main).textContent = g.src;
      $('gcount-' + g.main).textContent = g.items.length + '개';
      $('grows-' + g.main).innerHTML = g.items.map(n => newsRowHtml(n)).join('') || '<div class="empty-state">뉴스 없음</div>';
    });
    $$('#view-dashboard .nrow').forEach(el => {
      el.onclick = (ev) => {
        const mini = ev.target.closest('[data-app]');
        if (mini) { ev.stopPropagation(); openModal(mini.dataset.app); return; }
        openDashboardNews(el.dataset.main, el.dataset.id);
      };
    });
  }

  /* 하이라이트·뉴스 행 → 뉴스 뷰 상세 이동 */
  function openDashboardNews(main, id) {
    if (!id) return;
    state.news.main = main || 'mac';
    state.news.sub = '';
    state.news.page = 1;
    state.news.detailId = id;
    if (state.news.layout !== 'B') setNewsLayout('B');
    switchView('news');
    loadNews();
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

  /* ================= 맥 게임 (PLAN_v21) ================= */
  function gameStoreOf(g) {
    const t = String(g.tags || '');
    if (t.indexOf('appstorrent') >= 0) return 'appstorrent';
    if (t.indexOf('epic') >= 0) return 'epic';
    if (t.indexOf('steam') >= 0) return 'steam';
    return g.store || '';
  }

  function gameGenresOf(g) {
    if (Array.isArray(g.genres) && g.genres.length) return g.genres;
    const raw = String(g.tags || '').split(',').map(s => s.trim());
    return raw.filter(t => t && t !== 'game' && t !== 'steam' && t !== 'epic' && t !== 'appstorrent' &&
      t !== 'steam-header' &&
      t.indexOf('steam-appid:') !== 0 && t.indexOf('epic-') !== 0 && t.indexOf('genre:') !== 0);
  }

  function gameMiniHtml(g) {
    const store = gameStoreOf(g);
    const genres = gameGenresOf(g).slice(0, 2).join(' · ');
    const img = g.iconUrl
      ? '<img class="hicon" src="' + esc(g.iconUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">'
      : appIcon(g, 'hicon');
    return '<button type="button" class="hcard game-hcard" data-id="' + esc(g.id) + '" data-url="' + esc(g.sourceUrl || g.homepageUrl || '') + '" role="listitem">' +
      '<div class="hcard-top">' + img +
      '<span class="catpill">' + esc(store.toUpperCase() || 'GAME') + '</span></div>' +
      '<b>' + esc(g.name) + '</b><div class="ver">' + esc(genres || '무료') + '</div>' +
      '<span class="w0">FREE</span></button>';
  }

  // 지원언어 CSV(예: en,ko,ja)에 ko 포함 여부
  function hasKoLang(csv) {
    return String(csv || '').split(',').some(s => s.trim() === 'ko');
  }

  const LANG_LABEL = {
    en: '영어', ko: '한국어', ja: '일본어', zh: '중국어', fr: '프랑스어', de: '독일어',
    es: '스페인어', it: '이탈리아어', ru: '러시아어', pt: '포르투갈어', pl: '폴란드어',
    tr: '터키어', nl: '네덜란드어', cs: '체코어', da: '덴마크어', fi: '핀란드어',
    el: '그리스어', hu: '헝가리어', no: '노르웨이어', sv: '스웨덴어', th: '태국어',
    vi: '베트남어', uk: '우크라이나어', ar: '아랍어', hi: '힌디어', id: '인도네시아어',
    he: '히브리어', ro: '루마니아어', sk: '슬로바키아어', sr: '세르비아어',
    hr: '크로아티아어', bg: '불가리아어'
  };
  function langLabels(csv) {
    return String(csv || '').split(',')
      .map(s => s.trim()).filter(Boolean)
      .map(code => LANG_LABEL[code] || code.toUpperCase());
  }
  function isGameApp(a) {
    return a.category === '게임' || String(a.tags || '').split(',').some(t => t.trim() === 'game');
  }

  function gameCardHtml(g) {
    const store = gameStoreOf(g);
    const genres = gameGenresOf(g).slice(0, 3);
    const url = g.sourceUrl || g.homepageUrl || '';
    const koBadge = hasKoLang(g.supportedLanguages) ? '<span class="badge lang-ko">한국어</span>' : '';
    const img = g.iconUrl
      ? '<img class="g-thumb" src="' + esc(g.iconUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">'
      : '<div class="g-thumb g-fallback">🎮</div>';
    // 카드 클릭 → 내부 상세 모달 (외부 스토어 링크 제거, data-url은 폴백/참조용)
    return '<article class="game-card" role="listitem" data-id="' + esc(g.id) + '" data-url="' + esc(url) + '" tabindex="0">' +
      '<div class="g-top">' + img +
      (g.isNew ? '<div class="ac-badges"><span class="ac-badge new">NEW</span></div>' : '') + '</div>' +
      '<div class="app-name">' + esc(g.name) + '</div>' +
      '<div class="ac-sub">' +
      genres.map(t => '<span class="badge">' + esc(t) + '</span>').join('') +
      koBadge +
      '<span class="tag">' + esc((store || 'steam').toUpperCase()) + ' · MAC</span></div>' +
      '<div class="ac-foot"><span class="ac-price free">무료</span>' +
      '<span class="mono">' + esc(fmtDate(g.releaseDate || g.lastUpdatedAt)) + '</span></div>' +
      '</article>';
  }

  function bindGameCards(container) {
    $$('.game-card, .game-hcard', container).forEach(el => {
      const go = (e) => {
        if (e && e.target && (e.target.closest('a') || e.target.dataset.ext)) return;
        if (el.dataset.id) openModal(el.dataset.id);
        else switchView('games');
      };
      el.onclick = go;
      el.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); go(e); } };
    });
  }

  function gameQuery() {
    const g = state.games;
    const p = new URLSearchParams({ sort: g.sort, page: g.page, pageSize: PAGE_SIZE });
    if (g.genre) p.set('genre', g.genre);
    if (g.source) p.set('source', g.source);
    if (g.sourceId) p.set('sourceId', g.sourceId);
    return '/api/games?' + p.toString();
  }

  function renderGameFilters() {
    const g = state.games;
    $('gameChips').innerHTML = GAME_GENRES.map(label =>
      '<button type="button" class="chip' + (label === (g.genre || '전체') ? ' active' : '') + '" data-genre="' + esc(label) + '">' +
      esc(label) + '</button>'
    ).join('');
    $$('#gameChips .chip').forEach(b => {
      b.onclick = () => {
        state.games.genre = b.dataset.genre === '전체' ? '' : b.dataset.genre;
        state.games.page = 1;
        loadGamesList();
      };
    });
    $('gameSourceChoices').innerHTML = GAME_SOURCES.map(s =>
      '<button type="button" class="' + (s.value === g.source ? 'active' : '') + '" data-source="' + esc(s.value) + '">' + esc(s.label) + '</button>'
    ).join('');
    $$('#gameSourceChoices button').forEach(b => {
      b.onclick = () => { state.games.source = b.dataset.source; state.games.page = 1; loadGamesList(); };
    });
    $('gameGenreChoices').innerHTML = GAME_GENRES.map(label =>
      '<button type="button" class="' + (label === (g.genre || '전체') ? 'active' : '') + '" data-genre="' + esc(label) + '">' + esc(label) + '</button>'
    ).join('');
    $$('#gameGenreChoices button').forEach(b => {
      b.onclick = () => {
        state.games.genre = b.dataset.genre === '전체' ? '' : b.dataset.genre;
        state.games.page = 1;
        loadGamesList();
      };
    });
    $('gameSortChoices').innerHTML = GAME_SORTS.map(s =>
      '<button type="button" class="' + (s.value === g.sort ? 'active' : '') + '" data-sort="' + s.value + '">' + esc(s.label) + '</button>'
    ).join('');
    $$('#gameSortChoices button').forEach(b => {
      b.onclick = () => { state.games.sort = b.dataset.sort; state.games.page = 1; loadGamesList(); };
    });
    renderSourceChoices('gameCollectSourceChoices', 'games', g.sourceId, sid => {
      state.games.sourceId = sid;
      state.games.page = 1;
      loadGamesList();
    });
    $('gameActiveGenre').textContent = g.genre || '전체';
  }

  let gamesSeq = 0;
  function loadGamesList() {
    const grid = $('gameGrid'), empty = $('emptyGames');
    const seq = ++gamesSeq;
    if (!grid) return;
    grid.innerHTML = '';
    empty.hidden = true;
    $('gamesPagination').innerHTML = '';
    renderGameFilters();
    api(gameQuery()).then(d => {
      if (seq !== gamesSeq) return;
      const total = d.total || 0;
      $('gamesPanelCount').textContent = total;
      $('gameCountLabel').textContent = total + ' games · FREE';
      setNavCounts({ games: total });
      if (!d.games || !d.games.length) { empty.hidden = false; return; }
      grid.innerHTML = d.games.map(gameCardHtml).join('');
      bindGameCards(grid);
      // 이번 주 등록 가로줄: 최신 8
      const recent = d.games.slice(0, 8);
      const row = $('gameNewRow');
      if (row) {
        $('gameNewCount').textContent = recent.length + '개';
        row.innerHTML = recent.map(g => gameMiniHtml(g)).join('');
        bindGameCards(row);
      }
      // Epic 주간 하이라이트 (epic 우선)
      const epic = d.games.find(g => gameStoreOf(g) === 'epic');
      const hero = $('gameHero');
      if (hero) {
        if (epic) {
          hero.hidden = false;
          hero.innerHTML =
            '<div class="gh-body"><div class="gh-top"><span class="hot">이번 주 무료</span><span>EPIC</span></div>' +
            '<h3 class="hl-title">' + esc(epic.name) + '</h3>' +
            '<p class="hl-desc">' + esc(gameGenresOf(epic).join(' · ')) + ' · 스토어 호환 확인</p>' +
            (epic.sourceUrl ? '<a class="ac-btn primary" style="display:inline-block;margin-top:8px" href="' + esc(epic.sourceUrl) + '" target="_blank" rel="noopener">Epic에서 받기 ↗</a>' : '') +
            '</div>' +
            (epic.iconUrl ? '<img class="gh-thumb" src="' + esc(epic.iconUrl) + '" alt="" referrerpolicy="no-referrer">' : '');
        } else {
          hero.hidden = true;
          hero.innerHTML = '';
        }
      }
      renderPagination($('gamesPagination'), d.page, d.pageSize, d.total, p => {
        state.games.page = p;
        loadGamesList();
        window.scrollTo(0, 0);
      });
    }).catch(e => {
      console.error('[게임] 목록 실패', e);
      if (seq !== gamesSeq) return;
      empty.hidden = false;
      empty.querySelector('.empty-state-title').textContent = '데이터를 불러오는데 실패했습니다';
    });
  }

  function loadGames() {
    renderGameFilters();
    loadGamesList();
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
    else applyChip('전체');
  }

  function loadStore() {
    loadStoreList();
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
    if (f.sourceId) p.set('sourceIds', f.sourceId);
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
    f.sort = pa.sort || 'newest';
    f.newOnly = !!pa.newOnly;
    f.updatedOnly = !!pa.updatedOnly || chip.label === '업데이트';
    f.page = 1;
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
    if (!active && !f.license && !f.cat && !f.newOnly && !f.updatedOnly && f.sort === 'newest') active = '전체';
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
    renderSourceChoices('appSourceChoices', 'apps', f.sourceId, sid => setFilter({ sourceId: sid }));
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
    const grid = $('appGrid'), empty = $('emptyStore');
    const seq = ++storeSeq;
    grid.innerHTML = '';
    empty.hidden = true;
    $('pagination').innerHTML = '';
    renderStoreChips();
    renderStoreSidebar();
    api(storeQueryFromFilters()).then(d => {
      if (seq !== storeSeq) return;
      storeCache = d;
      $('appsCountLabel').textContent = d.total;
      $('storeActiveCat').textContent = state.filters.cat || '전체';
      // 헤더 카운트는 전체 고정값 — 필터 total로 덮지 않음
      if (!d.apps.length) { empty.hidden = false; $('pagination').innerHTML = ''; return; }
      grid.innerHTML = d.apps.map(cardHtml).join('');
      bindCards(grid);
      renderPagination($('pagination'), d.page, d.pageSize, d.total, p => { state.filters.page = p; loadStoreList(); window.scrollTo(0, 0); });
    }).catch((e) => {
      console.error('[스토어] 목록 실패', e);
      if (seq !== storeSeq) return;
      empty.querySelector('.empty-state-title').textContent = '데이터를 불러오는데 실패했습니다';
      empty.hidden = false;
      $('pagination').innerHTML = '';
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
      // 티커 (최신 헤드라인) — 내용이 같으면 다시 그리지 않음
      const heads = [...(d.mac || []), ...(d.ai || []), ...(d.sec || [])].slice(0, 8);
      const itemHtml = heads.map(n =>
        '<button type="button" class="ticker-item" data-main="' + esc(n.main || 'mac') + '" data-id="' + esc(n.id) + '"><b>' + esc(newsTitle(n)) + '</b><span class="srcbadge">' + esc(n.sourceName) + '</span></button>'
      ).join('');
      fillTicker($('storeTicker'), itemHtml);
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
    if (a.price > 0) return '<span class="ac-price">' + usdPrice(a.price) + '</span>';
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
    const box = $('statsContent');
    if (!box) return;
    box.innerHTML = '<div class="empty-state">통계 불러오는 중…</div>';
    Promise.all([
      api('/api/stats').catch(() => null),
      api('/api/stats/trends').catch(() => null),
      api('/api/stats/collect?days=7').catch(() => null),
      api('/api/main').catch(() => null)
    ]).then(([s, t, c, main]) => {
      if (!box.isConnected) return;
      if (!s && !t) {
        box.innerHTML = '<div class="empty-state">통계를 불러오는데 실패했습니다</div>';
        return;
      }
      const cats = Object.keys((t && t.byCategory) || {}).sort((a, b) => (t.byCategory[b] || 0) - (t.byCategory[a] || 0));
      const maxCat = cats.length ? (t.byCategory[cats[0]] || 1) : 1;
      const rx = (s && s.netRxBytes) || 0;
      const tx = (s && s.netTxBytes) || 0;
      const cTotal = (c && c.total) || {};
      const counts = (main && main.counts) || {};
      const newsTotal = (counts.mac || 0) + (counts.ai || 0) + (counts.sec || 0);
      const newsMax = Math.max(counts.mac || 0, counts.ai || 0, counts.sec || 0, 1);
      const daily = (c && c.days) || [];
      const dailyMax = Math.max(1, ...daily.map(d => Math.max((d.rxBytes || 0) + (d.txBytes || 0), 0)));

      let html = '<div class="kpi-grid">' +
        kpi((s && s.totalApps) || 0, '수집 앱') +
        kpi((s && s.activeSources) || 0, '활성 소스') +
        kpi((t && t.newLast7d) || 0, '최근 7일 신규') +
        kpi((t && t.versionBumpsLast7d) || 0, '최근 7일 버전업') +
        kpi(formatBytes(rx + tx), '30일 네트워크') +
        '</div>';

      html += '<h3 class="stats-section-title">네트워크 (30일)</h3>' +
        '<div class="kpi-grid">' +
        kpi(formatBytes(rx), '수신 Rx') +
        kpi(formatBytes(tx), '송신 Tx') +
        kpi(formatBytes((cTotal.rxBytes || 0) + (cTotal.txBytes || 0)), '최근 7일 합계') +
        '</div>';

      if (daily.length) {
        html += '<h3 class="stats-section-title">일별 네트워크 (7일)</h3>' +
          daily.map(d => {
            const bytes = (d.rxBytes || 0) + (d.txBytes || 0);
            const w = Math.round((bytes / dailyMax) * 100);
            return '<div class="bar-row"><span class="bar-name">' + esc(d.day || '') + '</span>' +
              '<span class="bar-track"><span class="bar-fill" style="width:' + w + '%"></span></span>' +
              '<span class="bar-count">' + esc(formatBytes(bytes)) + '</span></div>';
          }).join('');
      }

      html += '<h3 class="stats-section-title">뉴스 통계</h3>' +
        '<div class="kpi-grid">' +
        kpi(newsTotal, '뉴스 전체') +
        kpi(main && main.todayNews != null ? main.todayNews : 0, '오늘 수집') +
        kpi(counts.mac || 0, '🍎 맥 소식') +
        kpi(counts.ai || 0, '✦ AI 소식') +
        kpi(counts.sec || 0, '🛡 보안 소식') +
        '</div>' +
        [['맥 소식', counts.mac || 0], ['AI 소식', counts.ai || 0], ['보안 소식', counts.sec || 0]].map(([label, n]) =>
          '<div class="bar-row"><span class="bar-name">' + label + '</span>' +
          '<span class="bar-track"><span class="bar-fill" style="width:' + Math.round(n / newsMax * 100) + '%"></span></span>' +
          '<span class="bar-count">' + n + '</span></div>'
        ).join('');

      if (cats.length) {
        html += '<h3 class="stats-section-title">카테고리 분포</h3>' +
          cats.map(cname => '<div class="bar-row"><span class="bar-name">' + esc(cname) + '</span>' +
            '<span class="bar-track"><span class="bar-fill" style="width:' + Math.round((t.byCategory[cname] || 0) / maxCat * 100) + '%"></span></span>' +
            '<span class="bar-count">' + (t.byCategory[cname] || 0) + '</span></div>').join('');
      }

      box.innerHTML = html;
    }).catch((e) => {
      console.error('[스토어] 통계 실패', e);
      if (box) box.innerHTML = '<div class="empty-state">통계를 불러오는데 실패했습니다</div>';
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
    requestAnimationFrame(() => {
      // 제목/패널에 포커스 링 없이 다이얼로그 자체만 포커스
      modal.setAttribute('tabindex', '-1');
      modal.focus({ preventScroll: true });
    });
    api('/api/apps/' + encodeURIComponent(id)).then(a => { renderModal(a); })
      .catch((e) => { console.error('[모달] 실패', e); $('modalTitle').textContent = '불러오기 실패'; });
  }
  function closeModal() {
    const modal = $('appModal');
    if (typeof modal.close === 'function') modal.close();
    state.modal.currentId = null;
  }
  function ensureModalTabs() {
    // 탭/언어 버튼은 HTML에 고정 — 동기화만
    syncModalLangBtn();
  }
  function activeMdKeys() {
    const panel = $1('#modalPanels .modal-panel.active') || $('panelIntro');
    if (!panel) return [];
    return Array.from($$('[data-mdtext][data-key]', panel)).map(el => el.dataset.key);
  }
  function syncModalLangBtn() {
    const btn = $('modalLangBtn');
    if (!btn) return;
    const keys = activeMdKeys();
    if (!keys.length) { btn.hidden = true; return; }
    btn.hidden = false;
    const t = mdStore[keys[0]];
    btn.textContent = t && t.showing === 'en' ? '번역 보기' : '원문보기';
  }
  function toggleModalLang() {
    const keys = activeMdKeys();
    if (!keys.length) return;
    const first = mdStore[keys[0]];
    const next = first && first.showing === 'ko' ? 'en' : 'ko';
    keys.forEach(k => {
      const t = mdStore[k];
      if (!t) return;
      t.showing = next;
      const box = $1('[data-mdtext][data-key="' + k + '"]', $('modalPanels'));
      if (box) box.innerHTML = md(t.showing === 'ko' ? t.ko : t.en);
    });
    syncModalLangBtn();
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
    syncModalLangBtn();
  }
  // 게임 스토어 URL: tags 스토어(epic/steam) 우선 — steam-appid가 epic 게임을 스팀으로 못 잡게
  function gameStoreUrl(a) {
    const tags = String(a.tags || '');
    const store = gameStoreOf(a);
    const sources = a.sources || [];
    const pickSrc = (host) => {
      const s = sources.find(x => x.sourceUrl && x.sourceUrl.indexOf(host) >= 0);
      return s ? s.sourceUrl : '';
    };
    const onHost = (url, host) => url && url.indexOf(host) >= 0 ? url : '';
    const steamAppUrl = () => {
      const m = tags.match(/steam-appid:(\d+)/);
      return m ? 'https://store.steampowered.com/app/' + m[1] + '/' : '';
    };
    if (store === 'epic') {
      return pickSrc('epicgames.com') || onHost(a.sourceUrl, 'epicgames.com') ||
        onHost(a.homepageUrl, 'epicgames.com') || '';
    }
    if (store === 'steam') {
      return steamAppUrl() || pickSrc('steampowered.com') || onHost(a.sourceUrl, 'steampowered.com') ||
        a.sourceUrl || onHost(a.homepageUrl, 'steampowered.com') || '';
    }
    if (a.sourceUrl) return a.sourceUrl;
    return steamAppUrl() || pickSrc('epicgames.com') || pickSrc('steampowered.com');
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
    if (hasKoLang(a.supportedLanguages)) pills.push('<span class="pill lang-ko">한국어</span>');
    $('modalMetaPills').innerHTML = pills.join('');
    const cta = [];
    // 스토어 바로가기는 모달 푸터 CTA 1개 (게임 카드의 외부 링크 대체)
    const store = gameStoreOf(a);
    const storeUrl = gameStoreUrl(a);
    const storeLabel = store === 'epic' ? 'Epic에서 보기' : store === 'steam' ? 'Steam에서 보기' : '스토어에서 보기';
    if (storeUrl) cta.push('<a class="btn-primary" href="' + esc(storeUrl) + '" target="_blank" rel="noopener">' + storeLabel + ' ↗</a>');
    if (a.homepageUrl && a.homepageUrl !== storeUrl) {
      cta.push('<a class="' + (storeUrl ? 'btn-secondary' : 'btn-primary') + '" href="' + esc(a.homepageUrl) + '" target="_blank" rel="noopener">홈페이지</a>');
    }
    if (a.repoFullName) cta.push('<a class="btn-secondary" href="https://github.com/' + esc(a.repoFullName) + '" target="_blank" rel="noopener">GitHub</a>');
    if (a.trackId) cta.push('<a class="btn-secondary" href="https://apps.apple.com/us/app/id' + a.trackId + '" target="_blank" rel="noopener">App Store</a>');
    $('modalCta').innerHTML = cta.join('');
    syncModalLangBtn();

    const isGame = isGameApp(a);
    // 게임: 탭 라벨 — 소개 / 언어·요건 / 스크린샷 · 앱: 기존 유지
    $('tabFeatures').textContent = isGame ? '언어·요건' : '특징';
    $('tabChangelog').textContent = isGame ? '스크린샷' : '새 기능';

    // 전문 우선: longDescription, 없으면 descriptionSnippet(마커 포함 레거시)
    // KO 미번역(4000자 초과 등)이면 짧은 descriptionKo만 남고 전문이 숨김 → EN 전문 폴백
    const enBodyFull = a.longDescription || a.descriptionSnippet || '';
    const koLong = a.longDescriptionKo || '';
    const koBodyFull = koLong || a.descriptionKo || '';
    const enFull = a.descriptionSnippet || '';
    const koFull = a.descriptionKo || '';
    const enSplit = splitSysReq(enBodyFull);
    const koSplit = splitSysReq(koBodyFull);
    const legacyEnSplit = splitSysReq(enFull);
    const legacyKoSplit = splitSysReq(koFull);
    const sysreqRaw = enSplit.sysreq || koSplit.sysreq ||
        legacyEnSplit.sysreq || legacyKoSplit.sysreq;

    // 소개 = 짧은 발췌 (descriptionSnippet/ Ko), 세부 = 전문 (longDescription 또는 마커 뒤)
    let introKo = excerpt(koFull) || excerpt(enFull);
    let introEn = excerpt(enFull) || excerpt(koFull);
    let funcKo = '';
    let funcEn = '';

    const bodyFromFull = (splitBody(enSplit.body) || enSplit.body || '').trim();
    const bodyKoFromFull = (splitBody(koSplit.body) || koSplit.body || '').trim();

    if (a.longDescription || a.longDescriptionKo) {
      funcEn = bodyFromFull || a.longDescription || '';
      // KO 전문이 없으면(미번역) funcKo를 비워 EN 전문이 표시되도록 함
      funcKo = koLong ? (bodyKoFromFull || a.longDescriptionKo || '') : '';
      if (!introEn && !introKo) {
        introEn = excerpt(enFull) || excerpt(funcEn);
        introKo = excerpt(koFull) || excerpt(funcKo);
      }
    } else {
      // 레거시: 마커 분리
      introKo = excerpt(koSplit.body) || excerpt(enSplit.body) || introKo;
      introEn = excerpt(enSplit.body) || excerpt(koSplit.body) || introEn;
      funcKo = splitBody(koSplit.body) || '';
      funcEn = splitBody(enSplit.body) || '';
      if (!funcEn && !funcKo) {
        // 마커 없음: 전문 전체를 세부로, 소개는 첫 문단
        const fullEn = (enSplit.body || '').trim();
        const fullKo = (koSplit.body || '').trim();
        if (fullEn.length > 400 || fullKo.length > 400) {
          const cutEn = fullEn.indexOf('\n');
          const cutKo = fullKo.indexOf('\n');
          if (cutEn > 40 && cutEn < fullEn.length - 100) introEn = fullEn.slice(0, cutEn).trim();
          if (cutKo > 40 && cutKo < fullKo.length - 100) introKo = fullKo.slice(0, cutKo).trim();
          funcEn = fullEn;
          funcKo = fullKo;
        }
      }
    }
    // 소개와 세부가 같은 문자열이면 세부 중복 제거 (한쪽만)
    if (funcKo && funcEn && funcKo === funcEn) funcKo = '';
    // intro와 동일한 세부는 제거하되, EN 전문이 intro(KO 짧은소개)와 다르면 유지
    const introShow = introKo || introEn;
    let funcShowKo = funcKo;
    let funcShowEn = funcEn;
    if (funcShowEn && introShow && funcShowEn === introShow) funcShowEn = '';
    if (funcShowKo && introShow && funcShowKo === introShow) funcShowKo = '';
    // KO 전문이 짧은 소개와 동일(=descriptionKo만)이면 EN 전문으로 폴백
    if (funcShowKo && !funcShowEn && introShow && funcShowKo === introShow) funcShowKo = '';
    if (!funcShowKo && !funcShowEn && a.longDescription && a.longDescription !== introShow) {
      funcShowEn = a.longDescription;
    }

    let html = '';
    if (introShow) {
      // KO 짧은소개 + EN 전문 연결 (CHANGELOG: KO+EN concat — 게임·앱 공통)
      if (introKo && funcShowEn && !funcShowKo && introKo !== funcShowEn) {
        html += mdBlock(introKo, introKo);
        if (funcShowEn && funcShowEn !== introKo) {
          html += '<h4 style="margin-top:18px;">세부 설명</h4>';
          html += mdBlock('', funcShowEn);
        }
      } else {
        html += mdBlock(introKo, introEn);
      }
    }
    if (funcShowKo || funcShowEn) {
      // 이미 위에서 concat했으면 중복 스킵
      const already = introKo && funcShowEn && !funcShowKo && introKo !== funcShowEn;
      if (!already) {
        if (introShow) html += '<h4 style="margin-top:18px;">세부 설명</h4>';
        html += mdBlock(funcShowKo, funcShowEn);
      }
    }
    if (!html) html = '<p>소개 정보가 없습니다.</p>';
    $('panelIntro').innerHTML = html;

    if (isGame) {
      $('panelFeatures').innerHTML = '<h4>언어·요건</h4>' + gameLangReqHtml(a, sysreqRaw);
    } else {
      const feats = [];
      if (a.averageRating != null) feats.push('평점 ' + a.averageRating + (a.ratingCount != null ? ' (' + a.ratingCount + '개)' : ''));
      if (a.version) feats.push('버전 ' + esc(a.version));
      if (a.sellerName) feats.push('판매: ' + esc(a.sellerName));
      $('panelFeatures').innerHTML = '<h4>특징</h4>' + (feats.length ? feats.map(f => '<p>' + f + '</p>').join('') : '<p>특징 정보가 없습니다.</p>');
    }

    if (isGame) {
      $('panelChangelog').innerHTML = gameShotsHtml(a);
    } else {
      // 새 기능: 전문 우선 (releaseNotes), 요약은 fallback
      const notesKo = (a.releaseNotesKo || a.releaseNotes || a.releaseNotesSummary) || '';
      const notesEn = (a.releaseNotes || a.releaseNotesSummary || a.releaseNotesKo) || '';
      const notesShow = notesKo || notesEn;
      let newsHtml = '';
      if (notesShow) {
        newsHtml = mdBlock(notesKo, notesEn);
        if (a.version || (a.versions && a.versions.length)) {
          const rows = (a.versions || []).slice(0, 10).map(v => {
            const vlink = v.sourceUrl ? ' <a target="_blank" href="' + esc(v.sourceUrl) + '">열기</a>' : '';
            return '<tr><td>' + esc(v.version) + '</td><td class="notes">' + md(v.notesSummary || '') + '</td><td>' + vlink + '</td></tr>';
          }).join('');
          newsHtml += '<table class="version-table"><tr><th>버전</th><th>새 기능</th><th>링크</th></tr>' +
            (a.version ? '<tr><td><b>' + esc(a.version) + '</b> (현재)' + (a.prevVersion ? ' ← ' + esc(a.prevVersion) + ' 화' : '') + '</td><td class="notes">' + md(a.releaseNotes || a.releaseNotesSummary || '') + '</td><td></td></tr>' : '') + rows + '</table>';
        }
      } else {
        newsHtml = '<p>버전 기록이 없습니다.</p>';
      }
      $('panelChangelog').innerHTML = '<h4>새 기능</h4>' + newsHtml;
    }
  }

  /** 게임: 언어 지원 + 시스템 요구 사항(HTML) + 장르/OS/기본 정보 */
  function gameLangReqHtml(a, sysreqHtml) {
    const langs = langLabels(a.supportedLanguages);
    const parts = [];
    parts.push('<h5 class="mono sm">언어 지원</h5>');
    if (langs.length) {
      parts.push('<div class="lang-chips">' + langs.map(l =>
        '<span class="lang-chip' + (l === '한국어' ? ' ko' : '') + '">' + esc(l) + '</span>'
      ).join('') + '</div>');
    } else {
      parts.push('<p>지원 언어 정보가 없습니다.</p>');
    }
    parts.push('<h5 class="mono sm" style="margin-top:14px;">시스템 요구 사항</h5>');
    if (sysreqHtml) {
      parts.push('<div class="sysreq">' + sanitizeHtml(sysreqHtml) + '</div>');
    } else {
      parts.push('<p>시스템 요구 사항 정보가 없습니다.</p>');
    }
    const meta = [];
    const genres = gameGenresOf(a).filter(g =>
      g !== 'windows' && g !== 'linux' && g !== 'steam' && g !== 'epic' && g !== 'game');
    if (genres.length) meta.push('<p>장르: ' + esc(genres.join(' · ')) + '</p>');
    const tags = String(a.tags || '').split(',').map(s => s.trim());
    const os = [];
    if (tags.indexOf('windows') >= 0) os.push('Windows');
    os.push('macOS');
    if (tags.indexOf('linux') >= 0) os.push('Linux');
    meta.push('<p>플랫폼: ' + esc(os.join(' · ')) + '</p>');
    if (a.developer) meta.push('<p>개발: ' + esc(a.developer) + '</p>');
    if (a.releaseDate) meta.push('<p>출시: ' + esc(fmtDate(a.releaseDate)) + '</p>');
    if (a.license === 'FREE' || a.price === 0) meta.push('<p>가격: 무료</p>');
    if (meta.length) {
      parts.push('<div class="game-meta">' + meta.join('') + '</div>');
    }
    return parts.join('');
  }

  /** 게임: 스크린샷 그리드 (원본 URL 새 탭) */
  function gameShotsHtml(a) {
    const shots = String(a.screenshotUrls || '').split('\n').map(s => s.trim()).filter(Boolean);
    if (!shots.length) {
      return '<h4>스크린샷</h4><p>스크린샷이 없습니다.</p>';
    }
    return '<h4>스크린샷</h4><div class="shot-grid">' + shots.map(u =>
      '<a class="shot-cell" href="' + esc(u) + '" target="_blank" rel="noopener noreferrer">' +
      '<img src="' + esc(u) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' +
      '</a>'
    ).join('') + '</div>';
  }

  /* ================= 뉴스 ================= */
  function fillTicker(tm, itemHtml) {
    if (!tm || !itemHtml || tm.dataset.sig === itemHtml) return;
    tm.dataset.sig = itemHtml;
    const group = '<div class="ticker-group">' + itemHtml + '</div>';
    // 뷰 폭의 2배 이상이 되도록 그룹 복제 (짧은 목록도 빈틈 없이 루프)
    tm.innerHTML = group + group;
    requestAnimationFrame(() => {
      if (tm.dataset.sig !== itemHtml) return;
      const viewW = (tm.parentElement && tm.parentElement.clientWidth) || 0;
      const groupW = tm.firstElementChild ? tm.firstElementChild.getBoundingClientRect().width : 0;
      const copies = Math.max(2, viewW && groupW ? Math.ceil((viewW * 2) / groupW) + 1 : 2);
      if (copies > 2) {
        tm.innerHTML = group.repeat(copies);
        // N개 동일 그룹 → 한 그룹씩 이동 = -100%/N
        const pct = (100 / copies).toFixed(4);
        tm.style.animation = 'none';
        void tm.offsetWidth;
        tm.style.animation = `marquee ${Math.max(20, copies * 5)}s linear infinite`;
        tm.style.setProperty('--marquee-shift', `-${pct}%`);
      }
    });
    $$('.ticker-item', tm).forEach(el => {
      el.onclick = () => openDashboardNews(el.dataset.main, el.dataset.id);
    });
  }

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
    const crumb = $('newsCrumb');
    if (crumb) crumb.textContent = '/news / ' + state.news.main;
    const subs = NEWS_SUBS[state.news.main] || ['전체'];
    const html = subs.map(s =>
      '<button type="button" class="chip' + ((state.news.sub || '전체') === s ? ' active' : '') +
      '" data-sub="' + esc(s) + '">' + esc(s) + '</button>'
    ).join('');
    const sc1 = $('newsSubChips');
    if (sc1) sc1.innerHTML = html;
    const sc2 = $('newsSubChips2');
    if (sc2) sc2.innerHTML = html;
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
    if (n3) {
      n3.hidden = hybrid;
      n3.classList.toggle('has-detail', !!state.news.detailId);
    }
    $$('#layoutToggle .ltbtn').forEach(b => b.classList.toggle('active', b.dataset.layout === state.news.layout));
  }
  function loadNews() {
    syncNewsMains();
    applyNewsLayout();
    renderSourceChoices('newsSourceChoices', 'news', state.news.sourceId, sid => {
      state.news.sourceId = sid;
      state.news.page = 1;
      state.news.detailId = null;
      loadNews();
    });
    const seq = ++newsSeq;
    Promise.all([
      api('/api/main').catch(e => { console.error('[뉴스] /api/main 실패', e); return null; }),
      api(buildNewsQuery()).catch(e => { console.error('[뉴스] 목록 실패', e); return null; })
    ]).then(([main, list]) => {
      if (seq !== newsSeq) return;
      if (main) renderNewsChrome(main);
      if (state.news.layout === 'A') {
        if (list) renderHybridList(list);
      } else if (list) renderNewsList(list);
    });
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
          state.news.page = 1;
          syncNewsMains();
        }
        setNewsLayout('B');
        applyNewsLayout();
        loadNews();
      };
    });
    renderPagination($('hybridPagination'), d.page, d.pageSize, d.total, p => {
      state.news.page = p; loadNews(); window.scrollTo(0, 0);
    });
  }

  function renderNewsChrome(d) {
    const c = d.counts || {};
    const nMac = $('ncount-mac'), nAi = $('ncount-ai'), nSec = $('ncount-sec'), topNews = $('topNewsCount');
    if (nMac) nMac.textContent = c.mac || 0;
    if (nAi) nAi.textContent = c.ai || 0;
    if (nSec) nSec.textContent = c.sec || 0;
    const total = (c.mac || 0) + (c.ai || 0) + (c.sec || 0);
    if (topNews) topNews.textContent = total;
    setNavCounts({ news: total });
    // 티커 (최신 헤드라인) — 내용이 같으면 다시 그리지 않아 애니메이션 재시작 방지
    const heads = [...(d.mac || []), ...(d.ai || []), ...(d.sec || [])].slice(0, 8);
    const itemHtml = heads.map(n =>
      '<button type="button" class="ticker-item" data-main="' + esc(n.main || 'mac') + '" data-id="' + esc(n.id) + '"><b>' + esc(newsTitle(n)) + '</b><span class="srcbadge">' + esc(n.sourceName) + '</span></button>'
    ).join('');
    fillTicker($('tickerMove'), itemHtml);
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
        '<span><b>' + esc(a.name) + '</b><small>' + esc(a.category || '') + ' · ' + priceBadge(a) + '</small></span>' +
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
    if (state.news.sourceId) p.set('sourceId', state.news.sourceId);
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
    else if (d.news.length) openNewsDetail(d.news[0].id, true);
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
    applyNewsLayout();
    $$('#newsList .ncard').forEach(el => el.classList.toggle('sel', el.dataset.id === id));
    const box = $('newsDetail');
    if (!silent && box) box.scrollIntoView({ block: 'nearest' });
    // A형에서는 상세가 숨김 → B형 강제 전환 후 목록 로드 (재진입은 B라 무한루프 없음)
    if (state.news.layout === 'A') {
      setNewsLayout('B');
      applyNewsLayout();
      loadNews();
      return;
    }
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
    const sc1 = $('newsSubChips');
    if (sc1) sc1.innerHTML = '';
    const sc2 = $('newsSubChips2');
    if (sc2) sc2.innerHTML = '';
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

  /* ---------- 알림 (설정 서랍 알림 탭) ---------- */
  function loadNotifList() {
    const box = $('notifList');
    if (!box) return;
    box.innerHTML = '<p class="tiny-note">불러오는 중…</p>';
    api('/api/notifications?page=1&pageSize=30').then(d => {
      const list = d.notifications || [];
      if (!list.length) {
        box.innerHTML = '<div class="empty-state">알림이 없습니다</div>';
        return;
      }
      box.innerHTML = list.map(n =>
        '<p class="nrow-admin"><span style="flex:1"><b>' + esc(n.type) + '</b><br>' + esc(n.summary) +
        '<br><small class="muted">' + fmtDate(n.createdAt) + (n.isRead ? '' : ' · <b>안읽음</b>') + '</small></span>' +
        (n.isRead ? '' : '<button type="button" data-nread="' + n.id + '">읽음</button>') +
        '<button type="button" data-ndel="' + n.id + '">삭제</button></p>'
      ).join('');
      $$('#notifList [data-nread]').forEach(b => {
        b.onclick = () => {
          apiPost('/api/notifications/' + b.dataset.nread + '/read')
            .then(() => { toast('읽음 처리'); loadNotifList(); })
            .catch(() => toast('실패'));
        };
      });
      $$('#notifList [data-ndel]').forEach(b => {
        b.onclick = () => {
          apiDel('/api/notifications/' + b.dataset.ndel)
            .then(() => { toast('삭제됨'); loadNotifList(); })
            .catch(() => toast('실패'));
        };
      });
    }).catch(() => {
      box.innerHTML = '<div class="empty-state">불러오기 실패</div>';
    });
  }

  /* ---------- 설정 서랍 (설정·알림·통계) ---------- */
  const ADMIN_INTERVALS = [15, 30, 60, 120, 360, 720, 1440, 10080];
  let adminTab = 'settings';

  function switchAdminTab(tab) {
    adminTab = tab === 'notif' || tab === 'stats' ? tab : 'settings';
    $$('#adminTabs .drawer-tab').forEach(b => {
      const on = b.dataset.atab === adminTab;
      b.classList.toggle('active', on);
      b.setAttribute('aria-selected', on ? 'true' : 'false');
    });
    const show = (id, on) => { const el = $(id); if (el) el.hidden = !on; };
    show('atabSettings', adminTab === 'settings');
    show('atabNotif', adminTab === 'notif');
    show('atabStats', adminTab === 'stats');
    if (adminTab === 'settings') {
      loadAdminSettings();
      loadAdminSources();
    } else if (adminTab === 'notif') {
      loadNotifList();
    } else {
      loadStatsPanel();
    }
  }

  function openAdmin(tab) {
    $('adminDrawer').hidden = false;
    $('adminBackdrop').hidden = false;
    switchAdminTab(tab || adminTab || 'settings');
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
  function srcRowHtml(s) {
    return '<div class="srcrow"><span class="sname" title="' + esc(s.baseUrl || '') + '">' + esc(s.name) + '</span>' +
      '<span class="sstatus">' + esc(s.lastStatus || '') + '</span>' +
      '<select data-sint="' + esc(s.id) + '">' + ADMIN_INTERVALS.map(m =>
        '<option value="' + m + '"' + (s.intervalMinutes === m ? ' selected' : '') + '>' + m + '분</option>'
      ).join('') + '</select>' +
      '<button type="button" data-ssync="' + esc(s.id) + '">수집</button>' +
      '<button type="button" data-stoggle="' + esc(s.id) + '" class="' + (s.enabled ? 'on' : 'off') + '">' +
      (s.enabled ? 'ON' : 'OFF') + '</button></div>';
  }
  function loadAdminSources() {
    const empty = '<p class="tiny-note">소스 없음</p>';
    const fail = '<p class="tiny-note">불러오기 실패</p>';
    const GAME_TYPES = ['STEAM_FREETOMAC', 'EPIC_FREE', 'APPSTORRENT_GAMES'];
    const NEWS_TYPES = ['NEWS_RSS'];
    const COMMUNITY_TYPES = ['COMMUNITY_BOARD'];
    const isCommunity = s => COMMUNITY_TYPES.indexOf(s.type) >= 0;
    const isNews = s => NEWS_TYPES.indexOf(s.type) >= 0;
    const isGame = s => GAME_TYPES.indexOf(s.type) >= 0;
    api('/api/watchlist').then(srcs => {
      const community = srcs.filter(isCommunity);
      const news = srcs.filter(s => isNews(s) && !isCommunity(s));
      const games = srcs.filter(s => isGame(s) && !isNews(s) && !isCommunity(s));
      const apps = srcs.filter(s => !isCommunity(s) && !isNews(s) && !isGame(s));
      $('adminSourcesApps').innerHTML = apps.length ? apps.map(srcRowHtml).join('') : empty;
      $('adminSourcesGames').innerHTML = games.length ? games.map(srcRowHtml).join('') : empty;
      $('adminSourcesCommunity').innerHTML = community.length ? community.map(srcRowHtml).join('') : empty;
      $('adminSourcesNews').innerHTML = news.length ? news.map(srcRowHtml).join('') : empty;
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
    }).catch(() => {
      $('adminSourcesApps').innerHTML = fail;
      $('adminSourcesGames').innerHTML = fail;
      $('adminSourcesCommunity').innerHTML = fail;
      $('adminSourcesNews').innerHTML = fail;
    });
  }

  /* ---------- 커뮤니티 (PLAN_v23) ---------- */
  const COMMUNITY_MAINS = [
    { id: 'apple', label: '애플' },
    { id: 'mac', label: '맥' },
    { id: 'ai', label: 'AI' },
    { id: 'all', label: '전체' }
  ];
  const CM_PAGE_SIZE = 5;
  let communitySeq = 0;
  let cmPendingEdge = null;

  function goCommunityPage(target, edge) {
    const pages = Math.max(1, Math.ceil((state.community.total || 0) / CM_PAGE_SIZE));
    if (target < 1 || target > pages) return;
    state.community.page = target;
    cmPendingEdge = edge || null;
    state.community.detailId = null;
    loadCommunity();
    window.scrollTo(0, 0);
  }

  function syncCommunityMains() {
    $$('#communityMains .mainpill').forEach(b => {
      const on = b.dataset.main === state.community.main;
      b.classList.toggle('active', on);
      if (on) b.setAttribute('aria-selected', 'true'); else b.removeAttribute('aria-selected');
    });
    const label = COMMUNITY_MAINS.find(m => m.id === state.community.main);
    const t = $('cmListTitle');
    if (t) t.textContent = String(state.community.main).toUpperCase() + ' / ' + ((label && label.label) || '');
  }

  function buildCommunityQuery() {
    const c = state.community;
    const p = new URLSearchParams({ page: String(c.page), pageSize: String(CM_PAGE_SIZE) });
    if (c.main && c.main !== 'all') p.set('main', c.main);
    if (c.q) p.set('q', c.q);
    if (c.sourceId) p.set('sourceId', c.sourceId);
    return '/api/community?' + p.toString();
  }

  function communityRowHtml(p) {
    const age = Date.now() - (p.publishedAt || 0);
    let badges = '';
    if (age < 6 * 3600 * 1000) badges = '<span class="newtag">NEW</span>';
    return '<button type="button" class="nrow cmrow" data-id="' + esc(p.id) + '" data-main="' + esc(p.main) + '" role="listitem">' +
      '<span class="nbody"><span class="nmeta"><span class="subpill">' + esc(cmMainLabel(p.main)) + '</span>' +
      '<span>' + esc(p.sourceName) + (p.authorName ? ' · ' + esc(p.authorName) : '') + ' · ' + fmtTime(p.publishedAt) + '</span>' +
      badges +
      (p.commentCount != null ? '<span class="mono">💬' + p.commentCount + '</span>' : '') +
      '</span>' +
      '<span class="ntitle">' + esc(p.title) + '</span>' +
      (p.summary ? '<span class="ndesc">' + esc(p.summary) + '</span>' : '') +
      '</span>' +
      '<span class="narrow" aria-hidden="true">↗</span></button>';
  }

  function cmMainLabel(id) {
    const m = COMMUNITY_MAINS.find(x => x.id === id);
    return m ? m.label : id;
  }

  function renderCommunityList(d) {
    const box = $('communityList');
    const empty = $('emptyCommunity');
    const posts = d.posts || [];
    state.community.total = d.total || 0;
    state.community.listIds = posts.map(p => p.id);
    if (state.community.detailId && state.community.listIds.indexOf(state.community.detailId) < 0) {
      state.community.detailId = null;
    }
    if (!posts.length) {
      box.innerHTML = '<div class="mini-empty">글이 없습니다</div>';
      if (empty) empty.hidden = (d.total || 0) > 0;
      const det = $('communityDetail');
      if (det) det.innerHTML = '';
      return;
    }
    if (empty) empty.hidden = true;
    box.innerHTML = posts.map(communityRowHtml).join('');
    $$('.cmrow', box).forEach(el => {
      el.onclick = () => openCommunityDetail(el.dataset.id);
    });
    if (!state.community.detailId && !cmPendingEdge) {
      state.community.detailId = posts[0].id;
    }
    if (cmPendingEdge) {
      const pick = cmPendingEdge === 'first' ? state.community.listIds[0] : state.community.listIds[state.community.listIds.length - 1];
      cmPendingEdge = null;
      if (pick) state.community.detailId = pick;
    }
    openCommunityDetail(state.community.detailId, true);
    renderCommunityPagination(d);
  }

  function renderCommunityPagination(d) {
    const nav = $('communityPagination');
    if (!nav) return;
    const total = d.total || 0;
    const pages = Math.max(1, Math.ceil(total / CM_PAGE_SIZE));
    if (pages <= 1) { nav.innerHTML = ''; return; }
    const cur = state.community.page;
    let html = '';
    html += '<button type="button" class="pagebtn"' + (cur <= 1 ? ' disabled' : '') + ' data-p="' + (cur - 1) + '">이전</button>';
    const start = Math.max(1, cur - 2);
    const end = Math.min(pages, start + 4);
    for (let i = start; i <= end; i++) {
      html += '<button type="button" class="pagebtn' + (i === cur ? ' active' : '') + '" data-p="' + i + '">' + i + '</button>';
    }
    html += '<button type="button" class="pagebtn"' + (cur >= pages ? ' disabled' : '') + ' data-p="' + (cur + 1) + '">다음</button>';
    nav.innerHTML = html;
    $$('.pagebtn', nav).forEach(b => {
      b.onclick = () => {
        const p = parseInt(b.dataset.p, 10);
        if (!p || p === state.community.page) return;
        state.community.page = p;
        state.community.detailId = null;
        loadCommunity();
      };
    });
  }

  function openCommunityDetail(id, skipListHighlight) {
    if (!id) return;
    state.community.detailId = id;
    $$('.cmrow').forEach(el => el.classList.toggle('active', el.dataset.id === id));
    const det = $('communityDetail');
    if (!det) return;
    det.innerHTML = '<p class="tiny-note">불러오는 중…</p>';
    api('/api/community/' + encodeURIComponent(id)).then(p => {
      if (state.community.detailId !== id) return;
      const idx = state.community.listIds.indexOf(id);
      const lastOnPage = idx >= 0 && idx === state.community.listIds.length - 1;
      const firstOnPage = idx === 0;
      const totalPages = Math.max(1, Math.ceil((state.community.total || 0) / CM_PAGE_SIZE));
      const hasPrev = idx > 0 || (firstOnPage && state.community.page > 1);
      const hasNext = (idx >= 0 && idx < state.community.listIds.length - 1) || (lastOnPage && state.community.page < totalPages);
      det.innerHTML =
        '<header class="ndetail-head">' +
        '<div class="nmeta"><span class="subpill">' + esc(cmMainLabel(p.main)) + '</span>' +
        '<span>' + esc(p.sourceName) + ' · ' + fmtTime(p.publishedAt) + '</span></div>' +
        '<h2 class="ndtitle">' + esc(p.title) + '</h2>' +
        '<div class="ndmeta">' + esc([p.authorName, p.commentCount != null ? ('댓글 ' + p.commentCount) : ''].filter(Boolean).join(' · ')) + '</div>' +
        '</header>' +
        (p.thumbnailUrl ? '<img class="ndthumb" src="' + esc(p.thumbnailUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' : '') +
        (p.contentHtml
          ? '<div class="ndbody">' + p.contentHtml + '</div>'
          : (p.summary ? '<div class="ndbody"><p>' + esc(p.summary) + '</p></div>' : '')) +
        '<footer class="ndfoot"><a class="btn-primary" href="' + esc(p.originalUrl) + '" target="_blank" rel="noopener noreferrer">원문 보기 ↗</a></footer>' +
        '<nav class="nd-nav" aria-label="글 이전/다음">' +
        '<button type="button" class="nd-prev' + (hasPrev ? '' : ' disabled') + '" data-dir="-1"' + (hasPrev ? '' : ' disabled') + '>‹ 이전 글</button>' +
        '<span class="nd-navpos mono">' + (idx >= 0 ? (idx + 1) + ' / ' + state.community.listIds.length : '') + '</span>' +
        '<button type="button" class="nd-next' + (hasNext ? '' : ' disabled') + '" data-dir="1"' + (hasNext ? '' : ' disabled') + '>다음 글 ›</button></nav>';
      $$('.nd-nav [data-dir]', det).forEach(b => {
        b.onclick = () => {
          const i = state.community.listIds.indexOf(id) + (b.dataset.dir === '1' ? 1 : -1);
          if (i >= 0 && i < state.community.listIds.length) openCommunityDetail(state.community.listIds[i]);
          else if (i < 0) goCommunityPage(state.community.page - 1, 'last');
          else goCommunityPage(state.community.page + 1, 'first');
        };
      });
      if (!skipListHighlight) window.scrollTo(0, 0);
    }).catch(e => {
      console.error('[커뮤니티] 상세 실패', e);
      det.innerHTML = '<p class="tiny-note">상세를 불러오지 못했습니다</p>';
    });
  }

  function loadCommunity() {
    syncCommunityMains();
    renderSourceChoices('cmSourceChoices', 'community', state.community.sourceId, sid => {
      state.community.sourceId = sid;
      state.community.page = 1;
      state.community.detailId = null;
      loadCommunity();
    });
    const seq = ++communitySeq;
    Promise.all([
      api(buildCommunityQuery()).catch(e => { console.error('[커뮤니티] 목록 실패', e); return null; }),
      api('/api/community/counts').catch(() => null)
    ]).then(([list, counts]) => {
      if (seq !== communitySeq) return;
      if (list) renderCommunityList(list);
      if (counts) {
        ['apple', 'mac', 'ai', 'all'].forEach(k => {
          const el = $('cmcount-' + k);
          if (el) el.textContent = counts[k] != null ? counts[k] : '-';
        });
        setNavCounts({ community: counts.all });
      }
    });
  }

  /* ---------- 초기화 ---------- */
  function init() {
    // 메인 nav
    $$('#mainNav .navpill').forEach(b => { b.onclick = () => switchView(b.dataset.view); });
    $$('#mobileNav .navpill').forEach(b => { b.onclick = () => switchView(b.dataset.view); });
    $('brandLink').onclick = e => { e.preventDefault(); switchView('dashboard'); };
    // 앱스토어 서브탭
    $$('#view-appstore .subpill').forEach(b => { b.onclick = () => switchStoreSub(b.dataset.sub); });
    // 커뮤니티 카테고리
    $$('#communityMains .mainpill').forEach(b => { b.onclick = () => {
      state.community.main = b.dataset.main;
      state.community.page = 1;
      state.community.detailId = null;
      loadCommunity();
    }; });
    const cmQ = $('communityQ');
    if (cmQ) {
      let cmQTimer = null;
      cmQ.oninput = () => {
        clearTimeout(cmQTimer);
        cmQTimer = setTimeout(() => {
          state.community.q = cmQ.value.trim();
          state.community.page = 1;
          state.community.detailId = null;
          loadCommunity();
        }, 350);
      };
    }
    // 뉴스 TopTab
    $$('#topTabs .toptab').forEach(b => { b.onclick = () => switchNewsTop(b.dataset.top); });
    // 뉴스 main 탭
    $$('#newsMains .mainpill').forEach(b => { b.onclick = () => switchNewsMain(b.dataset.main); });
    // R49: A/B 레이아웃 토글 (localStorage 기억)
  if ($$('layoutToggle .ltbtn').length) {
    $$('#layoutToggle .ltbtn').forEach(b => {
      b.onclick = () => {
        setNewsLayout(b.dataset.layout);
        state.news.detailId = null;
        state.news.page = 1;
        loadNews();
      };
    });
  }
    // 메인 더보기
    $('dashAppsMore').onclick = () => switchView('appstore');
    const dashGamesMore = $('dashGamesMore');
    if (dashGamesMore) dashGamesMore.onclick = () => switchView('games');
    $('dashLoadMore').onclick = () => {
      state.dashPage++;
      ['mac', 'ai', 'sec'].forEach(m => {
        const p = new URLSearchParams({ main: m, page: state.dashPage, pageSize: 4 });
        api('/api/news?' + p.toString()).then(d => {
          const box = $('grows-' + m);
          box.innerHTML += (d.news || []).map(n => newsRowHtml(n)).join('');
          $$('.nrow', box).forEach(el => {
            el.onclick = (ev) => {
              const mini = ev.target.closest('[data-app]');
              if (mini) { ev.stopPropagation(); openModal(mini.dataset.app); return; }
              openDashboardNews(el.dataset.main, el.dataset.id);
            };
          });
        }).catch((e) => { console.error('[대시보드] 더보기 실패', e); });
      });
    };
    // 통합 검색 (앱 + 뉴스 → 드롭다운)
    let searchTimer = null;
    let searchSeq = 0;
    const searchInput = $('globalSearch');
    const searchDrop = $('searchDrop');

    function closeSearchDrop() {
      searchDrop.hidden = true;
      searchDrop.innerHTML = '';
    }
    function searchIconHtml(a) {
      if (a.iconUrl) return '<img class="sd-icon" src="' + esc(a.iconUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">';
      const ch = (a.name || '?').trim().charAt(0).toUpperCase();
      return '<span class="sd-icon text" style="background:' + iconBg(a.name) + '">' + esc(ch) + '</span>';
    }
    function renderSearchDrop(q, apps, news) {
      if (!q) { closeSearchDrop(); return; }
      const appRows = (apps || []).slice(0, 6);
      const newsRows = (news || []).slice(0, 6);
      if (!appRows.length && !newsRows.length) {
        searchDrop.innerHTML = '<div class="sd-empty">“' + esc(q) + '” 결과 없음</div>';
        searchDrop.hidden = false;
        return;
      }
      let html = '';
      if (appRows.length) {
        html += '<div class="sd-head">앱</div>' + appRows.map(a =>
          '<button type="button" class="sd-item" data-kind="app" data-id="' + esc(a.id) + '" role="option">' +
          searchIconHtml(a) +
          '<span class="sd-title">' + esc(a.name) + '</span>' +
          '<span class="sd-sub">' + esc(a.category || '') + '</span></button>'
        ).join('');
      }
      if (newsRows.length) {
        html += '<div class="sd-head">뉴스</div>' + newsRows.map(n =>
          '<button type="button" class="sd-item" data-kind="news" data-id="' + esc(n.id) + '" data-main="' + esc(n.main || 'mac') + '" role="option">' +
          '<span class="sd-icon text" style="background:#444">' + esc(logoText(n.sourceName)) + '</span>' +
          '<span class="sd-title">' + esc(newsTitle(n)) + '</span>' +
          '<span class="sd-sub">' + esc(n.main || '') + '</span></button>'
        ).join('');
      }
      searchDrop.innerHTML = html;
      searchDrop.hidden = false;
      $$('.sd-item', searchDrop).forEach(btn => {
        btn.onclick = () => {
          closeSearchDrop();
          searchInput.blur();
          if (btn.dataset.kind === 'app') openModal(btn.dataset.id);
          else openDashboardNews(btn.dataset.main, btn.dataset.id);
        };
      });
    }
    function runUnifiedSearch(q) {
      const seq = ++searchSeq;
      if (!q) {
        searchDrop.innerHTML = '<div class="sd-empty">앱·뉴스를 함께 검색합니다</div>';
        searchDrop.hidden = false;
        return;
      }
      searchDrop.innerHTML = '<div class="sd-empty">검색 중…</div>';
      searchDrop.hidden = false;
      Promise.all([
        api('/api/apps?q=' + encodeURIComponent(q) + '&page=1&pageSize=6&sort=newest').catch(() => null),
        api('/api/news?q=' + encodeURIComponent(q) + '&page=1&pageSize=6').catch(() => null)
      ]).then(([ad, nd]) => {
        if (seq !== searchSeq) return;
        const cur = searchInput.value.trim();
        if (cur !== q) return;
        renderSearchDrop(q, ad && ad.apps, nd && nd.news);
      });
    }
    searchInput.addEventListener('input', e => {
      clearTimeout(searchTimer);
      const q = e.target.value.trim();
      searchTimer = setTimeout(() => runUnifiedSearch(q), 250);
    });
    searchInput.addEventListener('focus', () => {
      runUnifiedSearch(searchInput.value.trim());
    });
    searchInput.addEventListener('keydown', e => {
      if (e.key === 'Escape') { closeSearchDrop(); searchInput.blur(); return; }
      if (e.key === 'Enter') {
        e.preventDefault();
        const first = $1('.sd-item', searchDrop);
        if (first) first.click();
        return;
      }
      if (e.key === 'ArrowDown') {
        const first = $1('.sd-item', searchDrop);
        if (first) { e.preventDefault(); first.focus(); }
      }
    });
    document.addEventListener('click', e => {
      if (!e.target.closest || !e.target.closest('.searchbox')) closeSearchDrop();
    });
    document.addEventListener('keydown', e => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); $('globalSearch').focus(); }
    });
    // 정렬/필터 (앱 스토어 — 사이드바·칩은 renderStoreSidebar/renderStoreChips에서 배선)
    // 헤더
    $('adminBtn').onclick = () => openAdmin();
    $('adminClose').onclick = closeAdmin;
    $('adminBackdrop').onclick = closeAdmin;
    $$('#adminTabs .drawer-tab').forEach(b => { b.onclick = () => switchAdminTab(b.dataset.atab); });
    $('adminSettingsForm').onsubmit = saveAdminSettings;
    $('adminSyncAll').onclick = () => {
      apiPost('/api/sync', {}).then(() => toast('전체 수집 요청됨')).catch(() => toast('수집 요청 실패'));
    };
    $('adminTranslateNow').onclick = () => {
      apiPost('/api/translate', {}).then(() => toast('번역 요청됨')).catch(() => toast('번역 요청 실패'));
    };
    $('adminNotifReadAll').onclick = () => {
      apiPost('/api/notifications/read-all').then(() => { toast('모두 읽음'); loadNotifList(); }).catch(() => toast('실패'));
    };
    $('adminNotifCleanup').onclick = () => {
      apiPost('/api/notifications/cleanup').then(d => { toast('정리됨 ' + (d.deleted || 0) + '건'); loadNotifList(); }).catch(() => toast('실패'));
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
        else if (!$('adminDrawer') || !$('adminDrawer').hidden) loadStatsPanel();
      } else if (state.view === 'games') {
        loadGamesList();
      } else if (state.view === 'news') {
        if (state.news.detailId) openNewsDetail(state.news.detailId, true);
        else { applyNewsLayout(); loadNews(); }
      }
      if (state.modal.currentId) openModal(state.modal.currentId, true);
    };
    // 폰트 크기 조절 (localStorage 기억)
    let fontPx = FONT_DEFAULT;
    try {
      const saved = parseInt(localStorage.getItem(FONT_KEY), 10);
      if (saved >= FONT_MIN && saved <= FONT_MAX) fontPx = saved;
    } catch (e) {}
    const applyFont = (px) => {
      fontPx = Math.min(FONT_MAX, Math.max(FONT_MIN, px));
      document.documentElement.style.setProperty('--fs', fontPx + 'px');
      try { localStorage.setItem(FONT_KEY, String(fontPx)); } catch (e) {}
      toast('글자 크기 ' + fontPx + 'px');
    };
    document.documentElement.style.setProperty('--fs', fontPx + 'px');
    $('fontDown').onclick = () => applyFont(fontPx - 1);
    $('fontUp').onclick = () => applyFont(fontPx + 1);
    $('modalClose').onclick = () => { if (typeof $('appModal').close === 'function') $('appModal').close(); };
    $('appModal').addEventListener('click', e => { if (e.target === $('appModal')) $('appModal').close(); });
    $$('#modalTabs [role="tab"]').forEach(btn => { btn.onclick = () => setActiveTab(btn.dataset.tab); });
    const langBtn = $('modalLangBtn');
    if (langBtn) langBtn.onclick = toggleModalLang;
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
      const topApp = $('topAppCount');
      if (topApp) topApp.textContent = d.total;
      setNavCounts({ app: d.total });
    }).catch(() => {});
    api('/api/games?page=1&pageSize=1').then(d => {
      setNavCounts({ games: d.total });
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
    if (part.games != null) {
      ['navGameCount', 'navGameCountM'].forEach(id => { const el = $(id); if (el) el.textContent = part.games; });
    }
    if (part.community != null) {
      ['navCommunityCount', 'navCommunityCountM'].forEach(id => { const el = $(id); if (el) el.textContent = part.community; });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
