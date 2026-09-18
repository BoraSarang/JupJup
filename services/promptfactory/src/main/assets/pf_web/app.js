// 프롬프트팩토리 v3 — 리포트 중심 마스터-디테일 (모달 없음, 서랍 설정)
(function () {
    'use strict';

    var API_BASE = '';
    var providerDisplay = {
        'OPENROUTER': 'OpenRouter',
        'NIM': 'NVIDIA NIM',
        'GOOGLE_AI_STUDIO': 'Google AI Studio'
    };

    var state = { prompts: [], selPrompt: null, execs: [], selExec: null, showDetailMobile: false };

    function $(id) { return document.getElementById(id); }

    /* ---------- 마크다운 (mac_web T-142 그대로 + GFM 표 패치) ---------- */
    function mdInline(s) {
        // 셀·문단 안 줄바꿈 플레이스홀더 → <br>, 선행 "- "는 불릿으로
        s = s.split('\uE000').map(function (seg) {
            return seg.replace(/^\s*-\s+/, '• ');
        }).join('<br>');
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
                html += '<div class="md-table-wrap"><table><thead><tr>' +
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
            html += '<p>' + mdInline(line) + '</p>';
        }
        flushList();
        if (inCode && codeBuf.length) html += '<pre><code>' + codeBuf.join('\n') + '</code></pre>';
        return html;
    }
    function stripMd(src) {
        return stripHtml(String(src == null ? '' : src).replace(/<br\s*\/?>/gi, ' '))
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
    function api(path, opts) {
        return fetch(API_BASE + path, opts).then(function (r) {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
        });
    }

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
        loadAll();
    }

    function loadAll() {
        loadHealth();
        api('/api/prompts').then(function (prompts) {
            state.prompts = prompts || [];
            renderChips();
            if (state.prompts.length) {
                selectPrompt(state.prompts[0].id);
            } else {
                $('dateList').innerHTML = '';
                $('reportBody').innerHTML = '<p class="empty-state">등록된 프롬프트가 없습니다.<br>⚙️ 설정에서 새 프롬프트를 만들어보세요.</p>';
                $('lastRun').textContent = '프롬프트 없음';
            }
        }).catch(function () {
            $('reportBody').innerHTML = '<p class="empty-state">프롬프트를 불러올 수 없습니다</p>';
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
    function selectPrompt(id) {
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
                selectExec(state.execs[0].id);
                var last = state.execs[0];
                $('lastRun').textContent = '마지막 실행 ' + formatDateShort(last.executedAt) + ' · ' + formatDuration(last.durationMs);
            } else {
                $('lastRun').textContent = '아직 실행 없음';
                renderEmptyReport();
            }
        }).catch(function () {
            $('dateList').innerHTML = '<p class="empty-state">기록을 불러올 수 없습니다</p>';
        });
    }

    function renderDates() {
        var box = $('dateList');
        box.className = 'date-list' + (state.showDetailMobile ? ' detail-open' : '');
        if (!state.execs.length) {
            box.innerHTML = '<p class="empty-state">아직 실행 결과가 없습니다.</p>';
            return;
        }
        box.innerHTML = state.execs.map(function (ex) {
            var prev = stripMd(ex.response || ex.errorMessage || '').slice(0, 60);
            return '<button class="date-item' + (state.selExec === ex.id ? ' active' : '') + '" data-id="' + ex.id + '" type="button">' +
                '<span class="date-main"><span class="dot ' + (ex.status === 'SUCCESS' ? 'success' : 'failed') + '"></span>' +
                esc(formatDateShort(ex.executedAt)) + '<span>' + esc(formatDuration(ex.durationMs)) + '</span></span>' +
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
            '<h2 class="report-title">' + esc(p ? p.title : '') + '</h2>' +
            '<div class="report-meta">아직 실행 결과가 없습니다.</div>' +
            '<div class="report-actions"><button class="btn btn-primary" id="rbRun" type="button">지금 실행</button></div>' +
            promptEditHtml(p);
        bindReportActions();
    }

    function promptEditHtml(p) {
        if (!p) return '';
        return '<button class="prompt-toggle" id="rbPromptToggle" type="button">프롬프트 보기 · 수정 ▾</button>' +
            '<div class="prompt-edit-box" id="rbPromptBox" hidden>' +
            '<textarea id="rbPromptText" rows="6">' + esc(p.content || '') + '</textarea>' +
            '<div class="report-actions" style="margin-top:8px">' +
            '<button class="btn btn-primary" id="rbPromptSave" type="button">프롬프트 저장</button>' +
            '<button class="btn" id="rbPromptEdit" type="button">설정에서 열기</button>' +
            '</div></div>';
    }

    function renderReport() {
        var p = curPrompt();
        var ex = curExec();
        if (!ex) { renderEmptyReport(); return; }
        var body = ex.status === 'SUCCESS' ? md(ex.response || '') : ('<p class="error-text">' + esc(ex.errorMessage || '실행 실패') + '</p>');
        $('reportBody').className = 'report-body';
        $('reportBody').innerHTML =
            '<button class="btn back-btn" id="rbBack" type="button">← 목록</button>' +
            '<h2 class="report-title">' + esc(p ? p.title : '') + '</h2>' +
            '<div class="report-meta">' + esc(formatTime(ex.executedAt)) + ' · ' + esc(formatDuration(ex.durationMs)) + ' · ' + esc(ex.status) +
            ' · ' + esc((providerDisplay[ex.provider] || ex.provider) + ' ' + (ex.modelId || '')) + '</div>' +
            '<div class="report-actions">' +
            '<button class="btn btn-primary" id="rbRun" type="button">지금 실행</button>' +
            '<button class="btn" id="rbCopy" type="button">응답 복사</button>' +
            '<button class="btn btn-danger" id="rbDelete" type="button">기록 삭제</button>' +
            '</div>' +
            promptEditHtml(p) +
            '<div class="md-body" id="rbBody">' + body + '</div>';
        bindReportActions();
    }

    function bindReportActions() {
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
            tog.textContent = box.hidden ? '프롬프트 보기 · 수정 ▾' : '프롬프트 닫기 ▴';
        });
        var save = $('rbPromptSave');
        if (save) save.addEventListener('click', function () {
            var p = curPrompt();
            var content = $('rbPromptText').value.trim();
            if (!content) { toast('프롬프트 내용을 입력하세요'); return; }
            api('/api/prompts/' + p.id, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: content })
            }).then(function (d) {
                if (!d.ok) { toast('저장 실패: ' + (d.error || '오류')); return; }
                p.content = content;
                toast('프롬프트가 저장되었습니다');
            }).catch(function () { toast('저장에 실패했습니다'); });
        });
        var edit = $('rbPromptEdit');
        if (edit) edit.addEventListener('click', function () {
            var p = curPrompt();
            if (!p) return;
            api('/api/prompts/' + p.id).then(function (full) {
                openDrawer();
                fillPromptForm(full);
            }).catch(function () { toast('프롬프트를 불러올 수 없습니다'); });
        });
    }

    function executeSelected() {
        var p = curPrompt();
        if (!p) return;
        if (!confirm('「' + p.title + '」를 지금 실행할까요?')) return;
        api('/api/prompts/' + p.id + '/execute', { method: 'POST' }).then(function (d) {
            toast(d.ok ? '실행이 예약되었습니다' : '실행 실패: ' + (d.error || '오류'));
            if (d.ok) setTimeout(function () { selectPrompt(p.id); }, 2000);
        }).catch(function () { toast('실행 예약에 실패했습니다'); });
    }

    function deleteSelected() {
        var ex = curExec();
        if (!ex) return;
        if (!confirm('이 실행 기록을 삭제할까요?')) return;
        fetch(API_BASE + '/api/executions/' + ex.id, { method: 'DELETE' }).then(function () {
            toast('삭제되었습니다');
            selectPrompt(state.selPrompt);
        }).catch(function () { toast('삭제에 실패했습니다'); });
    }

    function copyText(text) {
        function done() { toast('응답이 복사되었습니다'); }
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
        $('drawer').hidden = true;
        $('drawerBackdrop').hidden = true;
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
            selectPrompt(selectId || (state.prompts[0] && state.prompts[0].id));
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
            sel.innerHTML = list.length === 0
                ? '<option value="">모델 없음 (모델 갱신 필요)</option>'
                : list.map(function (m) {
                    return '<option value="' + escapeAttr(m.id) + '"' + (m.id === selectedId ? ' selected' : '') + '>' +
                        esc(m.id) + (m.contextWindow ? ' (' + m.contextWindow + ')' : '') + '</option>';
                }).join('');
        }).catch(function () {
            sel.innerHTML = '<option value="">모델 로드 실패</option>';
        });
    }

    /* ---------- 공급자 ---------- */
    function loadProviders() {
        api('/api/providers').then(renderProviders).catch(function () {
            $('providerList').innerHTML = '<p class="empty-state">공급자 정보를 불러올 수 없습니다</p>';
        });
    }
    function renderProviders(providers) {
        if (!providers) return;
        $('providerList').innerHTML = providers.map(function (p) {
            return '<div class="provider-card" data-name="' + p.name + '">' +
                '<div class="provider-card-header"><span class="provider-card-title">' + esc(p.displayName) + '</span>' +
                '<div class="provider-card-actions">' +
                '<button class="btn btn-primary" data-action="refresh" type="button">모델 갱신</button>' +
                '<button class="btn" data-action="all-on" type="button">모두 사용</button>' +
                '<button class="btn" data-action="all-off" type="button">모두 해제</button>' +
                '</div></div>' +
                '<div class="provider-key-status' + (p.hasApiKey ? ' has' : '') + '">' +
                (p.hasApiKey ? '✓ API 키 등록됨' : 'API 키 미등록 — 아래에서 입력') +
                ' <button class="btn" data-action="key" type="button">' + (p.hasApiKey ? '키 교체' : '키 등록') + '</button></div>' +
                '<div class="provider-search-row"><input type="search" class="provider-search" placeholder="모델 검색 (이름·ID)" autocomplete="off">' +
                '<span class="provider-count">' + p.models.length + '개</span></div>' +
                '<div class="provider-models">' +
                p.models.map(function (m) {
                    return '<div class="provider-model" data-search="' + escapeAttr(((m.id + ' ' + (m.name || '')).toLowerCase())) + '">' +
                        '<div class="provider-model-info"><span class="model-id">' + esc(m.id) + '</span>' +
                        (m.contextWindow ? '<div class="model-ctx">ctx ' + m.contextWindow.toLocaleString('ko-KR') + '</div>' : '') + '</div>' +
                        '<label class="checkbox-label provider-model-toggle"><input type="checkbox"' + (m.enabled ? ' checked' : '') +
                        ' data-model="' + escapeAttr(m.id) + '"><span>사용</span></label></div>';
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

    function refreshProviderModels(name, btn) {
        var original = btn.textContent;
        btn.disabled = true;
        btn.textContent = '갱신 중…';
        api('/api/providers/' + encodeURIComponent(name) + '/models/refresh', { method: 'POST' }).then(function (data) {
            if (data.ok) {
                var label = providerDisplay[name] || name;
                if (data.status === 'SKIPPED') {
                    toast(label + ' 갱신 스킵 — ' + (data.errorMessage || 'API 키 미설정'));
                } else {
                    toast(label + ' 갱신 완료 — ' + (data.count || 0) + '개 (신규 ' + (data.added || 0) + '개)');
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
        if (btn) { btn.disabled = true; btn.textContent = enabled ? '모두 사용 중…' : '모두 해제 중…'; }
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
