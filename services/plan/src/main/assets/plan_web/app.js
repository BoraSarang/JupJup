/* 알뜰요금줍줍 포털 — 바닐라 JS. 전체 로드(최대 500건) 후 클라이언트 필터/정렬 */
(function () {
    'use strict';

    var state = {
        allPlans: [],
        totalCount: 0,
        filteredPlans: [],
        currentPage: 1,
        pageSize: 20,
        sort: 'price_asc',
        tag: null,
        networks: [],
        carriers: [],
        minData: 0,
        maxPrice: 100000,
        notifType: '',
        notifPage: 1,
        notifPageSize: 20,
        notifList: [],
        notifTotal: 0,
        statsOpen: false,
        statsLoaded: false,
        crawlGran: 'daily',
        rankFilter: 'all',
        tab: 'list',
        keyword: '',
        brand: ''
    };

    var CURATION_TAGS = ['가성비청년', '해비유저', '효도폰', '영상시청', '신규출시'];

    var NOTIF_TYPE_LABEL = {
        'CRAWL_COMPLETE': '수집 완료',
        'CRAWL_FAILED': '수집 실패',
        'NEW_PLANS_FOUND': '신규 요금제',
        'NEW_PLAN_DETAIL': '신규 요금제',
        'CRAWL_SUMMARY': '요약',
        'CRAWL_FAILED_STREAK': '수집 실패'
    };

    function el(id) { return document.getElementById(id); }

    function esc(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function parseDataGb(text) {
        if (!text) return 0;
        var t = String(text).replace(/\s/g, '').toUpperCase();
        if (t.indexOf('무제한') === 0 && t.indexOf('GB') < 0) return 999999;
        var m = t.match(/(\d+(?:\.\d+)?)\s*GB/);
        return m ? parseInt(m[1], 10) : 0;
    }

    function formatPrice(n) {
        return Number(n || 0).toLocaleString('ko-KR') + '원';
    }

    var CHOSEONG = ['ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'];
    var LATIN_TO_JAMO = { b: 'ㅂ', c: 'ㅊ', d: 'ㄷ', f: 'ㅍ', g: 'ㄱ', h: 'ㅎ', j: 'ㅈ', k: 'ㅋ', l: 'ㄹ', m: 'ㅁ', n: 'ㄴ', p: 'ㅍ', q: 'ㅋ', r: 'ㄹ', s: 'ㅅ', t: 'ㅌ', v: 'ㅂ', w: 'ㅇ', x: 'ㅅ', y: 'ㅇ', z: 'ㅈ' };

    function chosKey(str) {
        var s = String(str == null ? '' : str).replace(/\s+/g, '').toLowerCase();
        var out = '';
        for (var i = 0; i < s.length; i++) {
            var ch = s.charAt(i);
            var code = s.charCodeAt(i);
            if (code >= 0xAC00 && code <= 0xD7A3) {
                out += CHOSEONG[Math.floor((code - 0xAC00) / 588)];
            } else if (/[a-z]/.test(ch)) {
                out += LATIN_TO_JAMO[ch] || ch;
            } else {
                out += ch;
            }
        }
        return out;
    }

    function planTextOf(p) {
        return String(p.planName || '').toLowerCase().replace(/\s+/g, '') + ' ' +
            String(p.carrierName || '').toLowerCase().replace(/\s+/g, '') + ' ' +
            String(p.dataAmount || '').toLowerCase().replace(/\s+/g, '') + ' ' +
            String(p.voice || '').toLowerCase().replace(/\s+/g, '') + ' ' +
            String(p.sms || '').toLowerCase().replace(/\s+/g, '') + ' ' +
            String(p.eventBadge || '').toLowerCase().replace(/\s+/g, '');
    }

    function parseVoiceMin(text) {
        if (!text) return 0;
        var t = String(text).replace(/\s/g, '');
        if (t.indexOf('무제한') >= 0) return 999999;
        var m = t.match(/(\d+(?:\.\d+)?)분/);
        return m ? parseFloat(m[1]) : 0;
    }

    function parseSmsCount(text) {
        if (!text) return 0;
        var t = String(text).replace(/\s/g, '');
        if (t.indexOf('무제한') >= 0) return 999999;
        var m = t.match(/(\d+(?:\.\d+)?)건/);
        return m ? parseFloat(m[1]) : 0;
    }

    function parseNLParts(kw) {
        var q = kw.toLowerCase();
        var part = { tokens: [], hasConstraints: false, priceMin: null, priceMax: null, dataMin: null, dataMax: null, voiceMin: null, voiceMax: null, smsMin: null, smsMax: null };
        var dirMin = /(이상|초과|부터|이내)/.test(q);
        var dirMax = /(이하|미만|까지|아래)/.test(q);
        var STOPS = ['요금제', '데이터', '통화', '문자', '무제한', '이상', '이하', '미만', '초과', '까지', '이내', '부터', '요금', '월', '가입', '번호이동'];
        var tokens = q.split(/\s+/).filter(Boolean);
        var m, mult, v;
        for (var i = 0; i < tokens.length; i++) {
            var t = tokens[i];
            if (/무제한/.test(t)) continue;
            if ((m = t.match(/^(\d+(?:\.\d+)?)\s*(천|만|억)?원$/))) {
                mult = { '천': 1000, '만': 10000, '억': 100000000 }[m[2]] || 1;
                v = parseFloat(m[1]) * mult;
                if (dirMin) part.priceMin = part.priceMin == null ? v : Math.min(part.priceMin, v);
                else part.priceMax = part.priceMax == null ? v : Math.max(part.priceMax, v);
                part.hasConstraints = true;
            } else if ((m = t.match(/^(\d+(?:\.\d+)?)\s*(기가|g|gb)$/))) {
                v = parseFloat(m[1]);
                if (dirMax) part.dataMax = part.dataMax == null ? v : Math.min(part.dataMax, v);
                else part.dataMin = part.dataMin == null ? v : Math.max(part.dataMin, v);
                part.hasConstraints = true;
            } else if ((m = t.match(/^(\d+(?:\.\d+)?)\s*mb$/))) {
                v = parseFloat(m[1]) / 1024;
                if (dirMax) part.dataMax = part.dataMax == null ? v : Math.min(part.dataMax, v);
                else part.dataMin = part.dataMin == null ? v : Math.max(part.dataMin, v);
                part.hasConstraints = true;
            } else if ((m = t.match(/^(\d+(?:\.\d+)?)\s*분$/))) {
                v = parseFloat(m[1]);
                if (dirMax) part.voiceMax = part.voiceMax == null ? v : Math.min(part.voiceMax, v);
                else part.voiceMin = part.voiceMin == null ? v : Math.max(part.voiceMin, v);
                part.hasConstraints = true;
            } else if ((m = t.match(/^(\d+(?:\.\d+)?)\s*건$/))) {
                v = parseFloat(m[1]);
                if (dirMax) part.smsMax = part.smsMax == null ? v : Math.min(part.smsMax, v);
                else part.smsMin = part.smsMin == null ? v : Math.max(part.smsMin, v);
                part.hasConstraints = true;
            } else if (STOPS.indexOf(t) < 0 && !/^\d+(?:\.\d+)?$/.test(t)) {
                part.tokens.push(t);
            }
        }
        return part;
    }

    function buildSearchMatcher(kw) {
        if (/[ㄱ-ㅎ]/.test(kw)) {
            var qk = chosKey(kw.replace(/\s+/g, ''));
            return function (p) { return chosKey(planTextOf(p)).indexOf(qk) >= 0; };
        }
        var nl = parseNLParts(kw);
        if (nl.hasConstraints || nl.tokens.length) {
            return function (p) {
                var text = planTextOf(p);
                for (var i = 0; i < nl.tokens.length; i++) {
                    if (text.indexOf(nl.tokens[i]) < 0) return false;
                }
                var price = Number(p.price) || 0;
                if (nl.priceMin != null && price < nl.priceMin) return false;
                if (nl.priceMax != null && price > nl.priceMax) return false;
                if (nl.dataMin != null && parseDataGb(p.dataAmount) < nl.dataMin) return false;
                if (nl.dataMax != null && parseDataGb(p.dataAmount) > nl.dataMax) return false;
                if (nl.voiceMin != null && parseVoiceMin(p.voice) < nl.voiceMin) return false;
                if (nl.voiceMax != null && parseVoiceMin(p.voice) > nl.voiceMax) return false;
                if (nl.smsMin != null && parseSmsCount(p.sms) < nl.smsMin) return false;
                if (nl.smsMax != null && parseSmsCount(p.sms) > nl.smsMax) return false;
                return true;
            };
        }
        var k = kw.toLowerCase().replace(/\s+/g, '');
        return function (p) { return planTextOf(p).indexOf(k) >= 0; };
    }

    function formatDate(ts) {
        if (!ts) return '-';
        try {
            return new Date(ts).toLocaleDateString('ko-KR');
        } catch (e) {
            return '-';
        }
    }

    function loadData() {
        var statsPromise = fetch('/api/stats').then(function (res) { return res.json(); });
        var plansPromise = fetch('/api/plans?pageSize=1000').then(function (res) { return res.json(); });
        return Promise.all([statsPromise, plansPromise])
            .then(function (results) {
                var stats = results[0] || {};
                var data = results[1] || {};
                state.totalCount = stats.totalPlans || 0;
                state.allPlans = data.plans || [];
                buildBrandOptions();
                updateLastUpdated();
                applyFilters();
            })
            .catch(function (err) {
                console.error('load failed', err);
                el('planGrid').innerHTML = '<div class="error-state">데이터를 불러오는데 실패했습니다. 서버가 실행 중인지 확인하세요.</div>';
            });
    }

    function buildBrandOptions() {
        var brands = [];
        state.allPlans.forEach(function (p) {
            if (p.carrierName && brands.indexOf(p.carrierName) < 0) brands.push(p.carrierName);
        });
        brands.sort(function (a, b) { return a.localeCompare(b, 'ko'); });
        var sel = el('brandFilter');
        sel.innerHTML = '<option value="">전체 브랜드</option>' +
            brands.map(function (b) { return '<option value="' + esc(b) + '">' + esc(b) + '</option>'; }).join('');
    }

    function relativeTime(ts) {
        var diff = Date.now() - ts;
        if (diff < 60000) return '방금 전';
        var m = Math.floor(diff / 60000);
        if (m < 60) return m + '분 전';
        var h = Math.floor(m / 60);
        if (h < 24) return h + '시간 전';
        var d = Math.floor(h / 24);
        if (d < 30) return d + '일 전';
        var mo = Math.floor(d / 30);
        if (mo < 12) return mo + '개월 전';
        return Math.floor(mo / 12) + '년 전';
    }

    function updateLastUpdated() {
        var latest = 0;
        state.allPlans.forEach(function (p) {
            if (p.collectedAt && p.collectedAt > latest) latest = p.collectedAt;
        });
        var node = el('lastUpdated');
        if (latest) {
            node.textContent = '마지막 업데이트: ' + relativeTime(latest);
            node.title = new Date(latest).toLocaleString('ko-KR');
        } else {
            node.textContent = '마지막 업데이트: -';
            node.title = '';
        }
    }

    function applyFilters() {
        var plans = state.allPlans.slice();
        if (state.brand) {
            plans = plans.filter(function (p) { return p.carrierName === state.brand; });
        }
        if (state.keyword) {
            var matcher = buildSearchMatcher(state.keyword);
            plans = plans.filter(matcher);
        }
        if (state.networks.length) {
            plans = plans.filter(function (p) { return state.networks.indexOf(p.networkType) >= 0; });
        }
        if (state.carriers.length) {
            plans = plans.filter(function (p) { return state.carriers.indexOf(p.mvnoNetwork) >= 0; });
        }
        if (state.minData > 0) {
            plans = plans.filter(function (p) { return parseDataGb(p.dataAmount) >= state.minData; });
        }
        if (state.maxPrice < 100000) {
            plans = plans.filter(function (p) { return p.price <= state.maxPrice; });
        }
        if (state.tag) {
            if (state.tag === '신규출시') {
                plans = plans.filter(function (p) { return p.isNew; });
            } else {
                plans = plans.filter(function (p) { return (p.tags || '').indexOf(state.tag) >= 0; });
            }
        }
        plans.sort(function (a, b) {
            switch (state.sort) {
                case 'price_desc': return b.price - a.price;
                case 'data_desc': return parseDataGb(b.dataAmount) - parseDataGb(a.dataAmount);
                case 'newest': return (b.collectedAt || 0) - (a.collectedAt || 0);
                default: return a.price - b.price;
            }
        });
        state.filteredPlans = plans;
        state.currentPage = 1;
        render();
    }

    function renderCard(p) {
        var sources = (p.sources || []).map(function (s) {
            return '<a class="source-badge" href="' + esc(s.sourceUrl) + '" target="_blank" rel="noopener noreferrer">' + esc(s.sourceName) + '</a>';
        }).join('');
        return '<article class="plan-card" role="listitem">' +
            '<div class="card-header">' +
            '<span class="carrier-badge">' + esc(p.carrierName) + '</span>' +
            '<span class="network-badge">' + esc(p.mvnoNetwork) + ' ' + esc(p.networkType) + '</span>' +
            (p.isNew ? '<span class="new-badge">NEW</span>' : '') +
            '</div>' +
            '<h2 class="plan-name">' + esc(p.planName) + '</h2>' +
            '<div class="plan-price"><span class="price">' + esc(formatPrice(p.price)) + '</span>' +
            (p.priceAfterDiscount ? '<span class="price-after">정가 ' + esc(formatPrice(p.priceAfterDiscount)) + '</span>' : '') +
            '</div>' +
            '<ul class="plan-specs">' +
            '<li>📊 ' + esc(p.dataAmount) + '</li>' +
            '<li>📞 ' + esc(p.voice) + '</li>' +
            '<li>💬 ' + esc(p.sms) + '</li>' +
            '</ul>' +
            '<div class="event-badge">' + esc(p.eventBadge || '') + '</div>' +
            '<div class="collected-line">첫 수집 ' + esc(formatDate(p.firstCollectedAt)) + ' · 최근 ' + esc(formatDate(p.collectedAt)) + '</div>' +
            '<div class="source-badges">' + sources + '</div>' +
            '</article>';
    }

    function render() {
        var grid = el('planGrid');
        var start = (state.currentPage - 1) * state.pageSize;
        var pagePlans = state.filteredPlans.slice(start, start + state.pageSize);
        grid.innerHTML = pagePlans.length
            ? pagePlans.map(renderCard).join('')
            : '<div class="empty-state">조건에 맞는 요금제가 없습니다. 필터를 조정해 보세요.</div>';
        renderPagination();
        el('visibleCount').textContent = '검색 결과 ' + state.filteredPlans.length + '개';
        el('totalCount').textContent = '전체 ' + (state.totalCount || state.allPlans.length) + '개';
    }

    function renderPagination() {
        var totalPages = Math.ceil(state.filteredPlans.length / state.pageSize);
        var nav = el('pagination');
        if (totalPages <= 1) { nav.innerHTML = ''; return; }
        var html = '';
        var cur = state.currentPage;
        if (cur > 1) html += '<button class="page-btn" data-page="' + (cur - 1) + '">‹</button>';
        for (var i = 1; i <= totalPages; i++) {
            if (i === 1 || i === totalPages || (i >= cur - 1 && i <= cur + 1)) {
                html += '<button class="page-btn' + (i === cur ? ' active' : '') + '" data-page="' + i + '">' + i + '</button>';
            } else if (i === cur - 2 || i === cur + 2) {
                html += '<span class="page-btn">…</span>';
            }
        }
        if (cur < totalPages) html += '<button class="page-btn" data-page="' + (cur + 1) + '">›</button>';
        nav.innerHTML = html;
        var btns = nav.querySelectorAll('.page-btn[data-page]');
        for (var k = 0; k < btns.length; k++) {
            btns[k].addEventListener('click', function () {
                state.currentPage = parseInt(this.getAttribute('data-page'), 10);
                render();
                window.scrollTo(0, 0);
            });
        }
    }

    function renderCurationTags() {
        var nav = el('curationTags');
        nav.innerHTML = CURATION_TAGS.map(function (t) {
            return '<button class="curation-tag" data-tag="' + t + '" type="button">' + t + '</button>';
        }).join('');
        var btns = nav.querySelectorAll('.curation-tag');
        for (var i = 0; i < btns.length; i++) {
            btns[i].addEventListener('click', function () {
                var t = this.getAttribute('data-tag');
                state.tag = (state.tag === t) ? null : t;
                for (var j = 0; j < btns.length; j++) {
                    btns[j].classList.toggle('active', btns[j].getAttribute('data-tag') === state.tag);
                }
                setTab('list');
                applyFilters();
            });
        }
    }

    function checkedValues(containerId) {
        var out = [];
        var boxes = el(containerId).querySelectorAll('input[type="checkbox"]:checked');
        for (var i = 0; i < boxes.length; i++) out.push(boxes[i].value);
        return out;
    }

    // ---------- 알림 센터 ----------

    function formatDateTime(ts) {
        if (!ts) return '-';
        try {
            return new Date(ts).toLocaleString('ko-KR');
        } catch (e) {
            return '-';
        }
    }

    function loadUnreadCount() {
        fetch('/api/notifications/unread-count').then(function (res) { return res.json(); })
            .then(function (data) {
                var n = (data && data.unreadCount) || 0;
                var badge = el('notifBadge');
                badge.textContent = n;
                badge.hidden = n === 0;
            })
            .catch(function () {});
    }

    function openNotifPanel() {
        el('notifPanel').removeAttribute('hidden');
        el('notifBackdrop').removeAttribute('hidden');
        loadNotifications();
    }

    function closeNotifPanel() {
        el('notifPanel').setAttribute('hidden', '');
        el('notifBackdrop').setAttribute('hidden', '');
    }

    function setNotifTab(type) {
        state.notifType = type;
        state.notifPage = 1;
        var tabs = document.querySelectorAll('.notif-tab');
        for (var i = 0; i < tabs.length; i++) {
            tabs[i].classList.toggle('active', tabs[i].getAttribute('data-type') === type);
        }
        loadNotifications();
    }

    function loadNotifications() {
        var params = 'page=' + state.notifPage + '&pageSize=' + state.notifPageSize;
        if (state.notifType) params += '&type=' + encodeURIComponent(state.notifType);
        fetch('/api/notifications?' + params).then(function (res) { return res.json(); })
            .then(function (data) {
                data = data || {};
                state.notifList = data.notifications || [];
                state.notifTotal = data.total || 0;
                renderNotifList();
                renderNotifPaging();
                loadUnreadCount();
            })
            .catch(function () {
                el('notifList').innerHTML = '<div class="notif-empty">알림을 불러오는데 실패했습니다.</div>';
            });
    }

    function renderNotifList() {
        var listEl = el('notifList');
        if (!state.notifList.length) {
            listEl.innerHTML = '<div class="notif-empty">알림이 없습니다.</div>';
            return;
        }
        listEl.innerHTML = state.notifList.map(function (n) {
            var label = NOTIF_TYPE_LABEL[n.type] || n.type || '';
            return '<div class="notif-item' + (n.isRead ? ' read' : ' unread') + '" data-id="' + n.id + '">' +
                '<span class="notif-item-dot"></span>' +
                '<div class="notif-item-main">' +
                '<div class="notif-item-title"><span class="notif-item-type">' + esc(label) + '</span>' + esc(n.summary) + '</div>' +
                '<div class="notif-item-time">' + esc(formatDateTime(n.createdAt)) + '</div>' +
                '</div>' +
                '<button class="notif-item-del" type="button" data-del="' + n.id + '" aria-label="삭제">🗑</button>' +
                '</div>';
        }).join('');
        var items = listEl.querySelectorAll('.notif-item');
        for (var i = 0; i < items.length; i++) {
            items[i].addEventListener('click', function () {
                var id = this.getAttribute('data-id');
                showNotifDetail(id);
                markRead(id);
            });
        }
        var dels = listEl.querySelectorAll('.notif-item-del');
        for (var j = 0; j < dels.length; j++) {
            dels[j].addEventListener('click', function (e) {
                e.stopPropagation();
                deleteNotif(this.getAttribute('data-del'));
            });
        }
    }

    function renderNotifPaging() {
        var nav = el('notifPaging');
        var totalPages = Math.max(1, Math.ceil(state.notifTotal / state.notifPageSize));
        var html = '';
        if (state.notifPage > 1) {
            html += '<button class="page-btn" data-np="' + (state.notifPage - 1) + '">‹</button>';
        }
        html += '<span class="page-btn notif-np-label">' + state.notifPage + ' / ' + totalPages + '</span>';
        if (state.notifPage < totalPages) {
            html += '<button class="page-btn" data-np="' + (state.notifPage + 1) + '">›</button>';
        }
        nav.innerHTML = html;
        var btns = nav.querySelectorAll('.page-btn[data-np]');
        for (var i = 0; i < btns.length; i++) {
            btns[i].addEventListener('click', function () {
                state.notifPage = parseInt(this.getAttribute('data-np'), 10);
                loadNotifications();
            });
        }
    }

    function markRead(id) {
        fetch('/api/notifications/' + id + '/read', { method: 'POST' })
            .then(function () { loadUnreadCount(); })
            .catch(function () {});
    }

    function deleteNotif(id) {
        fetch('/api/notifications/' + id, { method: 'DELETE' })
            .then(function () {
                loadNotifications();
                loadUnreadCount();
            })
            .catch(function () {});
    }

    function markAllRead() {
        fetch('/api/notifications/read-all', { method: 'POST' })
            .then(function () {
                loadNotifications();
                loadUnreadCount();
            })
            .catch(function () {});
    }

    function cleanupNotifs() {
        fetch('/api/notifications/cleanup', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: '{}'
        })
            .then(function () {
                loadNotifications();
                loadUnreadCount();
            })
            .catch(function () {});
    }

    function showNotifDetail(id) {
        fetch('/api/notifications/' + id).then(function (res) { return res.json(); })
            .then(function (data) {
                var modal = el('notifModal');
                el('notifModalTitle').textContent = '알림 상세';
                el('notifModalBody').innerHTML = renderDetailHTML(data);
                if (typeof modal.showModal === 'function') modal.showModal();
                else modal.setAttribute('open', '');
            })
            .catch(function () {
                var modal = el('notifModal');
                el('notifModalBody').innerHTML = '<div class="notif-empty">상세 정보를 불러오는데 실패했습니다.</div>';
                if (typeof modal.showModal === 'function') modal.showModal();
                else modal.setAttribute('open', '');
            });
    }

    function renderBarChart(sectionTitle, rows) {
        if (!rows || !rows.length) return '';
        var max = rows.reduce(function (m, r) { return Math.max(m, r.count || 0); }, 1);
        var html = '<div class="notif-section"><h3>' + esc(sectionTitle) + '</h3>';
        rows.forEach(function (r) {
            var label = r.brand || r.sourceName || r.network || '-';
            var pct = Math.round(((r.count || 0) / max) * 100);
            html += '<div class="bar-row"><span class="bar-label" title="' + esc(label) + '">' + esc(label) + '</span>' +
                '<span class="bar-track"><span class="bar-fill" style="width:' + pct + '%"></span></span>' +
                '<span class="bar-count">' + (r.count || 0) + '</span></div>';
        });
        html += '</div>';
        return html;
    }

    function renderDetailHTML(data) {
        var n = (data && data.notification) || {};
        var d = (data && data.detail) || {};
        var label = NOTIF_TYPE_LABEL[n.type] || n.type || '';
        var typeColors = { 'CRAWL_COMPLETE': '#0066FF', 'NEW_PLANS_FOUND': '#FF3B30', 'CRAWL_FAILED_STREAK': '#C62828' };
        var color = typeColors[n.type] || 'var(--primary)';
        var html = '<div class="notif-section"><h3>' + esc(label) + '</h3>' +
            '<div style="font-size:0.95rem;font-weight:600;margin-bottom:4px;border-left:4px solid ' + color + ';padding-left:8px;">' + esc(n.summary || '') + '</div>' +
            '<div class="notif-item-time">' + esc(formatDateTime(n.createdAt)) + '</div></div>';
        if (typeof d.totalFound === 'number') {
            var pctNew = Math.min(100, Math.round(((d.newPlans || 0) / Math.max(1, d.totalFound)) * 100));
            var pctUpd = Math.min(100, Math.round(((d.updatedPlans || 0) / Math.max(1, d.totalFound)) * 100));
            html += '<div class="notif-section"><h3>통계</h3>' +
                '<div class="bar-row"><span class="bar-label">전체 발견</span><span class="bar-track"><span class="bar-fill" style="width:100%"></span></span><span class="bar-count">' + (d.totalFound || 0) + '</span></div>' +
                '<div class="bar-row"><span class="bar-label">신규</span><span class="bar-track"><span class="bar-fill" style="width:' + pctNew + '%;background:#FF3B30"></span></span><span class="bar-count">' + (d.newPlans || 0) + '</span></div>' +
                '<div class="bar-row"><span class="bar-label">갱신</span><span class="bar-track"><span class="bar-fill" style="width:' + pctUpd + '%;background:#34A853"></span></span><span class="bar-count">' + (d.updatedPlans || 0) + '</span></div>' +
                (d.failedCount ? '<div class="bar-row"><span class="bar-label">실패</span><span class="bar-track"><span class="bar-fill" style="width:100%;background:#C62828"></span></span><span class="bar-count">' + d.failedCount + '</span></div>' : '') +
                '</div>';
        }
        html += renderBarChart('출처별 발견', d.bySource || []);
        html += renderBarChart('브랜드별', d.byBrand || []);
        html += renderBarChart('통신망별', d.byNetwork || []);
        var nps = d.newPlansDetail || [];
        if (nps.length) {
            html += '<div class="notif-section"><h3>신규 요금제</h3>';
            nps.forEach(function (p) {
                html += '<div class="new-plan-row"><span class="np-carrier">' + esc(p.carrierName || '') + '</span>' +
                    '<span class="np-name" title="' + esc(p.planName || '') + '">' + esc(p.planName || '') + '</span>' +
                    '<span>' + esc(p.dataAmount || '') + ' · ' + esc(formatPrice(p.price)) + '</span></div>';
            });
            html += '</div>';
        }
        var fails = d.failedSources || [];
        if (fails.length) {
            html += '<div class="notif-section"><h3>실패한 소스</h3>';
            fails.forEach(function (f) {
                html += '<div class="fail-row"><div class="fl-name">' + esc(f.sourceName || f.sourceId || '') + '</div><div class="fl-error">' + esc(f.error || '') + '</div></div>';
            });
            html += '</div>';
        }
        return html;
    }

    // ---------- 통계 대시보드 ----------

    function fmtNum(n) {
        return Number(n == null ? 0 : n).toLocaleString('ko-KR');
    }

    function fmtPct(r) {
        return Math.round((r == null ? 0 : r) * 100) + '%';
    }

    var NET_COLORS = { 'SKT': '#E53935', 'KT': '#8E24AA', 'LGU+': '#1E88E5' };
    var GRAY = '#80868B';
    var WARN = '#E65100';
    var SUCCESS = '#34A853';
    var PRIMARY = '#0066FF';

    function networkColor(name) {
        if (!name) return GRAY;
        var key = String(name).toUpperCase().replace(/\s+/g, '');
        if (key.indexOf('LGU') >= 0 || key.indexOf('LG') === 0) key = 'LGU+';
        else if (key.indexOf('KT') >= 0) key = 'KT';
        else if (key.indexOf('SKT') >= 0 || key.indexOf('SK') >= 0) key = 'SKT';
        return NET_COLORS[key] || GRAY;
    }

    function setTab(tab) {
        state.tab = tab;
        state.statsOpen = (tab === 'stats');
        var listSec = el('planGridSection');
        var statsSec = el('statsSection');
        var tabStats = el('tabStats');
        if (tab === 'stats') {
            listSec.setAttribute('hidden', '');
            statsSec.removeAttribute('hidden');
            tabStats.setAttribute('aria-selected', 'true');
            tabStats.classList.add('active');
            if (!state.statsLoaded) {
                state.statsLoaded = true;
                loadStats();
            }
        } else {
            listSec.removeAttribute('hidden');
            statsSec.setAttribute('hidden', '');
            tabStats.setAttribute('aria-selected', 'false');
            tabStats.classList.remove('active');
        }
        window.scrollTo({ top: 0, behavior: 'auto' });
    }

    function resetFilters() {
        state.networks = [];
        state.carriers = [];
        state.minData = 0;
        state.maxPrice = 100000;
        state.tag = null;
        state.keyword = '';
        state.brand = '';
        var boxes = document.querySelectorAll('#filterPanel input[type="checkbox"]');
        for (var i = 0; i < boxes.length; i++) boxes[i].checked = false;
        el('minData').value = '0';
        el('minDataValue').textContent = '0GB 이상';
        el('maxPrice').value = '100000';
        el('maxPriceValue').textContent = '100,000원 이하';
        el('keyword').value = '';
        el('brandFilter').value = '';
        var tags = el('curationTags').querySelectorAll('.curation-tag');
        for (var j = 0; j < tags.length; j++) tags[j].classList.remove('active');
        applyFilters();
    }

    function goHome() {
        resetFilters();
        setTab('list');
    }

    function goBrand(name) {
        state.brand = name || '';
        var sel = el('brandFilter');
        if (sel) sel.value = state.brand;
        loadStats();
        setTab('list');
        applyFilters();
    }

    function renderStatsNav() {
        var items = [
            ['sec-kpi', '요약'],
            ['sec-insights', '인사이트'],
            ['sec-brands', '브랜드'],
            ['sec-networks', '통신망'],
            ['sec-trends', '추이'],
            ['sec-dist', '분포'],
            ['sec-rank', '가성비'],
            ['sec-health', '건강도']
        ];
        var html = items.map(function (it) {
            var id = it[0], label = it[1];
            var exists = !!document.getElementById(id);
            return '<button class="stats-nav-btn" type="button" data-sec="' + id + '"' + (exists ? '' : ' hidden') + '>' + label + '</button>';
        }).join('');
        var nav = el('statsNav');
        nav.innerHTML = html;
        var btns = nav.querySelectorAll('.stats-nav-btn');
        for (var i = 0; i < btns.length; i++) {
            btns[i].addEventListener('click', function () {
                var target = document.getElementById(this.getAttribute('data-sec'));
                if (!target) return;
                var headerH = document.querySelector('.header').getBoundingClientRect().height;
                var top = target.getBoundingClientRect().top + window.scrollY - headerH - 8;
                window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' });
            });
        }
    }

    function loadStats() {
        var content = el('statsContent');
        content.innerHTML = '<div class="empty-state">통계 불러오는 중…</div>';
        var paths = {
            overview: '/api/stats/overview',
            brands: '/api/stats/brands',
            networks: '/api/stats/networks',
            priceDist: '/api/stats/distribution?type=price',
            dataDist: '/api/stats/distribution?type=data',
            crawlTrend: '/api/stats/trends?type=crawl&gran=' + state.crawlGran + '&days=' + (state.crawlGran === 'hourly' ? 2 : 30),
            newTrend: '/api/stats/trends?type=new&gran=' + state.crawlGran + '&days=' + (state.crawlGran === 'hourly' ? 2 : 30),
            valueRank: '/api/stats/value-ranking?network=' + (state.rankFilter === 'all' ? '' : state.rankFilter) + '&limit=10',
            health: '/api/stats/collection-health',
            insights: '/api/stats/insights'
        };
        var keys = Object.keys(paths);
        var fetches = keys.map(function (k) {
            return fetch(paths[k]).then(function (res) {
                if (!res.ok) throw new Error(k + ' status ' + res.status);
                return res.json();
            }).catch(function (err) {
                console.warn('stats ' + k + ' failed', err);
                return null;
            });
        });
        Promise.all(fetches).then(function (results) {
            var data = {};
            keys.forEach(function (k, i) { data[k] = results[i]; });
            renderStats(data);
        });
    }

    function renderStats(d) {
        var o = d.overview || {};
        var html = '';

        // KPI 카드
        html += '<div class="stats-kpi-grid" id="sec-kpi">';
        html += kpiCard('전체 요금제', fmtNum(o.totalPlans));
        html += kpiCard('브랜드', fmtNum(o.brandCount));
        html += kpiCard('통신망', fmtNum(o.networkCount));
        html += kpiCard('이번 주 신규', fmtNum(o.newThisWeek));
        html += kpiCard('평균 요금', o.avgPrice ? fmtNum(o.avgPrice) + '원' : '-');
        html += kpiCard('최저가', o.minPrice ? fmtNum(o.minPrice) + '원' : '-');
        html += kpiCard('무제한 비중', fmtPct(o.unlimitedRatio));
        html += kpiCard('5G 비중', fmtPct(o.g5Ratio));
        html += '</div>';

        // 수집 요약 (7일 신규·오늘 수집·24h 실패)
        var health = d.health || {};
        html += '<div class="stats-kpi-sub">' +
            '<span>오늘 수집 <b>' + fmtNum(o.crawlCountToday) + '</b>건</span>' +
            '<span>24h 실패 <b class="' + (o.crawlFail24h ? 'text-warn' : 'text-ok') + '">' + fmtNum(o.crawlFail24h) + '</b>건</span>' +
            '<span>24h 성공률 <b>' + fmtPct(health.success24h != null && (health.success24h + health.fail24h)
                ? health.success24h / (health.success24h + health.fail24h) : 1) + '</b></span>' +
            '</div>';

        // 인사이트
        var ins = (d.insights && d.insights.insights) || [];
        if (ins.length) {
            html += '<div class="stats-block" id="sec-insights"><h3>💡 자동 인사이트</h3><div class="insight-list">';
            ins.forEach(function (it) {
                var icon = it.type === 'warning' ? '⚠️' : (it.type === 'positive' ? '✅' : '💡');
                html += '<div class="insight-card insight-' + esc(it.type) + '"><span class="insight-icon">' + icon + '</span>' +
                    '<div><div class="insight-title">' + esc(it.title) + '</div>' +
                    '<div class="insight-text">' + esc(it.text) + '</div></div></div>';
            });
            html += '</div></div>';
        }

        // 저장 커버리지: 브랜드별
        var brands = (d.brands && d.brands.brands) || [];
        if (brands.length) {
            html += '<div class="stats-block" id="sec-brands"><h3>🏬 저장 커버리지 (브랜드별 요금제 수)</h3>' +
                '<div class="stats-one-line">이 데이터로 알 수 있는 것: 어느 브랜드를 가장 많이 수집했는지 (막대를 누르면 해당 브랜드 요금제로 이동)</div>' +
                renderBars('brandBars', brands.map(function (b) {
                    return { label: b.brand, value: b.planCount, color: networkColor(b.mvnoNetwork) };
                })) + '</div>';
        }

        // 통신망별 → /planGrid 링크
        var nets = (d.networks && d.networks.networks) || [];
        if (nets.length) {
            html += '<div class="stats-block" id="sec-networks"><h3>📡 통신망 비교</h3>' +
                '<div class="stats-one-line">이 데이터로 알 수 있는 것: SKT/KT/LGU+ 별 평균가·단가·5G 비중 차이</div>' +
                '<div class="net-cards">';
            nets.forEach(function (n) {
                var inNet = n.network === 'LGU+' ? 'LGU+' : n.network;
                html += '<div class="net-card"><div class="net-card-head">' +
                    '<span class="net-dot" style="background:' + networkColor(n.network) + '"></span>' +
                    '<b>' + esc(n.network) + '망</b></div>' +
                    '<div class="net-row"><span>요금제</span><b>' + fmtNum(n.planCount) + '</b></div>' +
                    '<div class="net-row"><span>평균가</span><b>' + fmtNum(n.avgPrice) + '원</b></div>' +
                    '<div class="net-row"><span>최저가</span><b>' + fmtNum(n.minPrice) + '원</b></div>' +
                    '<div class="net-row"><span>GB당 단가</span><b>' + (n.pricePerGb != null ? '평균 ' + fmtNum(n.pricePerGb) + '원' : '-') + '</b></div>' +
                    '<div class="net-row"><span>5G 비중</span><b>' + fmtPct(n.g5Ratio) + '</b></div>' +
                    '<div class="net-row"><span>무제한 비중</span><b>' + fmtPct(n.unlimitedRatio) + '</b></div></div>';
            });
            html += '</div></div>';
        }

        // 수집 추이 + 신규 요금제 추이
        var crawl = (d.crawlTrend && d.crawlTrend.points) || [];
        var ntr = (d.newTrend && d.newTrend.points) || [];
        if (crawl.length || ntr.length) {
            html += '<div class="stats-block" id="sec-trends"><h3>📈 수집 · 신규 추이 <span class="gran-group">' +
                '<button class="gran-btn' + (state.crawlGran === 'daily' ? ' active' : '') + '" data-gran="daily">일별</button>' +
                '<button class="gran-btn' + (state.crawlGran === 'hourly' ? ' active' : '') + '" data-gran="hourly">시간별</button></span></h3>' +
                '<div class="stats-one-line">이 데이터로 알 수 있는 것: 수집 활동이 언제, 얼마나 일어나는지 (' + state.crawlGran + ' 기준)</div>' +
                '<div class="trend-charts">';
            if (crawl.length) {
                html += '<div class="chart-card"><h4 class="h4-row">' +
                    '<span>발견·신규·실패</span>' +
                    '<span class="legend"><span class="lg" style="background:#0066FF">발견</span><span class="lg" style="background:#34A853">신규</span><span class="lg" style="background:#C62828">실패</span></span>' +
                    '</h4>' +
                    '<div class="chart-scroll">' +
                    renderMultiLines('crawlChart', crawl,
                        [{ key: 'plansFound', color: '#0066FF' }, { key: 'plansNew', color: '#34A853' }, { key: 'failCount', color: '#C62828' }]) +
                    '</div></div>';
            }
            if (ntr.length) {
                html += '<div class="chart-card"><h4>신규 요금제 수</h4>' +
                    '<div class="chart-scroll">' +
                    renderMultiLines('newChart', ntr, [{ key: 'plansNew', color: '#E65100' }]) +
                    '</div></div>';
            }
            html += '</div></div>';
        }

        // 가격대 + 데이터 용량 분포
        var priceB = (d.priceDist && d.priceDist.buckets) || [];
        var dataB = (d.dataDist && d.dataDist.buckets) || [];
        html += '<div class="stats-block" id="sec-dist"><h3>📊 분포 (가격대 · 데이터 용량)</h3>' +
            '<div class="dist-grid">';
        if (priceB.length) {
            html += '<div class="chart-card"><h4>가격대 분포</h4>' +
                renderBars('priceDist', priceB.map(function (b) { return { label: b.label, value: b.count, color: PRIMARY }; })) + '</div>';
        }
        if (dataB.length) {
            html += '<div class="chart-card"><h4>데이터 용량 구간</h4>' +
                renderDonut('dataDonut', dataB.map(function (b) {
                    return { label: b.label, value: b.count, color: b.label === '파싱 불가' ? '#B0B0B0' : '#0066FF' };
                }), '요금제 수') + '</div>';
        }
        html += '</div></div>';

        // 가성비 TOP10
        var rank = (d.valueRank && d.valueRank.items) || [];
        if (rank.length) {
            var maxScore = rank[0].score || 1;
            html += '<div class="stats-block" id="sec-rank"><h3>🏆 가성비 TOP ' + rank.length +
                ' <select id="rankFilter" class="inline-select" aria-label="가성비 필터">' +
                '<option value="all"' + (state.rankFilter === 'all' ? ' selected' : '') + '>전체</option>' +
                '<option value="5G"' + (state.rankFilter === '5G' ? ' selected' : '') + '>5G</option>' +
                '<option value="LTE"' + (state.rankFilter === 'LTE' ? ' selected' : '') + '>LTE</option></select></h3>' +
                '<div class="stats-one-line">이 데이터로 알 수 있는 것: 같은 가격대에서 데이터·음성·문자를 가장 많이 주는 요금제</div>' +
                '<div class="rank-list">';
            rank.forEach(function (r) {
                var pct = Math.round((r.score / maxScore) * 100);
                html += '<div class="rank-row"><span class="rank-no">' + r.rank + '</span>' +
                    '<div class="rank-main"><div class="rank-name"><span class="rank-brand" style="color:' + networkColor(r.carrierName) + '">' + esc(r.carrierName) + '</span> ' + esc(r.planName) + '</div>' +
                    '<div class="rank-meta">' + esc(r.networkType) + ' · ' + esc(r.dataAmount) + ' · ' + esc(r.voice) + ' · ' + esc(r.sms) + '</div>' +
                    '<div class="score-track"><div class="score-fill" style="width:' + pct + '%"></div></div></div>' +
                    '<div class="rank-right"><div class="rank-price">' + esc(formatPrice(r.price)) + '</div>' +
                    '<div class="rank-score">' + esc(r.scoreLabel) + '점</div></div></div>';
            });
            html += '</div><div class="stats-note">스코어 = (데이터GB + 음성분×0.3 + 문자건×0.1) ÷ (요금/1천원). 무제한은 100GB·2000분·2000건 가정치.</div></div>';
        }

        // 수집 건강도
        var srcs = (health.sources) || [];
        if (srcs.length) {
            html += '<div class="stats-block" id="sec-health"><h3>🩺 수집 건강도 (24시간)</h3>' +
                '<div class="health-grid">' +
                '<div class="health-item"><span>성공</span><b class="text-ok">' + fmtNum(health.success24h) + '</b></div>' +
                '<div class="health-item"><span>실패</span><b class="text-warn">' + fmtNum(health.fail24h) + '</b></div>' +
                '<div class="health-item"><span>평균 소요</span><b>' + (health.avgDurationSec || 0).toFixed(1) + '초</b></div></div>' +
                '<div class="src-grid">';
            srcs.forEach(function (s) {
                var st = (s.lastStatus || 'NEVER_RUN');
                html += '<div class="src-item"><span class="src-name">' + esc(s.sourceName) +
                    ' <span class="' + (st === 'SUCCESS' ? 'text-ok' : st === 'FAILED' ? 'text-warn' : '') + '">' + esc(st) + '</span></span>' +
                    '<span class="src-rate">성공률 ' + fmtPct(s.successRate) + '</span></div>';
            });
            html += '</div></div>';
        }

        el('statsContent').innerHTML = html;

        // 통계 목차 (정적 섹션 id 기준, 매 렌더 갱신)
        renderStatsNav();
        // 이벤트 재바인딩
        bindStatsEvents();
    }

    function kpiCard(label, value) {
        return '<div class="kpi-card"><div class="kpi-value">' + esc(value) + '</div><div class="kpi-label">' + esc(label) + '</div></div>';
    }

    function bindStatsEvents() {
        var granBtns = document.querySelectorAll('.gran-btn');
        for (var i = 0; i < granBtns.length; i++) {
            (function (btn) {
                btn.addEventListener('click', function () {
                    state.crawlGran = btn.getAttribute('data-gran');
                    loadStats();
                });
            })(granBtns[i]);
        }
        var rankSel = el('rankFilter');
        if (rankSel) {
            rankSel.addEventListener('change', function () {
                state.rankFilter = rankSel.value;
                loadStats();
            });
        }
        var barRects = document.querySelectorAll('#brandBars .bar-rect');
        for (var k = 0; k < barRects.length; k++) {
            barRects[k].addEventListener('click', function () {
                goBrand(this.getAttribute('data-label'));
            });
        }
    }

    // ---------- SVG 차트 렌더러 (바닐라, 접근성: aria + title) ----------

    function renderBars(id, items) {
        var W = 640, H = 220, padL = 8, padB = 28, padT = 12;
        var innerH = H - padT - padB;
        var max = Math.max.apply(null, items.map(function (it) { return it.value; }).concat([1]));
        var slot = W / items.length;
        var barW = Math.min(46, slot * 0.55);
        var parts = [];
        items.forEach(function (it, i) {
            if (it.value <= 0) return;
            var h = Math.max(2, (it.value / max) * innerH);
            var x = i * slot + (slot - barW) / 2;
            var y = H - padB - h;
            parts.push('<rect class="bar-rect" data-label="' + esc(it.label) + '" x="' + x.toFixed(1) + '" y="' + y.toFixed(1) + '" width="' + barW.toFixed(1) + '" height="' + h.toFixed(1) + '" fill="' + esc(it.color || PRIMARY) + '" rx="3"><title>' + esc(it.label + ': ' + it.value) + '</title></rect>');
            parts.push('<text x="' + (i * slot + slot / 2).toFixed(1) + '" y="' + (H - 8) + '" text-anchor="middle" class="svg-tick">' + esc(shortLabel(it.label)) + '</text>');
        });
        // Y축 그리드라인 3개
        for (var g = 1; g <= 3; g++) {
            var gy = padT + innerH * (1 - g / 4);
            parts.push('<line x1="' + padL + '" y1="' + gy.toFixed(1) + '" x2="' + W + '" y2="' + gy.toFixed(1) + '" class="grid-line"></line>');
        }
        return svgWrap(id, W, H, '막대그래프', parts.join(''));
    }

    function renderMultiLines(id, points, series) {
        var W = 640, H = 220, padL = 36, padR = 8, padB = 28, padT = 12;
        var innerW = W - padL - padR;
        var innerH = H - padT - padB;
        var max = 1;
        var values = {};
        series.forEach(function (s) { values[s.key] = true; });
        Object.keys(values).forEach(function (k) {
            points.forEach(function (p) { max = Math.max(max, p[k] || 0); });
        });
        var stepX = points.length > 1 ? innerW / (points.length - 1) : innerW;
        var parts = [];
        for (var g = 0; g <= 3; g++) {
            var gy = padT + innerH * (1 - g / 3);
            parts.push('<line x1="' + padL + '" y1="' + gy.toFixed(1) + '" x2="' + (W - padR) + '" y2="' + gy.toFixed(1) + '" class="grid-line"></line>');
            parts.push('<text x="' + (padL - 6) + '" y="' + (gy + 4).toFixed(1) + '" text-anchor="end" class="svg-tick">' + Math.round(max * g / 3) + '</text>');
        }
        var pathFor = function (key) {
            var d = '';
            points.forEach(function (p, i) {
                var x = padL + i * stepX;
                var y = padT + innerH - Math.min(max, p[key] || 0) / max * innerH;
                d += (i === 0 ? 'M' : 'L') + x.toFixed(1) + ' ' + y.toFixed(1) + ' ';
            });
            return d;
        };
        series.forEach(function (s) {
            parts.push('<path d="' + pathFor(s.key) + '" fill="none" stroke="' + esc(s.color) + '" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round"><title>' + esc(s.key) + '</title></path>');
        });
        // x 틱 (너무 많으면 5개씩)
        var tickEvery = Math.max(1, Math.ceil(points.length / 6));
        points.forEach(function (p, i) {
            if (i % tickEvery !== 0) return;
            var x = padL + i * stepX;
            parts.push('<text x="' + x.toFixed(1) + '" y="' + (H - 8) + '" text-anchor="middle" class="svg-tick">' + esc(shortLabel(p.label)) + '</text>');
        });
        return svgWrap(id, W, H, '꺾은선그래프', parts.join(''));
    }

    function renderDonut(id, segments, centerLabel) {
        var R = 70, S = 190, pad = 6;
        var total = segments.reduce(function (a, s) { return a + s.value; }, 0);
        if (total <= 0) return '<div class="donut-wrap">데이터 없음</div>';
        var full = 2 * Math.PI * R;
        var offset = 0;
        var parts = [];
        segments.forEach(function (seg) {
            var frac = seg.value / total;
            var len = frac * full;
            parts.push('<circle cx="' + S / 2 + '" cy="' + S / 2 + '" r="' + R + '" fill="none" stroke="' + esc(seg.color) + '" stroke-width="26" stroke-dasharray="' + len.toFixed(1) + ' ' + (full - len).toFixed(1) + '" stroke-dashoffset="' + (-offset).toFixed(1) + '"><title>' + esc(seg.label + ': ' + fmtNum(seg.value) + ' (' + fmtPct(frac) + ')') + '</title></circle>');
            offset += len;
        });
        var legend = '';
        segments.forEach(function (seg) {
            legend += '<span class="legend"><i style="background:' + esc(seg.color) + '"></i>' + esc(seg.label) + ' <b>' + fmtNum(seg.value) + '</b></span>';
        });
        parts.push('<text x="' + S / 2 + '" y="' + (S / 2 + 4) + '" text-anchor="middle" class="svg-center">' + esc(centerLabel || '') + '</text>');
        return '<div class="donut-wrap"><svg viewBox="0 0 ' + S + ' ' + S + '" class="donut" role="img" aria-label="도넛차트">' + parts.join('') + '</svg><div class="donut-legend">' + legend + '</div></div>';
    }

    function svgWrap(id, w, h, label, inner) {
        return '<svg id="' + esc(id) + '" viewBox="0 0 ' + w + ' ' + h + '" class="svg-chart" role="img" aria-label="' + esc(label) + '">' + inner + '</svg>';
    }

    function shortLabel(s) {
        s = String(s == null ? '' : s);
        return s.length > 8 ? s.slice(0, 8) : s;
    }

    function setFilterOpen(open) {
        document.body.classList.toggle('filter-open', open);
        el('filterToggle').setAttribute('aria-expanded', open ? 'true' : 'false');
        var backdrop = el('backdrop');
        var isMobile = window.innerWidth <= 900;
        if (isMobile) {
            if (open) backdrop.removeAttribute('hidden');
            else backdrop.setAttribute('hidden', '');
        } else {
            backdrop.setAttribute('hidden', '');
        }
    }

    function bindEvents() {
        el('filterToggle').addEventListener('click', function () {
            setFilterOpen(!document.body.classList.contains('filter-open'));
        });
        el('filterClose').addEventListener('click', function () { setFilterOpen(false); });
        el('backdrop').addEventListener('click', function () { setFilterOpen(false); });
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            var modal = el('notifModal');
            if (modal.open) { modal.close(); return; }
            if (!el('notifPanel').hidden) { closeNotifPanel(); return; }
            setFilterOpen(false);
        });
        window.addEventListener('resize', function () { setFilterOpen(false); });
        el('sortSelect').addEventListener('change', function (e) {
            state.sort = e.target.value;
            applyFilters();
        });
        el('filterNetwork').addEventListener('change', function () {
            state.networks = checkedValues('filterNetwork');
            applyFilters();
        });
        el('filterCarrier').addEventListener('change', function () {
            state.carriers = checkedValues('filterCarrier');
            applyFilters();
        });
        el('minData').addEventListener('input', function (e) {
            state.minData = parseInt(e.target.value, 10);
            el('minDataValue').textContent = state.minData + 'GB 이상';
            applyFilters();
        });
        el('maxPrice').addEventListener('input', function (e) {
            state.maxPrice = parseInt(e.target.value, 10);
            el('maxPriceValue').textContent = state.maxPrice.toLocaleString('ko-KR') + '원 이하';
            applyFilters();
        });
        el('resetFilters').addEventListener('click', resetFilters);
        function requestSync() {
        if (!confirm('지금 요금제를 수집하시겠습니까?\n수집이 끝나면 새로고침 해주세요.')) return;
        var btn = el('totalCount');
        btn.disabled = true;
        btn.textContent = '수집 요청 중…';
        fetch('/api/sync', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: '{}'
        }).then(function () {
            btn.textContent = '예약됨 (새로고침 해주세요)';
            setTimeout(function () {
                btn.disabled = false;
                render();
            }, 5000);
        }).catch(function () {
            btn.disabled = false;
            render();
        });
    }
        el('notifBell').addEventListener('click', openNotifPanel);
        el('logoLink').addEventListener('click', goHome);
        el('totalCount').addEventListener('click', requestSync);
        el('tabStats').addEventListener('click', function () { setTab('stats'); });
        var kwTimer = null;
        el('keyword').addEventListener('input', function (e) {
            clearTimeout(kwTimer);
            kwTimer = setTimeout(function () {
                state.keyword = e.target.value.trim();
                applyFilters();
            }, 200);
        });
        el('brandFilter').addEventListener('change', function (e) {
            state.brand = e.target.value;
            applyFilters();
        });
        el('notifClose').addEventListener('click', closeNotifPanel);
        el('notifBackdrop').addEventListener('click', closeNotifPanel);
        el('notifReadAll').addEventListener('click', markAllRead);
        el('notifCleanup').addEventListener('click', cleanupNotifs);
        el('notifModalClose').addEventListener('click', function () { el('notifModal').close(); });
        el('notifModal').addEventListener('click', function (e) {
            if (e.target === el('notifModal')) el('notifModal').close();
        });
        var tabs = document.querySelectorAll('.notif-tab');
        for (var i = 0; i < tabs.length; i++) {
            tabs[i].addEventListener('click', function () {
                setNotifTab(this.getAttribute('data-type'));
            });
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        renderCurationTags();
        bindEvents();
        loadData();
        loadUnreadCount();
        setInterval(loadUnreadCount, 60000);
    });
})();
