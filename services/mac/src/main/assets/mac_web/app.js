/* 맥줍줍 포털 — 바닐라 JS. 타임라인·Watchlist·통계·상세 7섹션 */
(function () {
    'use strict';

    var CATEGORIES = ['생산성', '유틸리티', '보안·프라이버시', '미디어·엔터', '개발',
        '디자인·크리에이티브', '금융', '글쓰기·노트', '시스템최적화', '커뮤니케이션'];
    var LICENSE_LABEL = { OSS: '오픈소스', FREE: '프리', PAID: '유료' };
    var PAGE_SIZE = 20;

    var state = { view: 'timeline', license: '', tag: '', category: '', q: '', sort: 'newest', page: 1, lang: 'ko', collectDays: 14, collectSource: '', watchMode: 'updated' };

    /* 현재 뷰 다시 로드 (공유 메뉴가 모든 뷰에서 동작하도록) */
    function reloadCurrentView() {
        if (state.view === 'watchlist') loadWatchlist();
        else if (state.view === 'stats') loadStats();
        else loadTimeline();
    }

    /* 한/영 선택: ko 기본, 번역 없으면 원문 폴백 */
    function pick(ko, en) {
        if (state.lang === 'ko' && ko) return { text: ko, isKo: true };
        return { text: en || ko || '', isKo: false };
    }

    function $(id) { return document.getElementById(id); }
    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    /* ---------- 마크다운 (T-142: 보수적 렌더러) ----------
     * XSS: escape 선행 + href https? 화이트리스트 + 수집 단계 raw HTML 제거.
     * 평문 오렌더 방지: 단일 *·_ 강조 미지원, 이미지는 링크로 (임의 로드 차단). */
    function mdInline(s) {
        s = s.replace(/`([^`]+?)`/g, '<code>$1</code>');
        s = s.replace(/\*\*([^*]+?)\*\*/g, '<strong>$1</strong>');
        s = s.replace(/!\[([^\]]*?)\]\((https?:\/\/[^)\s]+?)\)/g, '<a target="_blank" rel="noopener" href="$2">🖼 $1</a>');
        s = s.replace(/\[([^\]]+?)\]\((https?:\/\/[^)\s]+?)\)/g, '<a target="_blank" rel="noopener" href="$2">$1</a>');
        return s;
    }
    /* HTML 잔재 제거 (T-143: 구 수집분·릴리즈노트의 날 태그가 소스로 보이는 문제).
     * 진짜 태그만 제거: '<' 바로 뒤 영문(또는 '/'+영문). 'a < b' 같은 평문은 보존. */
    function stripHtml(s) {
        return String(s == null ? '' : s).replace(/<\/?[a-zA-Z][^>\n]*>/g, '');
    }
    function md(src) {
        var lines = esc(stripHtml(src)).split('\n');
        var html = '';
        var inCode = false;
        var codeBuf = [];
        var listBuf = [];
        var listTag = '';
        function flushList() {
            if (listBuf.length) {
                html += '<' + listTag + '>' + listBuf.join('') + '</' + listTag + '>';
                listBuf = [];
                listTag = '';
            }
        }
        lines.forEach(function (raw) {
            var line = raw.trim();
            if (/^```/.test(line)) {
                flushList();
                if (inCode) {
                    html += '<pre><code>' + codeBuf.join('\n') + '</code></pre>';
                    codeBuf = [];
                }
                inCode = !inCode;
                return;
            }
            if (inCode) {
                codeBuf.push(raw.replace(/^\s+|\s+$/g, ''));
                return;
            }
            if (!line) {
                flushList();
                return;
            }
            var h = line.match(/^(#{1,6})\s+(.*)$/);
            if (h) {
                flushList();
                html += h[1].length <= 2 ? '<h4>' + mdInline(h[2]) + '</h4>' : '<h5>' + mdInline(h[2]) + '</h5>';
                return;
            }
            if (/^(---|\*\*\*|___)\s*$/.test(line)) {
                flushList();
                html += '<hr>';
                return;
            }
            var q = line.match(/^&gt;\s?(.*)$/);
            if (q) {
                flushList();
                html += '<blockquote>' + mdInline(q[1]) + '</blockquote>';
                return;
            }
            var ul = line.match(/^[-*+]\s+(.*)$/);
            if (ul) {
                if (listTag !== 'ul') flushList();
                listTag = 'ul';
                var item = ul[1].replace(/^\[([ xX])\]\s+/, function (m, c) {
                    return c.toLowerCase() === 'x' ? '☑ ' : '☐ ';
                });
                listBuf.push('<li>' + mdInline(item) + '</li>');
                return;
            }
            var ol = line.match(/^\d+[.)]\s+(.*)$/);
            if (ol) {
                if (listTag !== 'ol') flushList();
                listTag = 'ol';
                listBuf.push('<li>' + mdInline(ol[1]) + '</li>');
                return;
            }
            flushList();
            html += '<p>' + mdInline(line) + '</p>';
        });
        flushList();
        if (inCode && codeBuf.length) html += '<pre><code>' + codeBuf.join('\n') + '</code></pre>';
        return html;
    }
    /* 카드·히스토리 발췌용: HTML+마크다운 기호 제거 → 평문 */
    function stripMd(src) {
        return stripHtml(src)
            .replace(/```[\s\S]*?```/g, ' ')
            .replace(/`([^`]*?)`/g, '$1')
            .replace(/^#{1,6}\s+/gm, '')
            .replace(/\*\*([^*]+?)\*\*/g, '$1')
            .replace(/!\[([^\]]*?)\]\([^)]*?\)/g, '$1')
            .replace(/\[([^\]]+?)\]\([^)]*?\)/g, '$1')
            .replace(/^\s*&gt;\s?/gm, '')
            .replace(/^\s*[-*+]\s+/gm, '')
            .replace(/^\s*\d+[.)]\s+/gm, '')
            .replace(/\s+/g, ' ').trim();
    }
    /* 한/원문 토글 블록 (메모리 보관 + 재렌더 — data 속성 전문 저장 폐지) */
    var mdStore = {};
    var mdSeq = 0;
    function mdBlock(koText, enText) {
        var show = koText || enText || '';
        if (!show) return '';
        if (koText && enText && koText !== enText) {
            var key = 'm' + (++mdSeq);
            mdStore[key] = { ko: koText, en: enText, showing: 'ko' };
            return '<div class="md-body" data-mdtext data-key="' + key + '">' + md(koText) + '</div>' +
                '<button class="orig-toggle" type="button" data-mdkey="' + key + '">원문보기</button>';
        }
        return '<div class="md-body">' + md(show) + '</div>';
    }
    function toast(msg) {
        var t = $('toast');
        t.textContent = msg;
        t.hidden = false;
        setTimeout(function () { t.hidden = true; }, 2200);
    }
    function api(path) {
        return fetch(path).then(function (r) {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
        });
    }

    /* ---------- 칩 ---------- */
    function buildCategoryChips() {
        var box = $('categoryChips');
        box.innerHTML = '';
        var all = document.createElement('button');
        all.className = 'chip active';
        all.type = 'button';
        all.textContent = '전체 카테고리';
        all.onclick = function () { state.category = ''; state.page = 1; syncChips(); reloadCurrentView(); };
        box.appendChild(all);
        CATEGORIES.forEach(function (c) {
            var b = document.createElement('button');
            b.className = 'chip';
            b.type = 'button';
            b.textContent = c;
            b.dataset.category = c;
            b.onclick = function () {
                state.category = state.category === c ? '' : c;
                state.page = 1;
                syncChips();
                reloadCurrentView();
            };
            box.appendChild(b);
        });
    }
    function syncChips() {
        document.querySelectorAll('#licenseChips .chip').forEach(function (b) {
            var on = (b.dataset.license !== undefined && b.dataset.license === state.license) ||
                (b.dataset.tag !== undefined && b.dataset.tag === state.tag);
            b.classList.toggle('active', !!on);
        });
        document.querySelectorAll('#categoryChips .chip').forEach(function (b) {
            b.classList.toggle('active', (b.dataset.category || '') === state.category);
        });
    }
    function bindLicenseTags() {
        document.querySelectorAll('#licenseChips .chip').forEach(function (b) {
            b.onclick = function () {
                if (b.dataset.license !== undefined) {
                    var v = b.dataset.license;
                    state.license = state.license === v ? '' : v;
                } else {
                    var t = b.dataset.tag;
                    state.tag = state.tag === t ? '' : t;
                }
                state.page = 1;
                syncChips();
                reloadCurrentView();
            };
        });
    }

    /* ---------- 모바일 필터 패널 (v1.7: 상단바만 sticky, 필터 접기식) ---------- */
    var filterCollapsed = true;
    function isMobileWidth() {
        return window.matchMedia && window.matchMedia('(max-width: 640px)').matches;
    }
    function applyFilterPanel(showFilters) {
        var panel = $('filterPanel');
        var toggle = $('filterToggle');
        if (!panel) return;
        if (!showFilters) {
            panel.hidden = true;
        } else {
            panel.hidden = isMobileWidth() ? filterCollapsed : false;
        }
        if (toggle) {
            toggle.setAttribute('aria-expanded', panel.hidden ? 'false' : 'true');
            toggle.textContent = panel.hidden ? '☰ 필터' : '✕ 닫기';
        }
    }
    function bindFilterToggle() {
        var toggle = $('filterToggle');
        var panel = $('filterPanel');
        if (!toggle || !panel) return;
        filterCollapsed = isMobileWidth();
        panel.hidden = filterCollapsed && isMobileWidth();
        toggle.setAttribute('aria-expanded', panel.hidden ? 'false' : 'true');
        toggle.textContent = panel.hidden ? '☰ 필터' : '✕ 닫기';
        toggle.onclick = function () {
            filterCollapsed = !panel.hidden;
            panel.hidden = !panel.hidden;
            toggle.setAttribute('aria-expanded', panel.hidden ? 'false' : 'true');
            toggle.textContent = panel.hidden ? '☰ 필터' : '✕ 닫기';
        };
        if (window.matchMedia) {
            window.matchMedia('(max-width: 640px)').addEventListener('change', function (e) {
                // 데스크탑 전환 시 항상 펼침, 모바일 복귀 시 접힘 상태 복원
                if (!e.matches) {
                    if (state.view !== 'stats') panel.hidden = false;
                } else {
                    if (state.view !== 'stats') panel.hidden = filterCollapsed;
                }
                toggle.setAttribute('aria-expanded', panel.hidden ? 'false' : 'true');
                toggle.textContent = panel.hidden ? '☰ 필터' : '✕ 닫기';
            });
        }
    }

    /* ---------- 카드 ---------- */
    function licensePill(a) {
        var cls = a.license === 'OSS' ? 'oss' : a.license === 'PAID' ? 'paid' : 'free';
        return '<span class="pill ' + cls + '">' + esc(LICENSE_LABEL[a.license] || a.license) + '</span>';
    }
    function iconHtml(a) {
        if (a.iconUrl) {
            return '<img class="app-icon" src="' + esc(a.iconUrl) + '" alt="" loading="lazy">';
        }
        var ch = (a.name || '?').trim().charAt(0).toUpperCase();
        return '<span class="app-icon app-icon-ph" aria-hidden="true">' + esc(ch) + '</span>';
    }
    function cardHtml(a) {
        var badges = licensePill(a) +
            '<span class="pill">' + esc(a.category || '') + '</span>' +
            (a.isNew ? '<span class="pill new">NEW</span>' : '') +
            (a.sourceUrl ? '<a class="pill pill-src" target="_blank" rel="noopener" href="' + esc(a.sourceUrl) + '" title="수집 출처에서 보기">출처: ' + esc(a.sourceName || '원문') + '</a>' : '');
        var ver = a.version
            ? '<div class="ver">' + (a.prevVersion ? '<s>' + esc(a.prevVersion) + '</s>' : '') + esc(a.version) + '</div>'
            : '<div class="ver">포착 ' + fmtDate(a.releaseDate || a.firstSeenAt) + '</div>';
        var notes = pick(a.releaseNotesKo, a.releaseNotesSummary);
        if (!notes.text) notes = pick(a.descriptionKo, a.descriptionSnippet);
        var notesHtml = notes.text ? '<div class="notes">' + esc(stripMd(notes.text)) + '</div>' : '';
        var meta = [];
        if (a.stars != null) meta.push('★ ' + a.stars);
        if (a.averageRating != null) meta.push('평점 ' + a.averageRating);
        if (a.primaryLanguage) meta.push(esc(a.primaryLanguage));
        if (a.forks != null) meta.push('⑂ ' + a.forks);
        if (a.fileSize) meta.push(fmtSize(a.fileSize));
        return '<article class="app-card" role="listitem" data-id="' + esc(a.id) + '">' +
            '<div class="app-head">' + iconHtml(a) +
            '<div class="app-head-text"><h3>' + esc(a.name) + '</h3>' +
            '<div class="dev">' + esc(a.developer || '') + '</div></div></div>' +
            '<div class="badges">' + badges + '</div>' + ver + notesHtml +
            '<div class="meta">' + meta.join(' · ') + '</div>' +
            '</article>';
    }
    function fmtDate(ts) {
        if (!ts) return '-';
        var d = new Date(ts);
        return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    }
    function fmtSize(bytes) {
        if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(1) + 'GB';
        if (bytes >= 1048576) return Math.round(bytes / 1048576) + 'MB';
        return Math.round(bytes / 1024) + 'KB';
    }
    function bindCards(container) {
        container.querySelectorAll('.app-card').forEach(function (el) {
            el.setAttribute('tabindex', '0');
            el.setAttribute('role', 'button');
            el.onclick = function () { openDetail(el.dataset.id); };
            el.onkeydown = function (e) {
                if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openDetail(el.dataset.id); }
            };
            // 출처 뱃지 클릭은 새탭만 (모달 방지)
            el.querySelectorAll('a[target="_blank"]').forEach(function (a) {
                a.onclick = function (e) { e.stopPropagation(); };
                a.onkeydown = function (e) { e.stopPropagation(); };
            });
        });
    }

    /* ---------- 타임라인 ---------- */
    var searchTimer = null;
    var timelineSeq = 0;
    function query() {
        var p = new URLSearchParams({ sort: state.sort, page: state.page, pageSize: PAGE_SIZE });
        if (state.license) p.set('license', state.license);
        if (state.tag) p.set('tag', state.tag);
        if (state.category) p.set('category', state.category);
        if (state.q) p.set('q', state.q);
        return '/api/apps?' + p.toString();
    }
    function loadTimeline() {
        var grid = $('appGrid');
        var seq = ++timelineSeq;
        grid.innerHTML = '<div class="empty-state">불러오는 중…</div>';
        api(query()).then(function (d) {
            if (seq !== timelineSeq) return;
            $('visibleCount').textContent = '검색 결과 ' + d.total + '개';
            if (!d.apps.length) {
                grid.innerHTML = '<div class="empty-state">조건에 맞는 앱이 없습니다</div>';
                $('pagination').innerHTML = '';
                return;
            }
            grid.innerHTML = d.apps.map(cardHtml).join('');
            bindCards(grid);
            renderPagination($('pagination'), d.page, d.pageSize, d.total, function (p) {
                state.page = p;
                loadTimeline();
                window.scrollTo(0, 0);
            });
        }).catch(function () {
            if (seq !== timelineSeq) return;
            grid.innerHTML = '<div class="empty-state">데이터를 불러오는데 실패했습니다 (서버 미기동 시)</div>';
        });
    }
    function renderPagination(box, page, pageSize, total, go) {
        var pages = Math.max(1, Math.ceil(total / pageSize));
        if (pages <= 1) { box.innerHTML = ''; return; }
        var html = '<button type="button" data-p="' + (page - 1) + '"' + (page <= 1 ? ' disabled' : '') + '>‹</button>';
        var nums = pageNums(page, pages);
        nums.forEach(function (n) {
            if (n === '…') { html += '<button type="button" disabled>…</button>'; return; }
            html += '<button type="button" data-p="' + n + '"' + (n === page ? ' class="active"' : '') + '>' + n + '</button>';
        });
        html += '<button type="button" data-p="' + (page + 1) + '"' + (page >= pages ? ' disabled' : '') + '>›</button>';
        box.innerHTML = html;
        box.querySelectorAll('button[data-p]').forEach(function (b) {
            if (b.disabled) return;
            b.onclick = function () { go(parseInt(b.dataset.p, 10)); };
        });
    }
    function pageNums(page, pages) {
        var set = {};
        [1, 2, page - 1, page, page + 1, pages - 1, pages].forEach(function (n) {
            if (n >= 1 && n <= pages) set[n] = true;
        });
        var arr = Object.keys(set).map(Number).sort(function (a, b) { return a - b; });
        var out = [];
        var prev = 0;
        arr.forEach(function (n) {
            if (n - prev > 1) out.push('…');
            out.push(n);
            prev = n;
        });
        return out;
    }

    /* ---------- Watchlist (버전 업데이트, 서버 필터+페이징) ---------- */
    function watchQuery() {
        // T-132: updated=실제 버전업(prevVersion 있음)만, settled=버전 있는 정착앱 전체
        var p = new URLSearchParams({ sort: state.sort, page: state.page, pageSize: PAGE_SIZE, bumped: 'true' });
        if (state.watchMode === 'updated') p.set('updatedOnly', 'true');
        if (state.license) p.set('license', state.license);
        if (state.tag) p.set('tag', state.tag);
        if (state.category) p.set('category', state.category);
        if (state.q) p.set('q', state.q);
        return '/api/apps?' + p.toString();
    }
    function loadWatchlist() {
        var grid = $('watchGrid');
        grid.innerHTML = '<div class="empty-state">불러오는 중…</div>';
        api(watchQuery()).then(function (d) {
            if (!d.apps.length) {
                grid.innerHTML = '<div class="empty-state">조건에 맞는 버전 업데이트가 없습니다</div>';
                $('watchPagination').innerHTML = '';
                return;
            }
            grid.innerHTML = d.apps.map(cardHtml).join('');
            bindCards(grid);
            renderPagination($('watchPagination'), d.page, d.pageSize, d.total, function (p) {
                state.page = p;
                loadWatchlist();
                window.scrollTo(0, 0);
            });
        }).catch(function () {
            grid.innerHTML = '<div class="empty-state">데이터를 불러오는데 실패했습니다</div>';
        });
    }

    /* ---------- 통계 (기존 섹션 우선 렌더 + 추가 섹션 독립 로드) ---------- */
    function loadStats() {
        var box = $('statsContent');
        box.innerHTML = '<div class="empty-state">통계 불러오는 중…</div>';
        // ① 기존 섹션: 하나라도 실패하면 전체 실패 표시 (예전 동작 유지)
        api('/api/stats').then(function (s) {
            return api('/api/stats/trends').then(function (t) { return { s: s, t: t }; });
        }).then(function (r) {
            box.innerHTML = baseStatsHtml(r.s, r.t) +
                '<div id="collectBox"></div>';
            // ② 추가 섹션: 각각 독립 — 실패해도 기존 섹션은 유지
            loadInsightsBox();
            loadCollectBox();
        }).catch(function () {
            box.innerHTML = '<div class="empty-state">통계를 불러오는데 실패했습니다</div>';
        });
    }
    function baseStatsHtml(s, t) {
        var cats = Object.keys(t.byCategory || {}).sort(function (a, b) { return t.byCategory[b] - t.byCategory[a]; });
        var maxCat = cats.length ? t.byCategory[cats[0]] : 1;
        var lic = t.byLicense || {};
        var html = '<div class="kpi-grid">' +
            kpi(s.totalApps, '수집 앱') + kpi(s.activeSources, '활성 소스') +
            kpi(t.newLast7d, '최근 7일 신규') + kpi(t.versionBumpsLast7d, '최근 7일 버전업') +
            kpi(t.aiTagCount, 'AI-Agent') + kpi(t.menuBarTagCount, 'MenuBar') +
            '</div><div id="insightBox"></div><h3>카테고리 분포</h3>';
        cats.forEach(function (c) {
            var v = t.byCategory[c];
            html += '<div class="bar-row"><span class="name">' + esc(c) + '</span>' +
                '<span class="bar-track"><span class="bar-fill" style="display:block;width:' +
                Math.round(v / maxCat * 100) + '%"></span></span>' +
                '<span class="cnt">' + v + '</span></div>';
        });
        html += '<h3>라이선스 분포</h3><div class="bar-row"><span class="name">오픈소스</span>' +
            bar(lic.OSS || 0, s.totalApps) + '</div>' +
            '<div class="bar-row"><span class="name">프리</span>' + bar(lic.FREE || 0, s.totalApps) + '</div>' +
            '<div class="bar-row"><span class="name">유료</span>' + bar(lic.PAID || 0, s.totalApps) + '</div>';
        return html;
    }
    function loadInsightsBox() {
        var box = $('insightBox');
        if (!box) return;
        api('/api/stats/insights').then(function (ins) {
            if (!ins.insights || !ins.insights.length) return;
            box.innerHTML = '<h3>💡 인사이트</h3><div class="insight-grid">' + ins.insights.map(function (n) {
                return '<div class="insight-card"><div class="insight-icon">' + esc(n.icon) + '</div>' +
                    '<div><b>' + esc(n.title) + '</b><p>' + esc(n.body) + '</p></div></div>';
            }).join('') + '</div>';
        }).catch(function () { /* 기존 섹션 유지, 조용히 생략 */ });
    }
    function loadCollectBox() {
        var box = $('collectBox');
        if (!box) return;
        var days = state.collectDays;
        api('/api/stats/collect?days=' + days).then(function (c) {
            collectCache = c.days || [];
            box.innerHTML = '<h3>📥 수집처별 일별 수집량 (' + days + '일)</h3>' + collectSectionHtml();
            bindCollectSection(box);
        }).catch(function () { /* 기존 섹션 유지, 조용히 생략 */ });
    }
    var collectCache = [];
    function collectSources() {
        var map = {};
        collectCache.forEach(function (d) {
            (d.bySource || []).forEach(function (s) {
                if (!map[s.sourceId]) map[s.sourceId] = { sourceId: s.sourceId, sourceName: s.sourceName, found: 0, newCount: 0, updated: 0 };
                map[s.sourceId].found += s.found;
                map[s.sourceId].newCount += s['new'] || 0;
                map[s.sourceId].updated += s.updated;
            });
        });
        return Object.keys(map).map(function (k) { return map[k]; }).sort(function (a, b) { return b.found - a.found; });
    }
    function collectScope(day) {
        if (!state.collectSource) {
            return { found: day.found, newCount: day['new'] || 0, updated: day.updated, runs: day.runs, name: '전체' };
        }
        var hit = null;
        (day.bySource || []).forEach(function (s) { if (s.sourceId === state.collectSource) hit = s; });
        if (!hit) return { found: 0, newCount: 0, updated: 0, runs: 0, name: '' };
        return { found: hit.found, newCount: hit['new'] || 0, updated: hit.updated, runs: 0, name: hit.sourceName };
    }
    function collectSectionHtml() {
        var srcs = collectSources();
        var chips = '<button type="button" data-src="" class="' + (!state.collectSource ? 'active' : '') + '">전체</button>' +
            srcs.map(function (s) {
                return '<button type="button" data-src="' + esc(s.sourceId) + '" class="' + (state.collectSource === s.sourceId ? 'active' : '') + '">' + esc(s.sourceName) + '</button>';
            }).join('');
        var table = '<table class="ver-table collect-table"><tr><th>수집처</th><th>발견</th><th>신규</th><th>갱신</th></tr>' +
            srcs.map(function (s) {
                return '<tr data-src="' + esc(s.sourceId) + '"><td>' + esc(s.sourceName) + '</td><td>' +
                    s.found + '</td><td>' + s.newCount + '</td><td>' + s.updated + '</td></tr>';
            }).join('') + '</table>';
        return '<div class="period-row collect-srcs" id="collectSources">' + chips + '</div>' +
            collectChart(collectCache) + table;
    }
    function bindCollectSection(box) {
        function select(src) {
            state.collectSource = src;
            box.querySelectorAll('#collectSources button').forEach(function (b) {
                b.classList.toggle('active', (b.dataset.src || '') === src);
            });
            var chart = box.querySelector('.collect-wrap');
            if (chart) chart.innerHTML = collectChartInner(collectCache);
        }
        box.querySelectorAll('#collectSources button').forEach(function (b) {
            b.onclick = function () { select(b.dataset.src || ''); };
        });
        box.querySelectorAll('.collect-table tr[data-src]').forEach(function (tr) {
            tr.onclick = function () { select(tr.dataset.src); };
        });
    }
    function collectChart(days) {
        if (!days.length) return '<div class="empty-state">수집 기록이 없습니다</div>';
        return '<div class="collect-wrap">' + collectChartInner(days) + '</div>';
    }
    function collectChartInner(days) {
        var scoped = days.map(function (d) {
            var sc = collectScope(d);
            return { day: d.day, found: sc.found, newCount: sc.newCount, updated: sc.updated, runs: sc.runs, name: sc.name };
        });
        var max = 1;
        scoped.forEach(function (d) { if (d.found > max) max = d.found; });
        var cols = scoped.map(function (d) {
            var hf = Math.max(2, Math.round(d.found / max * 100));
            var hn = Math.max(d.newCount ? 2 : 0, Math.round(d.newCount / max * 100));
            var label = String(d.day).slice(5);
            var title = d.day + ' ' + d.name + ' — 발견 ' + d.found + ' · 신규 ' + d.newCount + ' · 갱신 ' + d.updated + (d.runs ? ' · ' + d.runs + '회' : '');
            return '<div class="collect-day" title="' + esc(title) + '">' +
                '<div class="collect-bars"><span class="collect-bar-new" style="height:' + hn + '%"></span>' +
                '<span class="collect-bar-found" style="height:' + hf + '%"></span></div>' +
                '<span class="collect-label">' + esc(label) + '</span></div>';
        }).join('');
        return '<div class="collect-legend"><span><i class="sw sw-found"></i>발견</span> ' +
            '<span><i class="sw sw-new"></i>신규</span></div>' +
            '<div class="collect-chart">' + cols + '</div>';
    }
    function kpi(n, l) {
        return '<div class="kpi"><div class="n">' + n + '</div><div class="l">' + l + '</div></div>';
    }
    function bar(v, total) {
        var pct = total ? Math.round(v / total * 100) : 0;
        return '<span class="bar-track"><span class="bar-fill" style="display:block;width:' + pct + '%"></span></span>' +
            '<span class="cnt">' + v + '</span>';
    }

    /* ---------- 상세 7섹션 ---------- */
    function openDetail(id) {
        var modal = $('appModal');
        $('appModalTitle').textContent = '불러오는 중…';
        $('appModalBody').innerHTML = '';
        if (typeof modal.showModal === 'function') modal.showModal();
        api('/api/apps/' + encodeURIComponent(id)).then(function (a) {
            $('appModalTitle').textContent = a.name || '앱 상세';
            $('appModalBody').innerHTML = detailHtml(a);
        }).catch(function () {
            $('appModalTitle').textContent = '불러오기 실패';
        });
    }
    function sec(title, inner) {
        if (!inner) return '';
        return '<section class="detail-sec"><h3>' + title + '</h3>' + inner + '</section>';
    }
    function detailHtml(a) {
        // ① 소개 + ④ 세부 설명 (T-073: README 결합 본문은 앞/뒷부분 분리,
        // 그 외는 ①요약 300자 / ④전문으로 분리. T-142: 한/영 각각 분리 후 mdBlock 토글)
        var marker = '— README —';
        function splitBody(full) {
            if (!full) return '';
            if (full.indexOf(marker) >= 0) {
                var p = full.split(marker);
                return p.slice(1).join(marker).trim() || p[0].trim();
            }
            return full;
        }
        function excerpt(full) {
            if (!full) return '';
            if (full.indexOf(marker) >= 0) return full.split(marker)[0].trim().slice(0, 300);
            return full.length > 300 ? full.slice(0, 300) + '…' : full;
        }
        var koFull = a.descriptionKo || '';
        var enFull = a.descriptionSnippet || '';
        var introKo = excerpt(koFull);
        var introEn = excerpt(enFull);
        var introShow = introKo || introEn;
        var introHtml = introShow ? mdBlock(introKo, introEn) : '';
        var funcKo = splitBody(koFull);
        var funcEn = splitBody(enFull);
        var funcShow = funcKo || funcEn;
        // 짧은 본문(요약과 동일)은 ④ 생략 — 중복 해소
        var funcHtml = (funcShow && funcShow !== introShow) ? mdBlock(funcKo, funcEn) : '';
        // ② 스크린샷 (Apple CDN 직접 표시)
        var shots = '';
        if (a.screenshotUrls) {
            var urls = a.screenshotUrls.split(/\r?\n/).map(function (s) { return s.trim(); }).filter(Boolean);
            if (urls.length) {
                shots = '<div class="shot-row">' + urls.map(function (u) {
                    return '<img src="' + esc(u) + '" alt="스크린샷" loading="lazy">';
                }).join('') + '</div>';
            }
        }
        // ③ 특징
        var feats = [];
        if (a.sellerName) feats.push('<div>판매: ' + esc(a.sellerName) + '</div>');
        if (a.averageRating != null) feats.push('<div>평점 ' + a.averageRating + (a.ratingCount != null ? ' (' + a.ratingCount + '개)' : '') + '</div>');
        if (a.stars != null) feats.push('<div>★ ' + a.stars + (a.forks != null ? ' · ⑂ ' + a.forks : '') + (a.issues != null ? ' · 이슈 ' + a.issues : '') + '</div>');
        if (a.primaryLanguage) feats.push('<div>' + esc(a.primaryLanguage) + '</div>');
        if (a.licenseName) feats.push('<div>라이선스: ' + esc(a.licenseName) + '</div>');
        if (a.fileSize) feats.push('<div>용량: ' + fmtSize(a.fileSize) + '</div>');
        if (a.minOs) feats.push('<div>최소 OS: ' + esc(a.minOs) + '</div>');
        if (a.contentRating) feats.push('<div>연령 등급: ' + esc(a.contentRating) + '</div>');
        if (a.category) feats.push('<div>' + esc(a.category) + '</div>');
        if (a.tags) feats.push('<div>태그: ' + esc(a.tags) + '</div>');
        var feat = feats.length ? '<div class="feat-grid">' + feats.join('') + '</div>' : '';
        // ⑤ 새로운 기능 (한국어 기본 + 원문 토글) + 버전별 링크 (T-080)
        var newsPick = pick(a.releaseNotesKo, a.releaseNotes || a.releaseNotesSummary);
        var news = '';
        var hasHistory = a.version || (a.versions && a.versions.length) || newsPick.text;
        if (hasHistory) {
            var rows = (a.versions || []).slice(0, 10).map(function (v) {
                var vlink = v.sourceUrl
                    ? ' <a target="_blank" rel="noopener" href="' + esc(v.sourceUrl) + '">열기</a>'
                    : '';
                return '<tr><td>' + esc(v.version) + '</td><td>' + esc(stripMd(v.notesSummary || '')) + '</td><td>' + vlink + '</td></tr>';
            }).join('');
            var newsKo = newsPick.isKo ? newsPick.text : '';
            var newsEn = newsPick.isKo
                ? (a.releaseNotes || a.releaseNotesSummary || '')
                : newsPick.text;
            news = (newsPick.text ? mdBlock(newsKo, newsEn) : '') +
                '<table class="ver-table"><tr><th>버전</th><th>새 기능</th><th>링크</th></tr>' +
                (a.version ? '<tr><td><b>' + esc(a.version) + '</b> (현재)' +
                    (a.prevVersion ? ' ← <s>' + esc(a.prevVersion) + '</s>' : '') + '</td><td>' +
                    esc(stripMd(a.releaseNotesSummary || '')) + '</td><td></td></tr>' : '') + rows + '</table>';
        } else {
            news = '<p>버전 기록 없음 — ' + fmtDate(a.firstSeenAt) + ' 첫 포착, 다음 업데이트부터 기록됩니다.</p>';
        }
        // ⑥⑦ 홈페이지·다운로드·출처 분리 (T-070: 필드 직접 사용, 둔갑 금지)
        var homeBtn = a.homepageUrl
            ? '<a class="btn-link" target="_blank" rel="noopener" href="' + esc(a.homepageUrl) + '">홈페이지</a>'
            : '';
        var repoBtn = a.repoFullName
            ? '<a class="btn-link" target="_blank" rel="noopener" href="https://github.com/' + esc(a.repoFullName) + '">GitHub Repo</a>'
            : '';
        var storeBtn = a.trackId
            ? '<a class="btn-link" target="_blank" rel="noopener" href="https://apps.apple.com/us/app/id' + a.trackId + '">App Store (현재 버전)</a>'
            : '';
        var srcLinks = (a.sources || []).map(function (s) {
            if (!s.sourceUrl) return '';
            return '<a class="btn-link ghost" target="_blank" rel="noopener" href="' + esc(s.sourceUrl) + '">출처: ' + esc(s.sourceName) + '</a>';
        }).join('');
        var dl = '<div class="btn-row">' + homeBtn + repoBtn + storeBtn + srcLinks + '</div>';
        return sec('① 소개', introHtml) + sec('② 스크린샷', shots) + sec('③ 특징', feat) +
            sec('④ 세부 설명', funcHtml) + sec('⑤ 새로운 기능', news) +
            sec('⑥ 홈페이지 · ⑦ 다운로드 · 출처', dl);
    }
    document.addEventListener('click', function (e) {
        var btn = e.target.closest ? e.target.closest('.orig-toggle') : null;
        if (!btn || !btn.dataset.mdkey) return;
        var t = mdStore[btn.dataset.mdkey];
        if (!t) return;
        var showingKo = t.showing === 'ko';
        t.showing = showingKo ? 'en' : 'ko';
        var box = btn.parentElement.querySelector('[data-mdtext][data-key="' + btn.dataset.mdkey + '"]');
        if (box) box.innerHTML = md(showingKo ? t.en : t.ko);
        btn.textContent = showingKo ? '한국어보기' : '원문보기';
    });

    /* ---------- 헤더 ---------- */
    function loadHeader() {
        api('/api/stats').then(function (s) {
            $('totalCount').textContent = '총 ' + s.totalApps + '개';
            if (s.lastCollectedAt) {
                $('lastUpdated').textContent = '마지막 업데이트: ' + new Date(s.lastCollectedAt).toLocaleString();
            }
        }).catch(function () { /* 무시 */ });
    }

    /* ---------- 초기화 ---------- */
    function setView(v) {
        state.view = v;
        state.page = 1;
        document.querySelectorAll('.view-tabs .tab-btn').forEach(function (b) {
            var on = b.dataset.view === v;
            b.classList.toggle('active', on);
            b.setAttribute('aria-selected', on ? 'true' : 'false');
        });
        $('timelineSection').hidden = v !== 'timeline';
        $('watchlistSection').hidden = v !== 'watchlist';
        $('statsSection').hidden = v !== 'stats';
        // 통계는 전역 대시보드라 목록 필터 칩을 숨김 (보이는 메뉴는 전부 동작 보장)
        var showFilters = v !== 'stats';
        applyFilterPanel(showFilters);
        if (v === 'watchlist') loadWatchlist();
        if (v === 'stats') loadStats();
        window.scrollTo(0, 0);
    }
    document.querySelectorAll('.view-tabs .tab-btn').forEach(function (b) {
        b.onclick = function () { setView(b.dataset.view); };
    });
    $('sortSelect').onchange = function (e) { state.sort = e.target.value; state.page = 1; reloadCurrentView(); };
    $('keyword').addEventListener('input', function (e) {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(function () {
            state.q = e.target.value.trim();
            state.page = 1;
            reloadCurrentView();
        }, 300);
    });
    $('logoLink').onclick = function () {
        state.license = ''; state.tag = ''; state.category = ''; state.q = ''; state.sort = 'newest'; state.page = 1;
        $('keyword').value = '';
        $('sortSelect').value = 'newest';
        syncChips();
        setView('timeline');
        loadTimeline();
    };
    $('totalCount').onclick = function () {
        if (!confirm('지금 수집할까요?')) return;
        fetch('/api/sync', { method: 'POST', body: '{}' })
            .then(function () { toast('수집 요청됨'); })
            .catch(function () { toast('수집 요청 실패'); });
    };
    // T-150: 번역 즉시 실행 (적체 해소용 수동 실행 — 주기 대기 없이 1회 처리)
    $('translateNow').onclick = function () {
        if (!confirm('대기 중인 번역을 지금 실행할까요? (최대 100건)')) return;
        fetch('/api/translate', { method: 'POST', body: '{}' })
            .then(function () { toast('번역 요청됨'); })
            .catch(function () { toast('번역 요청 실패'); });
    };
    var notifPage = 1;
    var NOTIF_PAGE_SIZE = 20;
    $('notifBell').onclick = function () {
        notifPage = 1;
        openNotifCenter();
    };
    function openNotifCenter() {
        $('appModalTitle').textContent = '알림 센터';
        $('appModalBody').innerHTML = '<div class="empty-state">불러오는 중…</div>';
        if (typeof $('appModal').showModal === 'function') $('appModal').showModal();
        api('/api/notifications?page=' + notifPage + '&pageSize=' + NOTIF_PAGE_SIZE).then(function (d) {
            if (!d.notifications.length) {
                $('appModalBody').innerHTML = '<div class="empty-state">알림이 없습니다</div>';
                return;
            }
            var html = d.notifications.map(function (n) {
                return '<section class="detail-sec" data-notif="' + n.id + '" style="cursor:pointer"><h3>' + esc(n.type) + '</h3><p>' + esc(n.summary) + '</p><small>' + fmtDate(n.createdAt) + '</small></section>';
            }).join('');
            var pages = Math.max(1, Math.ceil((d.total || 0) / (d.pageSize || NOTIF_PAGE_SIZE)));
            if (pages > 1) {
                html += '<div class="pagination"><button type="button" id="notifPrev"' + (notifPage <= 1 ? ' disabled' : '') + '>‹ 이전</button>' +
                    '<span> ' + notifPage + ' / ' + pages + ' </span>' +
                    '<button type="button" id="notifNext"' + (notifPage >= pages ? ' disabled' : '') + '>다음 ›</button></div>';
            }
            $('appModalBody').innerHTML = html;
            var prev = $('notifPrev'), next = $('notifNext');
            if (prev) prev.onclick = function () { if (notifPage > 1) { notifPage--; openNotifCenter(); } };
            if (next) next.onclick = function () { notifPage++; openNotifCenter(); };
            $('appModalBody').querySelectorAll('[data-notif]').forEach(function (el) {
                el.onclick = function () {
                    api('/api/notifications/' + el.dataset.notif).then(function (dd) {
                        var t = dd.detail || {};
                        var timeHtml = '';
                        if (t.startedAt && t.finishedAt) {
                            var secs = Math.max(0, Math.round((t.finishedAt - t.startedAt) / 1000));
                            timeHtml = '<p><small>수집 시작 ' + fmtDate(t.startedAt) + ' → 완료 ' + fmtDate(t.finishedAt) + ' (소요 ' + secs + '초)</small></p>';
                        } else {
                            timeHtml = '<p><small>발견 시각 ' + fmtDate(dd.notification.createdAt) + '</small></p>';
                        }
                        el.innerHTML = '<h3>' + esc(dd.notification.type) + '</h3><p>' + esc(dd.notification.summary) + '</p>' + timeHtml;
                    });
                };
            });
        }).catch(function () {
            $('appModalBody').innerHTML = '<div class="empty-state">불러오기 실패</div>';
        });
    };
    $('langToggle').onclick = function () {
        state.lang = state.lang === 'ko' ? 'en' : 'ko';
        $('langToggle').textContent = state.lang === 'ko' ? '한국어' : '원문';
        reloadCurrentView();
    };
    document.querySelectorAll('#collectPeriod button').forEach(function (b) {
        b.onclick = function () {
            state.collectDays = parseInt(b.dataset.days, 10) || 14;
            document.querySelectorAll('#collectPeriod button').forEach(function (x) {
                x.classList.toggle('active', x === b);
            });
            loadStats();
        };
    });
    // T-132: Watchlist 범위 전환 (업데이트=실제 bump만 / 전체=버전 있는 정착앱)
    document.querySelectorAll('#watchModeRow button').forEach(function (b) {
        b.onclick = function () {
            state.watchMode = b.dataset.mode || 'updated';
            state.page = 1;
            document.querySelectorAll('#watchModeRow button').forEach(function (x) {
                x.classList.toggle('active', x === b);
            });
            loadWatchlist();
            window.scrollTo(0, 0);
        };
    });
    $('appModalClose').onclick = function () { $('appModal').close(); };
    $('appModal').addEventListener('click', function (e) {
        if (e.target === $('appModal')) $('appModal').close();
    });

    buildCategoryChips();
    bindLicenseTags();
    bindFilterToggle();
    syncChips();
    loadHeader();
    loadTimeline();
})();
