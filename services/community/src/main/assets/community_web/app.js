"use strict";
const state = { categoryId: null, sourceIds: new Set(), q: "", sort: "latest", page: 1, pageSize: 30, total: 0, categories: [], catCounts: {}, sources: [], sites: [], checkedSites: new Set() };
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

async function api(path, opts) {
  const res = await fetch(path, opts);
  if (!res.ok) throw new Error("HTTP " + res.status);
  return res.json();
}

/* ---------- R44 관리 토큰: 쓰기 API 자동 첨부 + 401 시 입력·재시도 ---------- */
(function () {
  const KEY = "jupjup_admin_token_3040";
  const origFetch = window.fetch.bind(window);
  window.fetch = async function (input, init) {
    const url = typeof input === "string" ? input : (input && input.url) || "";
    const method = ((init && init.method) || (input && input.method) || "GET").toUpperCase();
    if (url.indexOf("/api/") !== 0 || method === "GET" || method === "HEAD") return origFetch(input, init);
    let tok = "";
    try { tok = localStorage.getItem(KEY) || ""; } catch (e) {}
    const headers = {};
    if (init && init.headers) {
      if (init.headers.forEach) init.headers.forEach((v, k) => { headers[k] = v; });
      else for (const k in init.headers) headers[k] = init.headers[k];
    }
    if (tok) headers["X-Auth-Token"] = tok;
    const patched = Object.assign({}, init || {}, { method, headers });
    let r = await origFetch(input, patched);
    if (r.status !== 401) return r;
    const v = prompt("관리 토큰을 입력하세요 (기기 내 브라우저에서 http://127.0.0.1:3040/api/admin/token 조회)");
    if (!v) return r;
    try { localStorage.setItem(KEY, v.trim()); } catch (e) {}
    patched.headers["X-Auth-Token"] = v.trim();
    return origFetch(input, patched);
  };
})();

function toast(msg) {
  const t = $("toast");
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(t._h);
  t._h = setTimeout(() => { t.hidden = true; }, 2500);
}

function srcColor(name) {
  let h = 0;
  for (const c of name) h = (h * 31 + c.codePointAt(0)) % 360;
  return "hsl(" + h + ",55%,45%)";
}

function fmtTime(ts) {
  if (!ts) return "";
  const d = new Date(ts);
  const diff = Date.now() - ts;
  if (diff < 3600000) return Math.max(1, Math.floor(diff / 60000)) + "분 전";
  if (diff < 86400000) return Math.floor(diff / 3600000) + "시간 전";
  return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
}

function dealBadge(p) {
  if (p.categoryId === 7 && p.salePrice) {
    const rate = p.discountRate ? " " + p.discountRate + "%↓" : "";
    return '<span class="pill pill-hot">' + rate + " " + Number(p.salePrice).toLocaleString() + "원</span>";
  }
  if (p.categoryId === 8 && p.dealStatus) {
    const label = { selling: "판매중", sold: "판매완료", reserved: "예약중" }[p.dealStatus] || p.dealStatus;
    return '<span class="pill pill-used">' + esc(label) + "</span>";
  }
  return "";
}

function thumbSrc(url) {
  return "/api/thumb?url=" + encodeURIComponent(url);
}

function cardHtml(p) {
  const counts = [p.likeCount != null ? "♥" + p.likeCount : "", p.commentCount != null ? "💬" + p.commentCount : "", p.viewCount != null ? "👁" + p.viewCount : "", p.imageCount > 0 ? "🖼" + p.imageCount : ""].filter(Boolean).join(" ");
  const thumb = p.thumbnailUrl ? '<img class="thumb" src="' + esc(thumbSrc(p.thumbnailUrl)) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' : "";
  return '<article class="card" data-id="' + p.id + '"><div class="card-body">' +
    '<div class="card-top"><span class="badge-src" style="background:' + srcColor(p.sourceName || "?") + '">' + esc((p.sourceName || "?").slice(0, 1)) + "</span>" +
    "<span>" + esc(p.sourceName || "") + (p.boardName ? " · " + esc(p.boardName) : "") + "</span>" +
    '<span class="pill pill-cat">' + esc(p.categoryName || "") + "</span>" + dealBadge(p) + "</div>" +
    "<h3>" + esc(p.title) + "</h3>" +
    (p.summary ? '<div class="summary">' + esc(p.summary) + "</div>" : "") +
    '<div class="card-meta"><span>' + esc(p.author || "") + "</span><span>" + fmtTime(p.publishedAt || p.collectedAt) + "</span><span>" + esc(counts) + "</span></div>" +
    "</div>" + thumb + "</article>";
}

// 체크된 사이트의 소스 ID 집합 (전체 선택과 동일하면 빈 집합 = 파라미터 생략)
function effectiveSourceIds() {
  if (state.checkedSites.size === 0 || state.checkedSites.size >= state.sites.length || state.sites.length === 0) return new Set();
  const ids = new Set();
  state.sites.forEach((site) => {
    if (state.checkedSites.has(site.domain)) (site.sourceIds || []).forEach((id) => ids.add(id));
  });
  return ids;
}

function queryString(reset) {
  if (reset) state.page = 1;
  const sp = new URLSearchParams();
  if (state.categoryId) sp.set("category_id", state.categoryId);
  effectiveSourceIds().forEach((id) => sp.append("source_id", id));
  if (state.q) sp.set("q", state.q);
  sp.set("sort", state.sort);
  sp.set("page", state.page);
  sp.set("pageSize", state.pageSize);
  return sp.toString();
}

async function loadFeed(append) {
  const data = await api("/api/posts?" + queryString(!append));
  const posts = data.posts || [];
  state.total = data.total || 0;
  const feed = $("feed");
  if (!append) feed.innerHTML = "";
  feed.insertAdjacentHTML("beforeend", posts.map(cardHtml).join(""));
  feed.querySelectorAll(".card").forEach((el) => {
    el.onclick = () => openDetail(Number(el.dataset.id));
  });
  $("feedMeta").textContent = "검색 결과 " + state.total + "건" + (state.categoryId ? " · " + catName(state.categoryId) : "");
  $("btnMore").style.display = posts.length < state.pageSize ? "none" : "";
}

function catName(id) {
  const c = state.categories.find((c) => c.id === id);
  return c ? c.name : "";
}

// 왼쪽 카테고리 목록 (메인 내비, 건수 포함)
async function loadCategories() {
  const [cats, overview] = await Promise.all([
    api("/api/categories"),
    api("/api/stats/overview").catch(() => ({ byCategory: [] })),
  ]);
  state.categories = cats.categories || [];
  state.catCounts = {};
  (overview.byCategory || []).forEach((c) => { state.catCounts[c.categoryId] = c.count; });
  const wrap = $("categories");
  wrap.innerHTML = "";
  const total = Object.values(state.catCounts).reduce((a, b) => a + b, 0);
  const mkRow = (id, name, count) => {
    const label = document.createElement("label");
    label.className = "cat-row" + ((id === state.categoryId) || (id === null && !state.categoryId) ? " active" : "");
    label.innerHTML = '<input type="radio" name="cat" ' + (((id === state.categoryId) || (id === null && !state.categoryId)) ? "checked" : "") + ">" +
      "<span>" + esc(name) + "</span><span class=\"cat-count\">" + count + "</span>";
    label.querySelector("input").onchange = () => { state.categoryId = id; state.page = 1; loadCategories(); loadFeed(false); };
    wrap.appendChild(label);
  };
  mkRow(null, "전체", total);
  state.categories.forEach((c) => mkRow(c.id, c.name, state.catCounts[c.id] || 0));
}

// 왼쪽 언론사 접이식 필터 (사이트 단위)
async function loadSites() {
  const data = await api("/api/sites");
  state.sites = data.sites || [];
  if (state.checkedSites.size === 0) state.sites.forEach((s) => state.checkedSites.add(s.domain));
  const wrap = $("sources");
  wrap.innerHTML = "";
  state.sites.forEach((s) => {
    const label = document.createElement("label");
    const checked = state.checkedSites.has(s.domain) ? "checked" : "";
    label.innerHTML = '<input type="checkbox" ' + checked + ">" +
      '<span class="badge-src" style="background:' + srcColor(s.name) + '">' + esc(s.name.slice(0, 1)) + "</span>" +
      "<span>" + esc(s.name) + " (" + (s.postCount || 0) + ")</span>";
    label.querySelector("input").onchange = (e) => {
      if (e.target.checked) state.checkedSites.add(s.domain); else state.checkedSites.delete(s.domain);
      updateSiteHint();
      loadFeed(false);
    };
    wrap.appendChild(label);
  });
  updateSiteHint();
}

function updateSiteHint() {
  $("siteFilterHint").textContent = state.checkedSites.size + "/" + state.sites.length;
}

// ---- 1차: 사이트 관리 ----
async function openSites() {
  const wrap = $("siteList");
  wrap.innerHTML = "";
  let sites = state.sites;
  if (sites.length === 0) {
    try { sites = (await api("/api/sites")).sites || []; state.sites = sites; } catch (e) { console.error('[사이트] 조회 실패', e); }
  }
  if (sites.length === 0) { wrap.innerHTML = '<p class="msg">사이트 조회 실패</p>'; }
  let srcEnabled = {};
  try {
    const srcData = await api("/api/sources");
    const srcs = srcData.sources || [];
    srcs.forEach((s) => { srcEnabled[s.id] = !!s.enabled; });
  } catch (e) { console.error('[소스] 조회 실패', e); }
  sites.forEach((s) => {
    const div = document.createElement("div");
    div.className = "site-row";
    const on = (s.sourceIds || []).length > 0 && (s.sourceIds || []).every((id) => srcEnabled[id] !== false);
    div.innerHTML = '<span class="badge-src" style="background:' + srcColor(s.name) + '">' + esc(s.name.slice(0, 1)) + "</span>" +
      '<div class="board-main"><div class="board-name">' + esc(s.name) + "</div>" +
      '<div class="board-url">게시판 ' + (s.boardCount || 0) + " · 글 " + (s.postCount || 0) + "건</div>" +
      '<div class="site-actions"><button class="btn' + (on ? " on" : "") + '" data-act="toggle">' + (on ? "ON" : "OFF") +
      '</button><button class="btn" data-act="sync">수집</button><button class="btn" data-act="test">테스트</button>' +
      '<button class="btn" data-act="purge">비우기</button></div><div class="test-preview" hidden></div></div>' +
      "<span class=\"link\">열기 ›</span>";
    const openBoards = () => { $("siteModal").hidden = true; openBoardsSite(s.domain, s.name); };
    div.querySelector(".board-main").ondblclick = openBoards;
    div.querySelector(".link").onclick = openBoards;
    const prev = div.querySelector(".test-preview");
    const setToggles = (enabled) => {
      (s.sourceIds || []).forEach((id) => { srcEnabled[id] = enabled; });
      const b = div.querySelector('[data-act="toggle"]');
      b.textContent = enabled ? "ON" : "OFF";
      b.classList.toggle("on", enabled);
    };
    div.querySelector('[data-act="toggle"]').onclick = async (e) => {
      e.stopPropagation();
      const target = !((s.sourceIds || []).every((id) => srcEnabled[id] !== false));
      for (const id of (s.sourceIds || [])) {
        try {
          const r = await api("/api/sources/" + encodeURIComponent(id) + "/toggle", { method: "POST" });
          srcEnabled[id] = !!r.enabled;
        } catch (err) { toast("토글 실패"); return; }
      }
      setToggles(target);
      toast(target ? "수집 켜짐" : "수집 꺼짐");
    };
    div.querySelector('[data-act="sync"]').onclick = async (e) => {
      e.stopPropagation();
      for (const id of (s.sourceIds || [])) {
        await api("/api/sync", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ sourceId: id }) }).catch(() => {});
      }
      toast("수집 예약됨");
    };
    div.querySelector('[data-act="test"]').onclick = async (e) => {
      e.stopPropagation();
      const id = (s.sourceIds || [])[0];
      if (!id) { toast("소스 없음"); return; }
      prev.hidden = false;
      prev.textContent = "테스트 중…";
      try {
        const r = await api("/api/crawl/test", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ sourceId: id }) });
        if (r.ok) {
          prev.innerHTML = "수집 " + r.found + "건<br>" + (r.preview || []).map((p) => "· " + esc(p.title)).join("<br>");
        } else {
          prev.textContent = "실패: " + (r.error || "");
        }
      } catch (err) { prev.textContent = "테스트 실패"; }
    };
    div.querySelector('[data-act="purge"]').onclick = async (e) => {
      e.stopPropagation();
      if (!confirm(s.name + " 게시글을 비웁니까? (보드·설정 유지)")) return;
      let total = 0;
      for (const id of (s.sourceIds || [])) {
        try {
          const r = await api("/api/sources/" + encodeURIComponent(id) + "/purge", { method: "POST" });
          total += r.purged || 0;
        } catch (err) { toast("비우기 실패"); return; }
      }
      toast(total + "건 비움");
      loadSites().catch(() => {});
      loadFeed(false);
    };
    wrap.appendChild(div);
  });
  $("siteModal").hidden = false;
}

// ---- 2차: 사이트별 게시판 관리 ----
const boardsState = { domain: null, name: "", sourceIds: [], rows: [], categories: [], catalog: [] };
const INTERVALS = [15, 30, 60, 120];

async function openBoardsSite(domain, name) {
  boardsState.domain = domain;
  boardsState.name = name;
  boardsState.rows = [];
  const site = state.sites.find((s) => s.domain === domain);
  boardsState.sourceIds = site ? (site.sourceIds || []) : [];
  if (state.categories.length === 0) {
    try { state.categories = (await api("/api/categories")).categories || []; } catch (e) { console.error('[카테고리] 조회 실패', e); }
  }
  boardsState.categories = state.categories;
  try {
    const cat = await api("/api/board-catalog?domain=" + encodeURIComponent(domain));
    boardsState.catalog = cat.boards || [];
  } catch (e) { boardsState.catalog = []; }
  $("boardModalTitle").textContent = name + " 게시판 관리";
  $("boardMsg").textContent = "";
  $("newBoardCat").innerHTML = '<option value="">카테고리 선택</option>' + catOptions(0);
  $("newBoardInterval").innerHTML = INTERVALS.map((m) => '<option value="' + m + '"' + (m === 30 ? " selected" : "") + ">" + m + "분마다</option>").join("");
  $("catalogPick").innerHTML = '<option value="">추천 게시판에서 선택 (선택사항)</option>' +
    boardsState.catalog.map((c, i) => '<option value="' + i + '">' + esc(c.boardName) + " · " + esc(c.boardId) + "</option>").join("");
  $("boardModal").hidden = false;
  await reloadBoards();
}

async function reloadBoards() {
  try {
    const data = await api("/api/boards");
    const all = data.boards || [];
    const ids = boardsState.sourceIds;
    const rows = (ids.length > 0 ? all.filter((b) => ids.includes(b.sourceId)) : all)
      .map((b) => ({ ...b, _delete: false }));
    boardsState.rows = rows;
    renderBoards();
  } catch (e) { $("boardMsg").textContent = "게시판 조회 실패"; }
}

function catOptions(selected) {
  return boardsState.categories.map((c) =>
    '<option value="' + c.id + '"' + (c.id === selected ? " selected" : "") + ">" + esc(c.name) + "</option>"
  ).join("");
}

function intervalOptions(selected) {
  return INTERVALS.map((m) => '<option value="' + m + '"' + (m === selected ? " selected" : "") + ">" + m + "분마다</option>").join("");
}

function renderBoards() {
  const wrap = $("boardList");
  wrap.innerHTML = "";
  const multi = boardsState.sourceIds.length > 1;
  boardsState.rows.forEach((b, i) => {
    if (b._delete) return;
    const status = b.enabled ? "수집중" : "일시멈춤";
    const div = document.createElement("div");
    div.className = "board-row";
    div.innerHTML =
      '<label class="board-check" title="' + status + '"><input type="checkbox" data-i="' + i + '" data-f="enabled"' + (b.enabled ? " checked" : "") + "></label>" +
      '<div class="board-main"><div class="board-name">' + esc(b.boardName) + ' <span class="board-id">' + esc(b.boardId) + "</span>" +
      (multi ? ' <span class="board-id">' + esc(b.sourceId) + "</span>" : "") +
      ' <span class="board-status">' + status + "</span></div>" +
      '<div class="board-url">' + esc(b.boardUrl) + "</div></div>" +
      '<select data-i="' + i + '" data-f="categoryId" title="카테고리">' + catOptions(b.categoryId) + "</select>" +
      '<select data-i="' + i + '" data-f="intervalMinutes" title="수집 주기">' + intervalOptions(b.intervalMinutes || 30) + "</select>" +
      '<button class="btn icon board-del" data-i="' + i + '" title="삭제">✕</button>';
    div.querySelector('input[data-f="enabled"]').onchange = (e) => {
      b.enabled = e.target.checked;
      div.querySelector(".board-status").textContent = b.enabled ? "수집중" : "일시멈춤";
    };
    div.querySelector('select[data-f="categoryId"]').onchange = (e) => { b.categoryId = Number(e.target.value); };
    div.querySelector('select[data-f="intervalMinutes"]').onchange = (e) => { b.intervalMinutes = Number(e.target.value); };
    div.querySelector(".board-del").onclick = () => {
      if (b.id) { b._delete = true; } else { boardsState.rows.splice(i, 1); }
      renderBoards();
    };
    wrap.appendChild(div);
  });
  if (boardsState.rows.every((b) => b._delete)) {
    wrap.innerHTML = '<p class="msg">등록된 게시판이 없습니다. 아래에서 추가하세요.</p>';
  }
}

async function applyBoards(syncAfter) {
  const payload = {
    boards: boardsState.rows.map((b) => ({
      id: b.id || null,
      sourceId: b.sourceId || boardsState.sourceIds[0] || null,
      boardId: b.boardId,
      boardName: b.boardName,
      boardUrl: b.boardUrl,
      categoryId: b.categoryId,
      enabled: !!b.enabled,
      intervalMinutes: b.intervalMinutes || 30,
      _delete: !!b._delete,
    })),
  };
  try {
    const res = await api("/api/boards/batch", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (res.errors && res.errors.length > 0) {
      $("boardMsg").textContent = "일부 실패: " + res.errors.join(" / ");
    } else {
      $("boardMsg").textContent = "적용됨 (추가 " + res.created + " · 수정 " + res.updated + " · 삭제 " + res.deleted + ")";
      toast("게시판 적용됨");
    }
    await reloadBoards();
    loadSites().catch(() => {});
    if (syncAfter) {
      for (const sid of boardsState.sourceIds) {
        await api("/api/sync", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ sourceId: sid }),
        }).catch(() => {});
      }
      toast("수집 예약됨");
    }
    loadFeed(false);
  } catch (e) { $("boardMsg").textContent = "적용 실패"; }
}

async function loadRanking() {
  try {
    const data = await api("/api/ranking");
    const ol = $("ranking");
    ol.innerHTML = "";
    (data.ranking || []).forEach((p) => {
      const li = document.createElement("li");
      li.textContent = p.title;
      li.title = (p.sourceName || "") + " ♥" + (p.likeCount || 0);
      li.onclick = () => openDetail(p.id);
      ol.appendChild(li);
    });
    if (!data.ranking || data.ranking.length === 0) ol.innerHTML = "<li>아직 집계 없음</li>";
  } catch (e) { /* 랭킹 실패는 조용히 */ }
}

async function loadStats() {
  try {
    const data = await api("/api/stats/overview");
    const byCat = (data.byCategory || []).map((c) => esc(c.categoryName) + " " + c.count).join(" · ");
    const total = (data.byCategory || []).reduce((a, c) => a + c.count, 0);
    $("stats").innerHTML = "전체 게시글 " + total + "건<br>" + esc(byCat);
  } catch (e) { /* 통계 실패는 조용히 */ }
}

async function openDetail(id) {
  try {
    const p = await api("/api/posts/" + id);
    const when = p.publishedAt ? new Date(p.publishedAt).toLocaleString("ko-KR") : "";
    const images = (p.images && p.images.length > 0 ? p.images : (p.thumbnailUrl ? [p.thumbnailUrl] : [])).slice(0, 5);
    const gallery = images.map((u) => '<img class="detail-img" src="' + esc(thumbSrc(u)) + '" alt="" loading="lazy" referrerpolicy="no-referrer">').join("");
    // 요약 내 🔗 줄은 클릭 링크로
    const renderBody = (text) => text.split("\n").map((line) => {
      const m = line.match(/^🔗\s*(.*?):\s*(https?:\/\/\S+)\s*$/);
      if (m) return '🔗 <a href="' + esc(m[2]) + '" target="_blank" rel="noopener">' + esc(m[1]) + "</a>";
      return esc(line);
    }).join("<br>");
    const bodyText = p.summary ? renderBody(p.summary)
      : (images.length > 0 ? "이미지 " + images.length + "장 게시글입니다. 원문에서 확인하세요." : "요약 없음 — 원문에서 확인하세요.");
    $("detailBody").innerHTML = "<h1>" + esc(p.title) + "</h1>" +
      '<div class="kicker">' + esc(p.sourceName || p.sourceId || "") + (p.boardName ? " · " + esc(p.boardName) : "") +
      " · " + esc(p.categoryName || "") + (when ? " · " + esc(when) : "") + "</div>" +
      gallery +
      '<div class="body">' + bodyText + "</div>" +
      '<div class="actions"><a class="btn primary" href="' + esc(p.originalUrl) + '" target="_blank" rel="noopener">원문 보기 ↗</a>' +
      '<button class="btn" id="btnCopyLink">링크 복사</button>' +
      '<button class="btn" id="btnRefreshPost">새로고침</button></div>';
    $("btnCopyLink").onclick = async () => {
      try { await navigator.clipboard.writeText(p.originalUrl); toast("링크 복사됨"); }
      catch (e) { toast("복사 실패"); }
    };
    $("btnRefreshPost").onclick = async () => {
      try {
        const r = await api("/api/posts/" + id + "/refresh", { method: "POST" });
        toast(r.ok ? "보충됨" : "보충 실패");
        if (r.ok) openDetail(id);
      } catch (e) { toast("새로고침 실패"); }
    };
    $("detailModal").hidden = false;
  } catch (e) { toast("상세 조회 실패"); }
}

async function loadNotifs() {
  const wrap = $("notifList");
  wrap.innerHTML = "";
  $("notifMsg").textContent = "";
  try {
    const d = await api("/api/notifications?page=1&pageSize=20");
    const items = d.notifications || [];
    if (items.length === 0) { wrap.innerHTML = '<p class="msg">알림이 없습니다</p>'; return; }
    items.forEach((n) => {
      const div = document.createElement("div");
      div.className = "notif-row";
      div.innerHTML = "<div><b>" + esc(n.type) + "</b><br>" + esc(n.summary) + '</div>' +
        '<div class="nmeta">' + fmtTime(n.createdAt) + (n.isRead ? "" : " · 안읽음") + "</div>" +
        '<div class="nbtns">' +
        (n.isRead ? "" : '<button class="btn" data-nread="' + n.id + '">읽음</button>') +
        '<button class="btn" data-ndel="' + n.id + '">삭제</button></div>';
      const rd = div.querySelector("[data-nread]");
      if (rd) rd.onclick = async () => {
        try { await api("/api/notifications/" + n.id + "/read", { method: "POST" }); loadNotifs(); }
        catch (e) { toast("실패"); }
      };
      div.querySelector("[data-ndel]").onclick = async () => {
        try { await api("/api/notifications/" + n.id, { method: "DELETE" }); loadNotifs(); }
        catch (e) { toast("실패"); }
      };
      wrap.appendChild(div);
    });
  } catch (e) { $("notifMsg").textContent = "알림 조회 실패"; }
}

async function loadSettingsForm() {
  try {
    const s = await api("/api/settings");
    $("setPort").value = s.port;
    $("setRetention").value = s.retentionDays;
    $("setAutoStart").checked = !!s.autoStart;
    $("setWatchdog").value = s.watchdogIntervalSec;
    $("setNotifCrawl").checked = !!s.notifCrawlComplete;
    $("setNotifNew").checked = !!s.notifNewPost;
    $("setNotifFail").checked = !!s.notifFailure;
  } catch (e) { $("settingsMsg").textContent = "설정 조회 실패"; }
}

function init() {
  $("btnNotif").onclick = () => { $("notifDrawer").hidden = false; loadNotifs(); };
  $("btnCloseNotif").onclick = () => { $("notifDrawer").hidden = true; };
  $("btnNotifReadAll").onclick = async () => {
    try { await api("/api/notifications/read-all", { method: "POST" }); toast("모두 읽음"); loadNotifs(); }
    catch (e) { toast("실패"); }
  };
  $("btnNotifCleanup").onclick = async () => {
    try {
      const r = await api("/api/notifications/cleanup", { method: "POST" });
      toast((r.deleted || 0) + "건 정리됨");
      loadNotifs();
    } catch (e) { toast("실패"); }
  };
  $("logo").onclick = () => { state.categoryId = null; state.q = ""; $("q").value = ""; loadCategories(); loadFeed(false); };
  let qTimer = null;
  $("q").oninput = (e) => {
    clearTimeout(qTimer);
    qTimer = setTimeout(() => { state.q = e.target.value.trim(); loadFeed(false); }, 400);
  };
  $("btnMore").onclick = () => { state.page += 1; loadFeed(true); };
  $("btnAllSources").onclick = () => { state.sites.forEach((s) => state.checkedSites.add(s.domain)); loadSites(); loadFeed(false); };
  $("btnSiteManage").onclick = () => openSites();
  $("btnCloseSites").onclick = () => { $("siteModal").hidden = true; };
  $("siteModal").addEventListener("click", (e) => { if (e.target.id === "siteModal") $("siteModal").hidden = true; });
  $("btnSync").onclick = async () => {
    try {
      await api("/api/sync", { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" });
      toast("수집 예약됨");
    } catch (e) { toast("수집 요청 실패"); }
  };
  $("btnCloseDetail").onclick = () => { $("detailModal").hidden = true; };
  $("detailModal").addEventListener("click", (e) => { if (e.target.id === "detailModal") $("detailModal").hidden = true; });
  $("btnSettings").onclick = () => { $("settingsDrawer").hidden = false; loadSettingsForm(); };
  $("btnCloseSettings").onclick = () => { $("settingsDrawer").hidden = true; };
  $("settingsForm").onsubmit = async (e) => {
    e.preventDefault();
    const body = {
      port: Number($("setPort").value),
      retentionDays: Number($("setRetention").value),
      autoStart: $("setAutoStart").checked,
      watchdogIntervalSec: Number($("setWatchdog").value),
      notifCrawlComplete: $("setNotifCrawl").checked,
      notifNewPost: $("setNotifNew").checked,
      notifFailure: $("setNotifFail").checked,
    };
    try {
      await api("/api/settings", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
      $("settingsMsg").textContent = "저장됨";
      toast("설정 저장됨");
    } catch (err) { $("settingsMsg").textContent = "저장 실패 (값 범위 확인)"; }
  };
  $("btnCloseBoards").onclick = () => { $("boardModal").hidden = true; };
  $("boardModal").addEventListener("click", (e) => { if (e.target.id === "boardModal") $("boardModal").hidden = true; });
  $("btnAddBoard").onclick = () => {
    const boardId = $("newBoardId").value.trim();
    const boardName = $("newBoardName").value.trim();
    const boardUrl = $("newBoardUrl").value.trim();
    const categoryId = Number($("newBoardCat").value);
    const intervalMinutes = Number($("newBoardInterval").value) || 30;
    if (!boardId || !boardName) { $("boardMsg").textContent = "ID·이름은 필수입니다"; return; }
    if (!/^https?:\/\//.test(boardUrl)) { $("boardMsg").textContent = "URL은 http(s)로 입력하세요"; return; }
    if (!boardsState.categories.some((c) => c.id === categoryId)) { $("boardMsg").textContent = "카테고리를 선택하세요"; return; }
    if (!INTERVALS.includes(intervalMinutes)) { $("boardMsg").textContent = "주기는 15/30/60/120분 중 선택"; return; }
    boardsState.rows.push({ id: null, sourceId: boardsState.sourceIds[0] || null, boardId, boardName, boardUrl, categoryId, enabled: true, intervalMinutes });
    $("newBoardId").value = ""; $("newBoardName").value = ""; $("newBoardUrl").value = "";
    $("catalogPick").value = "";
    $("boardMsg").textContent = "";
    renderBoards();
  };
  $("catalogPick").onchange = (e) => {
    const c = boardsState.catalog[Number(e.target.value)];
    if (!c) return;
    $("newBoardId").value = c.boardId || "";
    $("newBoardName").value = c.boardName || "";
    $("newBoardUrl").value = c.boardUrl || "";
    if (boardsState.categories.some((x) => x.id === c.categoryId)) $("newBoardCat").value = String(c.categoryId);
  };
  $("btnApplyBoards").onclick = () => applyBoards(false);
  $("btnApplySync").onclick = () => applyBoards(true);
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") { $("detailModal").hidden = true; $("settingsDrawer").hidden = true; $("boardModal").hidden = true; $("siteModal").hidden = true; $("notifDrawer").hidden = true; }
  });
  loadCategories().then(() => loadFeed(false)).catch(() => { $("feed").innerHTML = "<p>서버 연결 실패</p>"; });
  loadSites().catch(() => {});
  loadRanking();
  loadStats();
}
document.addEventListener("DOMContentLoaded", init);
