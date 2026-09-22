// 프롬프트 저널 v1 — 뉴스룸 발행 아카이브 (모달 없음, 서랍 2탭)
(function () {
    'use strict';

    var API_BASE = '';
    var providerDisplay = {
        'OPENROUTER': 'OpenRouter'
    };

    var state = { prompts: [], selPrompt: null, execs: [], selExec: null, showDetailMobile: false };

    function $(id) { return document.getElementById(id); }

    /* ---------- 마크다운 (mac_web T-142 그대로 + GFM 표 패치) ---------- */
    function deslash(s, mode) {
        var BS = String.fromCharCode(92);
        var out = '';
        var i = 0;
        while (i < s.length) {
            var a = s.indexOf(BS, i);
            if (a < 0) { out += s.slice(i); break; }
            var b = s.indexOf(BS, a + 1);
            if (b < 0 || b - a > 82 || b - a < 2) { out += s.slice(i, a + 1); i = a + 1; continue; }
            var inner = s.slice(a + 1, b);
            inner = inner.replace(/^[ \t]+|[ \t]+$/g, '');
            if (!inner || inner.length > 80 || inner.indexOf('\n') >= 0 || inner.indexOf('`') >= 0 ||
                inner.indexOf('<') >= 0 || inner.indexOf('>') >= 0 || inner.indexOf(BS) >= 0) {
                out += s.slice(i, a + 1); i = a + 1; continue;
            }
            if (mode === 2) out += s.slice(i, a) + '`'+ inner + '`';
            else if (mode === 1) out += s.slice(i, a) + '<code>' + inner + '</code>';
            else out += s.slice(i, a) + inner;
            i = b + 1;
        }
        return out;
    }
    function mdInline(s) {
        // 셀·문단 안 줄바꿈 플레이스홀더 → <br>, 선행 "- "는 불릿으로
        s = s.split('\uE000').map(function (seg) {
            return seg.replace(/^\s*-\s+/, '• ');
        }).join('<br>');
        s = deslash(s, 1);
        s = s.replace(/`([^`]+?)`/g, '<code>$1</code>');
        s = s.replace(/\*\*([^*]+?)\*\*/g, '<strong>$1</strong>');
        s = s.replace(/\*([^*\n]+?):\*/g, '<strong>$1:</strong>');
        s = s.replace(/!\[([^\]]*?)\]\((https?:\/\/[^)\s]+?)\)/g, '<a target="_blank" rel="noopener" href="$2">🖼 $1</a>');
        s = s.replace(/\[([^\]]+?)\]\((https?:\/\/[^)\s]+?)\)/g, '<a target="_blank" rel="noopener" href="$2">$1</a>');
        return s;
    }
    function stripHtml(s) {
        return String(s == null ? '' : s).replace(/<\/?[a-zA-Z][^>\n]*>/g, '');
    }
    function isTableSep(s) {
        var t = String(s).trim();
        return /^[\s|:.\-]+$/.test(t) && t.indexOf('-') >= 0 && t.indexOf('|') >= 0;
    }
    function splitRow(s) {
        var t = String(s).trim();
        if (t.charAt(0) === '|') t = t.slice(1);
        if (t.charAt(t.length - 1) === '|') t = t.slice(0, -1);
        return t.split('|').map(function (c) { return c.trim(); });
    }
    /* ---------- v2: 서비스 표 → 카드형 섹션 ---------- */
    function isServiceTable(headArr) {
        var j = headArr.join(' ');
        return /서비스/.test(j) && /모델|ID/.test(j) && /비고|출처|조건/.test(j);
    }
    function colIdx(headArr, re) {
        for (var i = 0; i < headArr.length; i++) {
            if (re.test(headArr[i])) return i;
        }
        return -1;
    }
    function modelLineHtml(part) {
        // `id` 또는 \id\ → code + 복사 버튼, 나머지는 인라인 렌더
        part = deslash(String(part), 2);
        var toks = String(part).split('`');
        var out = '';
        for (var i = 0; i < toks.length; i++) {
            if (i % 2 === 1 && toks[i]) {
                out += '<code>' + toks[i] + '</code>' +
                    '<button class="copy-btn" data-copy="' + escapeAttr(toks[i]) + '" type="button" title="모델 ID 복사">복사</button>';
            } else {
                out += mdInline(toks[i]);
            }
        }
        return out;
    }
    function svcCard(headArr, row) {
        var si = colIdx(headArr, /서비스/);
        var mi = colIdx(headArr, /모델|ID/);
        var ni = colIdx(headArr, /비고|조건/);
        var oi = colIdx(headArr, /출처|링크|URL/);
        function cell(k) { return (k >= 0 && row[k] != null) ? row[k] : ''; }
        var svc = cell(si) || cell(0);
        var models = String(cell(mi)).split('\uE000').join('\n').split('\n').map(function (s) {
            return s.trim().replace(/^[•\-*]\s*/, '');
        }).filter(function (s) { return s; });
        var badges = models.length ? models.map(function (m) {
            return '<div class="svc-model">' + modelLineHtml(m) + '</div>';
        }).join('') : '<div class="svc-model">' + mdInline(cell(mi)) + '</div>';
        var note = cell(ni);
        var srcUrls = (String(cell(oi)).match(/https?:\/\/[^)\s]+/g) || []);
        var src = srcUrls.length ? srcUrls.map(function (u) {
            return '<a class="src-link" target="_blank" rel="noopener" href="' + escapeAttr(u) + '" title="' + escapeAttr(u) + '">↗</a>';
        }).join(' ') : mdInline(cell(oi));
        return '<div class="svc-card"><div class="svc-head">' + mdInline(svc) + '</div>' +
            '<div class="svc-models">' + badges + '</div>' +
            (note ? '<div class="svc-note">' + mdInline(note) + '</div>' : '') +
            (cell(oi) ? '<div class="svc-src">출처 ' + src + '</div>' : '') + '</div>';
    }
    function md(src) {
        // <br>은 줄바꿈으로 보존(플레이스홀더), <URL> 꺾쇠 링크는 md 링크로 — stripHtml 전에 처리
        src = String(src == null ? '' : src).replace(/<br\s*\/?>/gi, '\uE000');
        src = src.replace(/<(https?:\/\/[^>\s]+?)>/g, '[$1]($1)');
        var lines = escapeHtml(stripHtml(src)).split('\n');
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
        for (var i = 0; i < lines.length; i++) {
            var raw = lines[i];
            var line = raw.trim();
            if (/^```/.test(line)) {
                flushList();
                if (inCode) {
                    html += '<pre><code>' + codeBuf.join('\n') + '</code></pre>';
                    codeBuf = [];
                }
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                codeBuf.push(raw.replace(/^\s+|\s+$/g, ''));
                continue;
            }
            if (!line) {
                flushList();
                continue;
            }
            if (line.indexOf('|') >= 0 && i + 1 < lines.length && isTableSep(lines[i + 1])) {
                flushList();
                var head = splitRow(line);
                i += 1;
                var rows = [];
                while (i + 1 < lines.length && lines[i + 1].indexOf('|') >= 0 && lines[i + 1].trim() !== '') {
                    i += 1;
                    if (isTableSep(lines[i])) continue;
                    rows.push(splitRow(lines[i]));
                }
                if (isServiceTable(head)) {
                    html += rows.map(function (r) { return svcCard(head, r); }).join('');
                    continue;
                }
                html += '<div class="md-table-wrap paper"><table><thead><tr>' +
                    head.map(function (c) { return '<th>' + mdInline(c) + '</th>'; }).join('') +
                    '</tr></thead><tbody>' +
                    rows.map(function (r) {
                        return '<tr>' + r.map(function (c) { return '<td>' + mdInline(c) + '</td>'; }).join('') + '</tr>';
                    }).join('') +
                    '</tbody></table></div>';
                continue;
            }
            var h = line.match(/^(#{1,6})\s+(.*)$/);
            if (h) {
                flushList();
                html += h[1].length <= 2 ? '<h4>' + mdInline(h[2]) + '</h4>' : '<h5>' + mdInline(h[2]) + '</h5>';
                continue;
            }
            if (/^(---|\*\*\*|___)\s*$/.test(line)) {
                flushList();
                html += '<hr>';
                continue;
            }
            var q = line.match(/^&gt;\s?(.*)$/);
            if (q) {
                flushList();
                html += '<blockquote>' + mdInline(q[1]) + '</blockquote>';
                continue;
            }
            var ul = line.match(/^[-*+]\s+(.*)$/);
            if (ul) {
                if (listTag !== 'ul') flushList();
                listTag = 'ul';
                var item = ul[1].replace(/^\[([ xX])\]\s+/, function (m, c) {
                    return c.toLowerCase() === 'x' ? '☑ ' : '☐ ';
                });
                listBuf.push('<li>' + mdInline(item) + '</li>');
                continue;
            }
            var ol = line.match(/^\d+[.)]\s+(.*)$/);
            if (ol) {
                if (listTag !== 'ol') flushList();
                listTag = 'ol';
                listBuf.push('<li>' + mdInline(ol[1]) + '</li>');
                continue;
            }
            flushList();
            if (/^인사이트\s*[:：]/.test(line)) {
                html += '<p class="lead">' + mdInline(line) + '</p>';
                continue;
            }
            html += '<p>' + mdInline(line) + '</p>';
        }
        flushList();
        if (inCode && codeBuf.length) html += '<pre><code>' + codeBuf.join('\n') + '</code></pre>';
        return html;
    }
    function stripMd(src) {
        return deslash(stripHtml(String(src == null ? '' : src).replace(/<br\s*\/?>/gi, ' '))
            .replace(/```[\s\S]*?```/g, ' ')
            .replace(/`([^`]*?)`/g, '$1')
            .replace(/^#{1,6}\s+/gm, '')
            .replace(/\*\*([^*]+?)\*\*/g, '$1')
            .replace(/!\[([^\]]*?)\]\([^)]*?\)/g, '$1')
            .replace(/\[([^\]]+?)\]\([^)]*?\)/g, '$1')
            .replace(/^\s*&gt;\s?/gm, '')
            .replace(/^\s*[-*+]\s+/gm, '')
            .replace(/^\s*\d+[.)]\s+/gm, '')
            .replace(/\s+/g, ' ').trim(), 0);
    }

    function toast(msg) {
        var t = $('toast');
        t.textContent = msg;
        t.hidden = false;
        clearTimeout(t._tm);
        t._tm = setTimeout(function () { t.hidden = true; }, 2200);
    }
    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }
    function escapeHtml(s) { return esc(s); }
    function escapeAttr(s) { return esc(s); }
    function formatTime(ms) {
        var d = new Date(ms);
        return d.toLocaleDateString('ko-KR') + ' ' + d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
    }
    function formatDateShort(ms) {
        var d = new Date(ms);
        return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric', weekday: 'short' }) + ' ' +
            d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
    }
    function formatDuration(ms) {
        var v = Number(ms || 0);
        if (v < 1000) return v + 'ms';
        var sec = v / 1000;
        if (sec < 60) return (Math.round(sec * 10) / 10) + '초';
        var m = Math.floor(sec / 60);
        var s = Math.round(sec % 60);
        if (m < 60) return s === 0 ? m + '분' : m + '분 ' + s + '초';
        var h = Math.floor(m / 60);
        var rm = m % 60;
        return rm === 0 ? h + '시간' : h + '시간 ' + rm + '분';
    }
    /* ---------- 뉴스룸: 호수·조간석간·발행주기 ---------- */
    function daypartOf(ms) {
        var h = new Date(ms).getHours();
        if (h >= 5 && h < 12) return '조간';
        if (h >= 17 && h < 24) return '석간';
        return '단신';
    }
    function editionNoOf(ex) {
        var idx = state.execs.findIndex(function (e) { return e.id === ex.id; });
        if (idx < 0) return null;
        return state.execs.length - idx;
    }
    function formatEditionDate(ms) {
        var d = new Date(ms);
        return d.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' });
    }
    function editionLabel(ex) {
        var n = editionNoOf(ex);
        var dp = daypartOf(ex.executedAt);
        if (state.execs.length && ex.scheduleHint === 'hourly') dp = '속보';
        return '제' + n + '호 발행 · ' + formatEditionDate(ex.executedAt) + ' ' + dp + ' · 취재 ' + formatDuration(ex.durationMs);
    }
    function cyclePillText(p) {
        if (!p) return '';
        if (p.scheduleType === 'once') return '단행본 · 1회 발행';
        var t = p.scheduleValue || '09:00';
        if (t === '09:00' || /^0?9:00/.test(t)) return '일간 · 매일 ' + t + ' 발행';
        return '일간 · 매일 ' + t + ' 발행';
    }
    function firstLineOf(ex) {
        // v2: stripMd는 개행을 뭉개므로 줄 보존 버전 사용
        var lines = textLines(ex.response || '');
        return (lines[0] || '').replace(/^#+\s*/, '');
    }
    function briefingDateOf(ex) {
        var m = stripMd(ex.response || '').match(/(\d{4}-\d{2}-\d{2})/);
        return m ? m[1] : null;
    }
    function headlineOf(ex) {
        // v2: 긴 레거시 첫줄에서도 35자 이내 핵심만 H1으로
        if (ex.status !== 'SUCCESS') return '취재 실패';
        var raw = firstLineOf(ex);
        var dash = raw.search(/\s[—–]\s/);
        if (dash < 0) dash = raw.search(/\s-\s/);
        var core = raw;
        if (dash > 0) {
            var tail = raw.slice(dash + 3).trim();
            if (tail.length >= 4) core = tail;
        }
        core = core.replace(/^\d{4}-\d{2}-\d{2}\s*/, '').trim() || raw;
        if (core.length > 35) {
            var cut = core.slice(0, 35);
            var sp = cut.search(/\s[^\s]*$/);
            core = (sp > 15 ? cut.slice(0, sp) : cut).trim() + '…';
        }
        if (!core) return '신규 무료 모델 없음';
        if (/^오늘의 뉴스\s*\+\s*인사이트/.test(core) && core.length < 30) return '신규 무료 모델 없음';
        return core;
    }
    function insightOf(ex) {
        // v2: 아카이브 요약은 헤드라인이 아니라 "인사이트:" 1줄
        var lines = textLines(ex.response || ex.errorMessage || '');
        for (var i = 0; i < lines.length; i++) {
            var m = lines[i].match(/^인사이트\s*[:：]\s*(.+)$/);
            if (m && m[1].trim()) return m[1].trim().slice(0, 80);
        }
        return headlineOf(ex);
    }
    function textLines(src) {
        var t = stripHtml(String(src == null ? '' : src).replace(/<br\s*\/?>/gi, '\n'));
        t = t.replace(/```/g, '');
        return t.split('\n').map(function (ln) {
            return deslash(ln.replace(/`([^`]*?)`/g, '$1'), 0)
                .replace(/^#{1,6}\s+/, '')
                .replace(/\*\*([^*]+?)\*\*/g, '$1')
                .replace(/!\[([^\]]*?)\]\([^)]*?\)/g, '$1')
                .replace(/\[([^\]]+?)\]\([^)]*?\)/g, '$1')
                .replace(/^\s*>\s?/, '')
                .replace(/^\s*[-*+]\s+/, '')
                .replace(/^\s*\d+[.)]\s+/, '')
                .replace(/<(https?:\/\/[^>\s]+?)>/g, '$1')
                .replace(/[ \t]+/g, ' ').trim();
        }).filter(function (ln) { return ln; });
    }
    function hasBreakingNews(ex) {
        // 변경 요약에 실제 신규 모델이 있을 때만 BREAKING
        var resp = ex.response || '';
        var m = resp.match(/신규 추가된 무료 모델([\s\S]{0,300})/);
        if (m) {
            var seg = m[1].split('\n').slice(0, 4).join(' ');
            if (/없음|변경 없음|어제와 동일|해당 없음/.test(seg)) return false;
            return true;
        }
        // 다른 채널: 최근 24h SUCCESS면 BREAKING
        return ex.status === 'SUCCESS' && (Date.now() - ex.executedAt) < 24 * 3600 * 1000;
    }
    function updateMasthead() {
        var now = new Date();
        var td = $('todayDate');
        if (td) td.textContent = now.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' });
        var sub = $('todayDateSub');
        if (sub) {
            var y = now.getFullYear();
            var m = ('0' + (now.getMonth() + 1)).slice(-2);
            var d = ('0' + now.getDate()).slice(-2);
            var wk = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'][now.getDay()];
            sub.textContent = y + '.' + m + '.' + d + ' ' + wk;
        }
        var p = curPrompt();
        var cl = $('channelLine');
        if (cl) cl.textContent = p ? (p.title + ' · 발행인: 편집국 자동 취재 시스템') : '브리핑 채널';
        var pill = $('cyclePill');
        if (pill) {
            if (p) { pill.hidden = false; pill.textContent = '발행 주기: ' + cyclePillText(p); }
            else { pill.hidden = true; }
        }
        var box = $('editionNo');
        if (box) box.textContent = state.execs.length ? String(state.execs.length) : '–';
    }
    function api(path, opts) {
        return fetch(API_BASE + path, opts).then(function (r) {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
        });
    }

    /* ---------- R44 관리 토큰: 쓰기 API 자동 첨부 + 401 시 입력·재시도 ---------- */
    (function () {
        var KEY = 'jupjup_admin_token_3030';
        var origFetch = window.fetch.bind(window);
        window.fetch = function (input, init) {
            var url = typeof input === 'string' ? input : (input && input.url) || '';
            var method = ((init && init.method) || (input && input.method) || 'GET').toUpperCase();
            var isApi = url.indexOf('/api/') === 0 || url.indexOf(API_BASE + '/api/') === 0;
            if (!isApi || method === 'GET' || method === 'HEAD') return origFetch(input, init);
            var tok = '';
            try { tok = localStorage.getItem(KEY) || ''; } catch (e) {}
            var headers = {};
            if (init && init.headers) { for (var k in init.headers) headers[k] = init.headers[k]; }
            if (tok) headers['X-Auth-Token'] = tok;
            var patched = { method: method, headers: headers };
            for (var p in (init || {})) { if (p !== 'headers' && p !== 'method') patched[p] = init[p]; }
            return origFetch(input, patched).then(function (r) {
                if (r.status !== 401) return r;
                var v = prompt('관리 토큰을 입력하세요 (기기 내 브라우저에서 http://127.0.0.1:3030/api/admin/token 조회)');
                if (!v) return r;
                try { localStorage.setItem(KEY, v.trim()); } catch (e) {}
                patched.headers['X-Auth-Token'] = v.trim();
                return origFetch(input, patched);
            });
        };
    })();

    /* ---------- 초기화 ---------- */
    function init() {
        $('logoBtn').addEventListener('click', function () {
            state.showDetailMobile = false;
            if (state.prompts.length) selectPrompt(state.prompts[0].id);
        });
        $('settingsBtn').addEventListener('click', openDrawer);
        $('drawerClose').addEventListener('click', closeDrawer);
        $('drawerBackdrop').addEventListener('click', closeDrawer);
        $('btnNewPrompt').addEventListener('click', newPrompt);
        $('btnCancelPrompt').addEventListener('click', hidePromptForm);
        $('promptForm').addEventListener('submit', savePrompt);
        $('pfProvider').addEventListener('change', function () { populateModelSelect($('pfProvider').value); });
        var tc = $('tabChannel');
        var ts = $('tabSources');
        var tr = $('tabRuns');
        if (tc && ts) {
            tc.addEventListener('click', function () { switchDrawerTab('channel'); });
            ts.addEventListener('click', function () { switchDrawerTab('sources'); });
        }
        if (tr) tr.addEventListener('click', function () { switchDrawerTab('runs'); });
        var runRefresh = $('btnRunRefresh');
        if (runRefresh) runRefresh.addEventListener('click', loadRuns);
        updateMasthead();
        loadAll();
    }

    function loadAll() {
        loadHealth();
        api('/api/prompts').then(function (prompts) {
            state.prompts = prompts || [];
            renderChips();
            renderChannels();
            if (state.prompts.length) {
                selectPrompt(state.prompts[0].id);
            } else {
                $('dateList').innerHTML = '';
                $('reportBody').innerHTML = '<p class="empty-state">개설된 채널이 없습니다.<br>⚙️ 설정에서 새 브리핑 채널을 개설해보세요.</p>';
                $('lastRun').textContent = '발행된 호수 없음';
                updateMasthead();
            }
        }).catch(function () {
            $('reportBody').innerHTML = '<p class="empty-state">채널을 불러올 수 없습니다</p>';
        });
        loadProviders();
        populateModelSelect($('pfProvider').value);
    }

    function loadHealth() {
        api('/api/health').then(function () {
            $('statusDot').className = 'status-dot running';
        }).catch(function () {
            $('statusDot').className = 'status-dot stopped';
            toast('서버 연결 실패');
        });
    }

    /* ---------- 프롬프트 칩 ---------- */
    function renderChips() {
        var box = $('promptChips');
        box.innerHTML = state.prompts.map(function (p) {
            return '<button class="chip' + (state.selPrompt === p.id ? ' active' : '') + (p.enabled ? '' : ' off') + '" data-id="' + p.id + '" type="button">' +
                esc(p.title) + (p.enabled ? '' : ' · OFF') + '</button>';
        }).join('');
        box.querySelectorAll('.chip').forEach(function (c) {
            c.addEventListener('click', function () { selectPrompt(Number(c.dataset.id)); });
        });
    }

    /* ---------- 마스터-디테일 ---------- */
    function selectPrompt(id, focusExecId) {
        state.selPrompt = id;
        state.selExec = null;
        state.showDetailMobile = false;
        renderChips();
        $('dateList').innerHTML = '<p class="empty-state">불러오는 중…</p>';
        $('reportBody').innerHTML = '<p class="empty-state">불러오는 중…</p>';
        api('/api/prompts/' + id + '/executions?limit=50').then(function (exs) {
            if (state.selPrompt !== id) return;
            state.execs = exs || [];
            renderDates();
            if (state.execs.length) {
                var target = focusExecId ? state.execs.find(function (e) { return e.id === focusExecId; }) : null;
                selectExec((target || state.execs[0]).id);
                var last = state.execs[0];
                $('lastRun').textContent = editionLabel(last);
            } else {
                $('lastRun').textContent = '아직 발행 없음';
                renderEmptyReport();
            }
            updateMasthead();
        }).catch(function () {
            $('dateList').innerHTML = '<p class="empty-state">지난 호를 불러올 수 없습니다</p>';
        });
    }

    function renderDates() {
        var box = $('dateList');
        box.className = 'date-list' + (state.showDetailMobile ? ' detail-open' : '');
        if (!state.execs.length) {
            box.innerHTML = '<p class="empty-state">아직 발행된 호수가 없습니다.</p>';
            return;
        }
        var latest = state.execs.filter(function (e) { return e.status === 'SUCCESS'; })[0];
        var isFresh = latest ? hasBreakingNews(latest) : false;
        var title = '<p class="archive-title">발행 아카이브' +
            (isFresh ? '<span class="badge badge-breaking">BREAKING</span>' : '<span class="badge badge-rest">휴간</span>') + '</p>';
        box.innerHTML = title + state.execs.map(function (ex) {
            var n = editionNoOf(ex);
            var dp = daypartOf(ex.executedAt);
            var prev = insightOf(ex);
            var dot = ex.status === 'SUCCESS' ? (isFresh ? 'success' : 'rest') : 'failed';
            return '<button class="date-item' + (state.selExec === ex.id ? ' active' : '') + '" data-id="' + ex.id + '" type="button">' +
                '<span class="date-main"><span class="dot ' + dot + '"></span>' +
                '<span class="edition-tag">제' + n + '호 · ' + esc(formatEditionDate(ex.executedAt)) + ' ' + dp + '</span>' +
                '<span>' + esc(formatDuration(ex.durationMs)) + '</span></span>' +
                '<span class="date-sub">' + esc(prev) + '</span></button>';
        }).join('');
        box.querySelectorAll('.date-item').forEach(function (b) {
            b.addEventListener('click', function () { selectExec(Number(b.dataset.id)); });
        });
    }

    function selectExec(id) {
        state.selExec = id;
        state.showDetailMobile = true;
        renderDates();
        renderReport();
        if (window.innerWidth <= 900) {
            $('dateList').className = 'date-list detail-open';
            $('reportBody').className = 'report-body';
        }
    }

    function curPrompt() {
        return state.prompts.find(function (p) { return p.id === state.selPrompt; });
    }
    function curExec() {
        return state.execs.find(function (e) { return e.id === state.selExec; });
    }

    function renderEmptyReport() {
        var p = curPrompt();
        $('reportBody').className = 'report-body';
        $('reportBody').innerHTML =
            '<h2 class="report-title serif">' + esc(p ? p.title : '') + '</h2>' +
            '<div class="report-meta">아직 발행된 호수가 없습니다.</div>' +
            '<div class="report-actions"><button class="btn btn-primary" id="rbRun" type="button">최신호 발행</button></div>' +
            promptEditHtml(p);
        bindReportActions();
    }

    function promptEditHtml(p) {
        if (!p) return '';
        return '<button class="prompt-toggle" id="rbPromptToggle" type="button">취재 지침서 보기 ▾</button>' +
            '<div class="prompt-edit-box" id="rbPromptBox" hidden>' +
            '<textarea id="rbPromptText" rows="6">' + esc(p.content || '') + '</textarea>' +
            '<div class="report-actions" style="margin-top:8px">' +
            '<button class="btn btn-primary" id="rbPromptSave" type="button">지침서 저장</button>' +
            '<button class="btn" id="rbPromptEdit" type="button">편집국에서 열기</button>' +
            '</div></div>';
    }

    function renderReport() {
        var p = curPrompt();
        var ex = curExec();
        if (!ex) { renderEmptyReport(); return; }
        var body = ex.status === 'SUCCESS' ? md(ex.response || '') : ('<p class="error-text">' + esc(ex.errorMessage || '취재 실패') + '</p>');
        var head = ex.status === 'SUCCESS' ? headlineOf(ex) : '취재 실패';
        var bdate = briefingDateOf(ex);
        $('reportBody').className = 'report-body';
        $('reportBody').innerHTML =
            '<button class="btn back-btn" id="rbBack" type="button">← 아카이브</button>' +
            '<div class="kicker">' + esc(p ? p.title : 'PROMPT JOURNAL') + ' · 제' + editionNoOf(ex) + '호' +
            (bdate ? ' · ' + esc(bdate) + ' 일일 브리핑' : '') + '</div>' +
            '<h2 class="report-title serif">' + esc(head) + '</h2>' +
            '<div class="report-meta" title="' + esc('취재원: ' + (providerDisplay[ex.provider] || ex.provider) + ' ' + (ex.modelId || '')) + '">' + esc(editionLabel(ex)) + '</div>' +
            '<div class="report-actions">' +
            '<button class="btn btn-primary" id="rbRun" type="button">최신호 발행</button>' +
            '<button class="btn" id="rbCopy" type="button">기사 복사</button>' +
            '<button class="btn btn-danger" id="rbDelete" type="button">이 호 폐기</button>' +
            '</div>' +
            promptEditHtml(p) +
            '<div class="md-body" id="rbBody">' + body + '</div>';
        bindReportActions();
    }

    function bindReportActions() {
        var rbBody = $('rbBody');
        if (rbBody) rbBody.addEventListener('click', function (e) {
            var b = e.target && e.target.closest ? e.target.closest('[data-copy]') : null;
            if (b) copyText(b.getAttribute('data-copy'), '모델 ID가 복사되었습니다');
        });
        var back = $('rbBack');
        if (back) back.addEventListener('click', function () {
            state.showDetailMobile = false;
            $('dateList').className = 'date-list';
            $('reportBody').className = 'report-body list-open';
        });
        var run = $('rbRun');
        if (run) run.addEventListener('click', executeSelected);
        var copy = $('rbCopy');
        if (copy) copy.addEventListener('click', function () {
            var ex = curExec();
            var text = (ex && ex.response) || '';
            copyText(text);
        });
        var del = $('rbDelete');
        if (del) del.addEventListener('click', deleteSelected);
        var tog = $('rbPromptToggle');
        if (tog) tog.addEventListener('click', function () {
            var box = $('rbPromptBox');
            box.hidden = !box.hidden;
            tog.textContent = box.hidden ? '취재 지침서 보기 ▾' : '취재 지침서 닫기 ▴';
        });
        var save = $('rbPromptSave');
        if (save) save.addEventListener('click', function () {
            var p = curPrompt();
            var content = $('rbPromptText').value.trim();
            if (!content) { toast('지침서 내용을 입력하세요'); return; }
            api('/api/prompts/' + p.id, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: content })
            }).then(function (d) {
                if (!d.ok) { toast('저장 실패: ' + (d.error || '오류')); return; }
                p.content = content;
                toast('취재 지침서가 저장되었습니다');
            }).catch(function () { toast('저장에 실패했습니다'); });
        });
        var edit = $('rbPromptEdit');
        if (edit) edit.addEventListener('click', function () {
            var p = curPrompt();
            if (!p) return;
            api('/api/prompts/' + p.id).then(function (full) {
                openDrawer();
                switchDrawerTab('channel');
                fillPromptForm(full);
            }).catch(function () { toast('채널을 불러올 수 없습니다'); });
        });
    }

    function executeSelected() {
        var p = curPrompt();
        if (!p) return;
        if (!confirm('「' + p.title + '」 최신호를 발행할까요?')) return;
        api('/api/prompts/' + p.id + '/execute', { method: 'POST' }).then(function (d) {
            toast(d.ok ? '최신호 발행이 예약되었습니다' : '발행 실패: ' + (d.error || '오류'));
            if (d.ok) setTimeout(function () { selectPrompt(p.id); }, 2000);
        }).catch(function () { toast('발행 예약에 실패했습니다'); });
    }

    function deleteSelected() {
        var ex = curExec();
        if (!ex) return;
        if (!confirm('이 호를 폐기할까요?')) return;
        fetch(API_BASE + '/api/executions/' + ex.id, { method: 'DELETE' }).then(function () {
            toast('이 호를 폐기했습니다');
            selectPrompt(state.selPrompt);
        }).catch(function () { toast('폐기에 실패했습니다'); });
    }

    function copyText(text, msg) {
        function done() { toast(msg || '기사가 복사되었습니다'); }
        function fallback() {
            var ta = document.createElement('textarea');
            ta.value = text;
            ta.style.position = 'fixed';
            ta.style.opacity = '0';
            document.body.appendChild(ta);
            ta.focus();
            ta.select();
            try { document.execCommand('copy'); done(); }
            catch (e) { toast('복사를 지원하지 않는 환경입니다'); }
            document.body.removeChild(ta);
        }
        if (navigator.clipboard && window.isSecureContext) {
            navigator.clipboard.writeText(text).then(done, fallback);
        } else { fallback(); }
    }

    /* ---------- 서랍 ---------- */
    function openDrawer() {
        $('drawer').hidden = false;
        $('drawerBackdrop').hidden = false;
    }
    function closeDrawer() {
        stopRunPolling();
        $('drawer').hidden = true;
        $('drawerBackdrop').hidden = true;
    }
    function switchDrawerTab(which) {
        var ch = which === 'channel';
        var runs = which === 'runs';
        $('tabChannel').classList.toggle('active', ch);
        $('tabSources').classList.toggle('active', which === 'sources');
        $('tabRuns').classList.toggle('active', runs);
        $('panelChannel').hidden = !ch;
        $('panelSources').hidden = which !== 'sources';
        $('panelRuns').hidden = !runs;
        if (runs) {
            loadRuns();
            startRunPolling();
        } else {
            stopRunPolling();
        }
    }

    /* ---------- 실행기록 (모든 채널 실행 내역) ---------- */
    var runPollTimer = null;
    function startRunPolling() {
        stopRunPolling();
        runPollTimer = setInterval(loadRuns, 10000);
    }
    function stopRunPolling() {
        if (runPollTimer) { clearInterval(runPollTimer); runPollTimer = null; }
    }
    function loadRuns() {
        api('/api/executions?limit=80').then(function (exs) {
            if ($('panelRuns').hidden) return;
            state.runs = exs || [];
            renderRuns();
        }).catch(function () {
            if ($('panelRuns').hidden) return;
            $('runList').innerHTML = '<p class="empty-state">실행 기록을 불러올 수 없습니다</p>';
        });
    }
    function runDateLabel(ms) {
        var d = new Date(ms);
        return d.toLocaleString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    }
    function renderRuns() {
        var list = $('runList');
        if (!state.runs.length) {
            list.innerHTML = '<p class="empty-state">아직 실행 기록이 없습니다. 채널을 만들어 발행해 보세요.</p>';
            return;
        }
        list.innerHTML = state.runs.map(function (ex) {
            var ok = ex.status === 'SUCCESS';
            var err = ex.errorMessage || '';
            var canView = state.prompts.some(function (x) { return x.id === ex.promptId; });
            var p = state.prompts.find(function (x) { return x.id === ex.promptId; });
            return '<div class="run-item' + (ok ? '' : ' failed') + '">' +
                '<div class="run-head">' +
                '<span class="run-status ' + (ok ? 'ok' : 'fail') + '">' + (ok ? '✓ 성공' : '✕ 실패') + '</span>' +
                '<span class="run-title">' + esc(p ? p.title : ('채널 #' + ex.promptId)) + '</span>' +
                '</div>' +
                '<div class="run-model">' + esc(ex.modelId || '') + ' · ' + runDateLabel(ex.executedAt) +
                ' · ' + esc(formatDuration(ex.durationMs)) + '</div>' +
                (err ? '<div class="run-error">' + esc(err) + '</div>' : '') +
                '<div class="run-actions">' +
                (canView ? '<button class="btn btn-sm" data-run="view" data-id="' + ex.id + '" data-prompt="' + ex.promptId + '" type="button">보기</button>' : '') +
                (!ok ? '<button class="btn btn-sm" data-run="retry" data-id="' + ex.promptId + '" type="button">재발행</button>' : '') +
                '<button class="btn btn-sm btn-danger" data-run="del" data-id="' + ex.id + '" type="button">폐기</button>' +
                '</div></div>';
        }).join('');
        list.querySelectorAll('[data-run]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var act = btn.dataset.run;
                if (act === 'view') {
                    stopRunPolling();
                    closeDrawer();
                    selectPrompt(Number(btn.dataset.prompt), Number(btn.dataset.id));
                } else if (act === 'retry') {
                    api('/api/prompts/' + btn.dataset.id + '/execute', { method: 'POST' }).then(function (d) {
                        toast(d.ok ? '재발행이 예약되었습니다' : '재발행 실패: ' + (d.error || '오류'));
                        if (d.ok) setTimeout(loadRuns, 5000);
                    }).catch(function () { toast('재발행 예약에 실패했습니다'); });
                } else if (act === 'del') {
                    if (!confirm('이 실행 기록을 폐기할까요?')) return;
                    fetch(API_BASE + '/api/executions/' + btn.dataset.id, { method: 'DELETE' }).then(function () {
                        toast('실행 기록을 폐기했습니다');
                        loadRuns();
                    }).catch(function () { toast('폐기에 실패했습니다'); });
                }
            });
        });
    }
    function newPrompt() {
        openDrawer();
        $('pfId').value = '';
        $('promptForm').classList.remove('hidden');
        $('promptForm').reset();
        $('pfEnabled').checked = true;
        populateModelSelect('OPENROUTER');
        $('promptForm').scrollIntoView();
    }
    function hidePromptForm() { $('promptForm').classList.add('hidden'); }
    function fillPromptForm(p) {
        $('promptForm').classList.remove('hidden');
        $('pfId').value = p.id;
        $('pfTitle').value = p.title || '';
        $('pfContent').value = p.content || '';
        $('pfProvider').value = p.provider;
        $('pfScheduleType').value = p.scheduleType;
        $('pfScheduleValue').value = p.scheduleValue;
        $('pfEnabled').checked = !!p.enabled;
        $('pfUsePrev').checked = !!p.usePreviousResult;
        populateModelSelect(p.provider, p.modelId);
        $('promptForm').scrollIntoView();
    }
    function savePrompt(e) {
        e.preventDefault();
        var id = $('pfId').value;
        var body = {
            title: $('pfTitle').value.trim(),
            content: $('pfContent').value.trim(),
            provider: $('pfProvider').value,
            modelId: $('pfModel').value,
            scheduleType: $('pfScheduleType').value,
            scheduleValue: $('pfScheduleValue').value,
            enabled: $('pfEnabled').checked,
            usePreviousResult: $('pfUsePrev').checked
        };
        if (!body.title || !body.content) { toast('제목과 내용을 입력하세요'); return; }
        fetch(API_BASE + (id ? '/api/prompts/' + id : '/api/prompts'), {
            method: id ? 'PUT' : 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).then(function (r) { return r.json(); }).then(function (d) {
            if (!d.ok) { toast('저장 실패: ' + (d.error || '오류')); return; }
            toast('저장되었습니다');
            hidePromptForm();
            var nid = id ? Number(id) : d.id;
            loadAllSilent(nid);
        }).catch(function () { toast('저장에 실패했습니다'); });
    }
    function loadAllSilent(selectId) {
        api('/api/prompts').then(function (prompts) {
            state.prompts = prompts || [];
            renderChips();
            renderChannels();
            selectPrompt(selectId || (state.prompts[0] && state.prompts[0].id));
        });
    }

    /* ---------- 브리핑 채널 목록 ---------- */
    function renderChannels() {
        var box = $('channelList');
        if (!box) return;
        if (!state.prompts.length) {
            box.innerHTML = '<p class="empty-state">개설된 채널이 없습니다.<br>+ 새 채널로 개설해보세요.</p>';
            return;
        }
        box.innerHTML = state.prompts.map(function (p) {
            return '<div class="channel-card' + (p.enabled ? '' : ' off') + '" data-id="' + p.id + '">' +
                '<div class="channel-card-head"><span class="channel-card-title">' + esc(p.title) + '</span>' +
                '<div class="channel-card-actions">' +
                '<button class="btn" data-action="edit" type="button">편집</button>' +
                '<button class="btn btn-danger" data-action="del" type="button">폐간</button>' +
                '</div></div>' +
                '<div class="channel-card-meta">' + esc(cyclePillText(p)) + (p.enabled ? '' : ' · 휴간 중') + '</div></div>';
        }).join('');
        box.querySelectorAll('[data-action="edit"]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var id = Number(btn.closest('.channel-card').dataset.id);
                api('/api/prompts/' + id).then(fillPromptForm)
                    .catch(function () { toast('채널을 불러올 수 없습니다'); });
            });
        });
        box.querySelectorAll('[data-action="del"]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var card = btn.closest('.channel-card');
                var id = Number(card.dataset.id);
                var p = state.prompts.find(function (x) { return x.id === id; });
                if (!confirm('「' + (p ? p.title : id) + '」 채널을 폐간할까요? 지난 호 기록도 함께 삭제됩니다.')) return;
                fetch(API_BASE + '/api/prompts/' + id, { method: 'DELETE' }).then(function () {
                    toast('채널을 폐간했습니다');
                    hidePromptForm();
                    loadAllSilent(state.selPrompt === id ? undefined : state.selPrompt);
                }).catch(function () { toast('폐간에 실패했습니다'); });
            });
        });
    }

    function populateProviderSelect() {
        populateModelSelect($('pfProvider').value);
    }
    function populateModelSelect(provider, selectedId) {
        var sel = $('pfModel');
        sel.innerHTML = '<option value="">모델 불러오는 중…</option>';
        api('/api/providers').then(function (providers) {
            var p = providers.find(function (x) { return x.name === provider; });
            if (!p) return;
            var enabled = p.models.filter(function (m) { return m.enabled; });
            var list = enabled.length > 0 ? enabled : p.models;
            // 저장된 모델이 목록에 없어도 첫 항목으로 둔갑시키지 않고 그대로 표시 (stale 내성)
            var stale = selectedId && !list.some(function (m) { return m.id === selectedId; });
            var staleOpt = stale
                ? '<option value="' + escapeAttr(selectedId) + '" selected>' + esc(selectedId) + ' (목록에 없음)</option>'
                : '';
            sel.innerHTML = (list.length === 0 && !stale)
                ? '<option value="">모델 없음 (취재원 동기화 필요)</option>'
                : staleOpt + list.map(function (m) {
                    return '<option value="' + escapeAttr(m.id) + '"' + (m.id === selectedId ? ' selected' : '') + '>' +
                        esc(m.id) + (m.contextWindow ? ' (' + m.contextWindow + ')' : '') + '</option>';
                }).join('');
        }).catch(function () {
            sel.innerHTML = '<option value="">모델 로드 실패</option>';
        });
    }

    /* ---------- 취재원 ---------- */
    function loadProviders() {
        api('/api/providers').then(function (providers) {
            renderProviders(providers);
            loadSearchEngine();
        }).catch(function () {
            $('providerList').innerHTML = '<p class="empty-state">취재원 정보를 불러올 수 없습니다</p>';
            loadSearchEngine();
        });
    }
    function renderProviders(providers) {
        if (!providers) return;
        $('providerList').innerHTML = providers.map(function (p) {
            return '<div class="provider-card" data-name="' + p.name + '">' +
                '<div class="provider-card-header"><span class="provider-card-title">' + esc(p.displayName) + '</span>' +
                '<div class="provider-card-actions">' +
                '<button class="btn btn-primary" data-action="refresh" type="button">취재원 동기화</button>' +
                '<button class="btn" data-action="all-on" type="button">모두 투입</button>' +
                '<button class="btn" data-action="all-off" type="button">모두 해제</button>' +
                '</div></div>' +
                '<div class="provider-key-status' + (p.hasApiKey ? ' has' : '') + '">' +
                (p.hasApiKey ? '✓ 취재원 연결됨' : '취재원 미연결 — 아래에서 입력') +
                ' <button class="btn" data-action="key" type="button">' + (p.hasApiKey ? '키 교체' : '키 등록') + '</button></div>' +
                '<div class="provider-search-row"><input type="search" class="provider-search" placeholder="취재원 검색 (이름·ID)" autocomplete="off">' +
                '<span class="provider-count">' + p.models.length + '개</span></div>' +
                '<div class="provider-models">' +
                p.models.map(function (m) {
                    return '<div class="provider-model" data-search="' + escapeAttr(((m.id + ' ' + (m.name || '')).toLowerCase())) + '">' +
                        '<div class="provider-model-info"><span class="model-id">' + esc(m.id) + '</span>' +
                        (m.contextWindow ? '<div class="model-ctx">ctx ' + m.contextWindow.toLocaleString('ko-KR') + '</div>' : '') + '</div>' +
                        '<label class="checkbox-label provider-model-toggle"><input type="checkbox"' + (m.enabled ? ' checked' : '') +
                        ' data-model="' + escapeAttr(m.id) + '"><span>투입</span></label></div>';
                }).join('') + '</div></div>';
        }).join('');

        $('providerList').querySelectorAll('[data-action="refresh"]').forEach(function (btn) {
            btn.addEventListener('click', function () { refreshProviderModels(btn.closest('.provider-card').dataset.name, btn); });
        });
        $('providerList').querySelectorAll('[data-action="all-on"]').forEach(function (btn) {
            btn.addEventListener('click', function () { setAllModelsEnabled(btn.closest('.provider-card').dataset.name, true, btn); });
        });
        $('providerList').querySelectorAll('[data-action="all-off"]').forEach(function (btn) {
            btn.addEventListener('click', function () { setAllModelsEnabled(btn.closest('.provider-card').dataset.name, false, btn); });
        });
        $('providerList').querySelectorAll('[data-action="key"]').forEach(function (btn) {
            btn.addEventListener('click', function () { askApiKey(btn.closest('.provider-card').dataset.name); });
        });
        $('providerList').querySelectorAll('.provider-search').forEach(function (input) {
            input.addEventListener('input', function () {
                var card = input.closest('.provider-card');
                var q = input.value.trim().toLowerCase();
                var shown = 0;
                card.querySelectorAll('.provider-model').forEach(function (row) {
                    var hit = !q || (row.dataset.search || '').indexOf(q) >= 0;
                    row.style.display = hit ? '' : 'none';
                    if (hit) shown++;
                });
                var total = card.querySelectorAll('.provider-model').length;
                card.querySelector('.provider-count').textContent = q ? (shown + ' / ' + total + '개') : (total + '개');
            });
        });
        $('providerList').querySelectorAll('.provider-model-toggle input').forEach(function (cb) {
            cb.addEventListener('change', function () {
                toggleModelEnabled(cb.closest('.provider-card').dataset.name, cb.dataset.model, cb.checked);
            });
        });
    }

    /* ---------- 검색 엔진 (Exa) — 취재원 하단 카드 ---------- */
    function loadSearchEngine() {
        var list = $('providerList');
        var old = list.querySelector('.provider-card[data-engine="exa"]');
        if (old) old.remove();
        api('/api/search/key').then(function (d) {
            var hasKey = !!(d && d.hasApiKey);
            var wrapper = document.createElement('div');
            wrapper.innerHTML =
                '<div class="provider-card" data-engine="exa">' +
                '<div class="provider-card-header"><span class="provider-card-title">검색 엔진 · Exa</span>' +
                '<div class="provider-card-actions"><button class="btn" data-action="search-test" type="button">검색 테스트</button></div></div>' +
                '<div class="provider-key-status' + (hasKey ? ' has' : '') + '">' +
                (hasKey ? '✓ 검색엔진 연결됨' : '검색엔진 미연결 — 아래에서 입력') +
                ' <button class="btn" data-action="search-key" type="button">' + (hasKey ? '키 교체' : '키 등록') + '</button></div>' +
                '<div class="exa-status" data-role="exa-status"></div>' +
                '<div class="provider-models"><p class="empty-state exa-note">' +
                '리포트 생성 시 사실·최신 자료를 Exa 검색으로 실측 수집해 [웹 검색 결과]로 주입합니다. ' +
                '무료 1,000건/월 (neural 검색 단가 $0.007/건).</p></div>' +
                '</div>';
            var card = wrapper.firstChild;
            list.appendChild(card);
            card.querySelector('[data-action="search-key"]').addEventListener('click', askSearchApiKey);
            card.querySelector('[data-action="search-test"]').addEventListener('click', function (e) {
                testSearch(e.currentTarget);
            });
        }).catch(function () { /* 키 상태 조회 실패 — 카드 생략 */ });
    }

    function askSearchApiKey() {
        var key = prompt('Exa 검색엔진 API 키를 입력하세요 (빈 값 = 키 삭제)');
        if (key === null) return;
        api('/api/search/key', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ apiKey: key.trim() })
        }).then(function (data) {
            toast(data.ok ? 'Exa 키가 저장되었습니다' : '저장 실패: ' + (data.error || '오류'));
            if (data.ok) loadProviders();
        }).catch(function () { toast('저장 요청 실패'); });
    }

    function testSearch(btn) {
        var original = btn.textContent;
        btn.disabled = true;
        btn.textContent = '확인 중…';
        var statusEl = btn.closest('.provider-card').querySelector('[data-role="exa-status"]');
        api('/api/search/test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({})
        }).then(function (data) {
            toast(data.ok ? '검색 정상 — ' + data.count + '건' : '검색 실패: ' + (data.error || '오류'));
            renderExaStatus(statusEl, data);
        }).catch(function () {
            toast('검색 요청 실패');
            renderExaStatus(statusEl, { ok: false, error: '검색 요청 실패' });
        }).then(function () {
            btn.disabled = false;
            btn.textContent = original;
        });
    }

    function renderExaStatus(el, data) {
        if (!el) return;
        el.className = 'exa-status ' + (data.ok ? 'ok' : 'fail');
        el.textContent = data.ok
            ? '✓ 검색 정상 · ' + data.count + '건' + (data.sample ? ' — ' + data.sample.title : '')
            : '⚠ 검색 비정상 — ' + (data.error || '오류');
    }

    function refreshProviderModels(name, btn) {
        var original = btn.textContent;
        btn.disabled = true;
        btn.textContent = '동기화 중…';
        api('/api/providers/' + encodeURIComponent(name) + '/models/refresh', { method: 'POST' }).then(function (data) {
            if (data.ok) {
                var label = providerDisplay[name] || name;
                if (data.status === 'SKIPPED') {
                    toast(label + ' 동기화 스킵 — ' + (data.errorMessage || 'API 키 미설정'));
                } else {
                    toast(label + ' 동기화 완료 — ' + (data.count || 0) + '개 (신규 ' + (data.added || 0) + '개)');
                }
                loadProviders();
                loadAllSilent(state.selPrompt);
                populateModelSelect($('pfProvider').value);
            } else {
                toast('갱신 실패: ' + (data.error || data.errorMessage || '오류'));
            }
        }).catch(function () { toast('갱신 요청 실패'); }).then(function () {
            btn.disabled = false;
            btn.textContent = original;
        });
    }

    function askApiKey(name) {
        var key = prompt((providerDisplay[name] || name) + ' API 키를 입력하세요 (빈 값 = 취소)');
        if (key === null || key.trim() === '') return;
        api('/api/providers/' + encodeURIComponent(name) + '/key', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ apiKey: key.trim() })
        }).then(function (data) {
            toast(data.ok ? 'API 키가 저장되었습니다' : '저장 실패: ' + (data.error || '오류'));
            if (data.ok) loadProviders();
        }).catch(function () { toast('저장 요청 실패'); });
    }

    function toggleModelEnabled(name, model, enabled) {
        api('/api/providers/' + encodeURIComponent(name) + '/models/' + encodeURIComponent(model) + '/enabled', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ enabled: enabled })
        }).then(function (data) {
            if (!data.ok) { toast('변경 실패: ' + (data.error || '오류')); loadProviders(); }
        }).catch(function () { toast('변경 요청 실패'); loadProviders(); });
    }

    function setAllModelsEnabled(name, enabled, btn) {
        var original = btn ? btn.textContent : '';
        if (btn) { btn.disabled = true; btn.textContent = enabled ? '모두 투입 중…' : '모두 해제 중…'; }
        api('/api/providers/' + encodeURIComponent(name) + '/models/enabled', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ enabled: enabled })
        }).then(function (data) {
            if (!data.ok) throw new Error(data.error || '오류');
            return loadProviders();
        }).catch(function () {
            return api('/api/providers').then(function (providers) {
                var p = providers.find(function (x) { return x.name === name; });
                var models = (p && p.models) || [];
                var chain = Promise.resolve();
                models.forEach(function (m) {
                    chain = chain.then(function () {
                        return fetch(API_BASE + '/api/providers/' + encodeURIComponent(name) + '/models/' + encodeURIComponent(m.id) + '/enabled', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ enabled: enabled })
                        });
                    });
                });
                return chain.then(loadProviders);
            }).catch(function () { toast('일괄 변경에 실패했습니다'); });
        }).then(function () {
            populateModelSelect($('pfProvider').value);
            if (btn) { btn.disabled = false; btn.textContent = original; }
        });
    }

    init();
})();
