/**
 * Graph List Page JavaScript
 * 图谱列表页面 JavaScript 模块
 */

(function() {
    'use strict';

    let currentFilter = 'all';
    let currentView = 'recommended';
    let searchQuery = '';
    let currentPage = 0;
    let totalPages = 0;
    let totalElements = 0;
    let activeRequestId = 0;
    const pageSize = 20;

    function escapeHtml(value) {
        return String(value ?? '').replace(/[&<>"']/g, function (ch) {
            return {
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#39;'
            }[ch];
        });
    }

    function escapeJsString(value) {
        return String(value ?? '')
            .replace(/\\/g, '\\\\')
            .replace(/'/g, "\\'")
            .replace(/\r/g, '\\r')
            .replace(/\n/g, '\\n')
            .replace(/</g, '\\x3C')
            .replace(/>/g, '\\x3E');
    }

    function safeInteger(value) {
        const number = Number(value);
        return Number.isInteger(number) && number >= 0 ? number : 0;
    }

    function safeImageUrl(value, fallback) {
        const url = String(value ?? '').trim();
        if (!url) return fallback;
        if (url.startsWith('/')) return url;
        try {
            const parsed = new URL(url, window.location.origin);
            return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : fallback;
        } catch (e) {
            return fallback;
        }
    }

    function graphPlaceholderDataUrl(text) {
        const safeText = escapeHtml(String(text || 'Graph').slice(0, 24));
        const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="225" viewBox="0 0 400 225"><defs><linearGradient id="g" x1="0" x2="1" y1="0" y2="1"><stop stop-color="#f8fafc"/><stop offset="1" stop-color="#e2e8f0"/></linearGradient></defs><rect width="400" height="225" fill="url(#g)"/><circle cx="126" cy="82" r="28" fill="#64748b" fill-opacity=".18"/><circle cx="210" cy="132" r="36" fill="#64748b" fill-opacity=".14"/><circle cx="294" cy="82" r="24" fill="#64748b" fill-opacity=".18"/><path d="M150 94 176 112M244 116 274 92" stroke="#64748b" stroke-opacity=".35" stroke-width="4"/><text x="200" y="185" text-anchor="middle" font-family="Arial, sans-serif" font-size="22" font-weight="700" fill="#64748b">${safeText}</text></svg>`;
        return 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(svg);
    }

    document.addEventListener('DOMContentLoaded', function() {
        bindEvents();
        handleUrlParams();
        loadGraphs(0);
    });

    function bindEvents() {
        // Range sliders
        const rangeInputs = document.querySelectorAll('input[type="range"]');
        rangeInputs.forEach(input => {
            input.addEventListener('input', function() {
                const valueDisplay = document.getElementById(this.id.replace('Range', 'Value'));
                if (valueDisplay) {
                    let val = this.value;
                    if (this.id.includes('Density') || this.id.includes('Richness')) {
                        val = (parseFloat(val) / 10).toFixed(1);
                    }
                    valueDisplay.textContent = val;
                }
            });
        });

        // Search input
        const searchInput = document.getElementById('searchInput');
        if (searchInput) {
            searchInput.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') performSearch();
            });
            searchInput.addEventListener('input', function() {
                if (this.value.length > 0) showSearchSuggestions(this.value);
                else {
                    searchQuery = '';
                    hideSearchSuggestions();
                    loadGraphs(0);
                }
            });
        }

        // Keyword filter
        const keywordFilter = document.getElementById('keywordFilter');
        if (keywordFilter) {
            keywordFilter.addEventListener('input', debounce(function() {
                loadGraphs(0);
            }, 300));
        }
    }

    function handleUrlParams() {
        const params = new URLSearchParams(window.location.search);
        const q = params.get('q');
        if (q) {
            searchQuery = q.trim();
            const searchInput = document.getElementById('searchInput');
            if (searchInput) searchInput.value = searchQuery;
            saveSearchHistory(searchQuery);
        }
    }

    async function loadGraphs(page = currentPage) {
        const grid = document.getElementById('graphGrid');
        if (!grid) return;

        const requestedPage = Math.max(0, safeInteger(page));
        const requestId = ++activeRequestId;
        currentPage = requestedPage;
        totalPages = 0;

        grid.innerHTML = '<div class="col-span-full text-center py-12"><span class="loading loading-spinner loading-lg text-primary"></span><p class="mt-4 text-base-content/60">加载图谱中...</p></div>';
        const resultCount = document.getElementById('resultCount');
        if (resultCount) resultCount.textContent = '正在加载图谱...';
        renderPager();

        try {
            const data = await fetchPublicGraphPage(requestedPage);
            if (requestId !== activeRequestId) return;

            currentPage = safeInteger(data.number ?? data.page ?? requestedPage);
            totalPages = safeInteger(data.totalPages);
            totalElements = safeInteger(data.totalElements);

            if (totalPages > 0 && currentPage >= totalPages) {
                loadGraphs(totalPages - 1);
                return;
            }

            const graphs = Array.isArray(data.content) ? data.content : [];
            if (graphs.length === 0) {
                renderEmptyState();
                renderResultCount(0);
                renderPager();
                return;
            }

            renderGraphs(graphs);
            renderResultCount(graphs.length);
            renderPager();
        } catch (e) {
            if (requestId !== activeRequestId) return;
            grid.innerHTML = '<div class="col-span-full text-center py-12"><span class="iconify text-error" data-icon="heroicons:exclamation-circle" data-width="48"></span><p class="mt-4 text-base-content/60">加载失败，请刷新重试</p></div>';
            document.getElementById('resultCount').textContent = '加载失败';
            renderPager();
        }
    }

    async function fetchPublicGraphPage(page) {
        const params = new URLSearchParams({
            page: String(page),
            size: String(pageSize),
            sortBy: currentView
        });
        const domain = getSelectedDomain();
        const refineKeyword = getRefineKeyword();
        if (domain) {
            params.set('domain', domain);
        }
        if (searchQuery) {
            params.set('keyword', searchQuery);
        }
        if (refineKeyword) {
            params.set('refineKeyword', refineKeyword);
        }
        const hasKeyword = Boolean(searchQuery || refineKeyword);
        const endpoint = currentView === 'recommended' && !hasKeyword ? '/api/graph/recommended' : '/api/graph/search';
        const response = await fetch(endpoint + '?' + params.toString(), {
            credentials: 'include'
        });
        if (!response.ok) {
            throw new Error('加载失败');
        }
        return response.json();
    }

    function getSelectedDomain() {
        return currentFilter && currentFilter !== 'all' ? currentFilter : '';
    }

    function getRefineKeyword() {
        return document.getElementById('keywordFilter')?.value.trim() || '';
    }

    function hasActiveFilters() {
        return Boolean(getSelectedDomain() || searchQuery || getRefineKeyword());
    }

    function renderResultCount(visibleCount) {
        const resultCount = document.getElementById('resultCount');
        if (!resultCount) return;
        if (totalElements === 0) {
            resultCount.textContent = hasActiveFilters() ? '暂无匹配图谱' : '暂无图谱';
            return;
        }
        const pageText = `显示第 ${currentPage + 1}/${Math.max(totalPages, 1)} 页，当前 ${visibleCount} 个`;
        resultCount.textContent = hasActiveFilters()
            ? `${pageText}，筛选到 ${totalElements} 个图谱`
            : `${pageText}，共 ${totalElements} 个图谱`;
    }

    function renderPager() {
        const pager = document.getElementById('graphPager');
        if (!pager) return;

        if (totalPages <= 1) {
            pager.innerHTML = '';
            return;
        }

        const buttons = [];
        buttons.push(createPagerButton(currentPage - 1, '上一页', 'heroicons:chevron-left', currentPage === 0));

        const pages = getVisiblePages(currentPage, totalPages);
        let previous = -1;
        pages.forEach(page => {
            if (previous >= 0 && page - previous > 1) {
                buttons.push('<span class="join-item btn btn-sm btn-disabled graph-pager-ellipsis">...</span>');
            }
            buttons.push(createPagerButton(page, String(page + 1), null, false, page === currentPage));
            previous = page;
        });

        buttons.push(createPagerButton(currentPage + 1, '下一页', 'heroicons:chevron-right', currentPage >= totalPages - 1));
        pager.innerHTML = `<div class="join">${buttons.join('')}</div>`;
    }

    function getVisiblePages(page, pages) {
        const values = new Set([0, pages - 1, page - 1, page, page + 1]);
        return Array.from(values)
            .filter(value => value >= 0 && value < pages)
            .sort((a, b) => a - b);
    }

    function createPagerButton(page, label, icon, disabled, active) {
        const classes = ['join-item', 'btn', 'btn-sm'];
        if (active) classes.push('btn-primary');
        if (disabled) classes.push('btn-disabled');

        const content = icon
            ? `<span class="iconify" data-icon="${icon}" data-width="16"></span><span class="sr-only">${escapeHtml(label)}</span>`
            : escapeHtml(label);

        return `<button type="button" class="${classes.join(' ')}" ${disabled ? 'disabled' : ''} onclick="goToGraphPage(${page})">${content}</button>`;
    }

    function renderGraphs(graphs) {
        const grid = document.getElementById('graphGrid');
        if (!grid) return;

        grid.innerHTML = graphs.map(graph => {
            const graphId = safeInteger(graph.graphId);
            const graphName = graph.name || graph.graphName || '未命名图谱';
            const coverFallback = graphPlaceholderDataUrl(graphName);
            const coverImage = safeImageUrl(graph.coverImage, coverFallback);

            return `
            <div class="card bg-base-100 shadow-soft graph-card fade-in" onclick="viewGraph(${graphId})">
                <figure>
                    <img src="${escapeHtml(coverImage)}" alt="${escapeHtml(graphName)}" class="graph-card-image">
                </figure>
                <div class="card-body p-4">
                    <div class="flex items-center justify-between mb-2">
                        <span class="badge badge-primary badge-sm domain-badge">${escapeHtml(graph.domain || '未分类')}</span>
                        <button class="btn btn-ghost btn-xs favorite-btn ${isFavorited(graphId) ? 'favorited' : ''}" onclick="toggleFavorite(${graphId}, '${escapeJsString(graphName)}', event)">
                            <span class="iconify" data-icon="heroicons:heart${isFavorited(graphId) ? '-solid' : ''}" data-width="16"></span>
                        </button>
                    </div>
                    <h3 class="card-title text-base font-semibold line-clamp-1">${escapeHtml(graphName)}</h3>
                    <p class="text-sm text-base-content/70 line-clamp-2 mb-3">${escapeHtml(graph.description || '暂无描述')}</p>
                    <div class="provider-info mb-2">
                        <span class="iconify" data-icon="heroicons:user" data-width="14"></span>
                        <span>${escapeHtml(graph.uploaderName || '匿名')}</span>
                    </div>
                    <div class="stats-info">
                        <span class="flex items-center gap-1">
                            <span class="iconify" data-icon="heroicons:circle" data-width="12"></span>
                            ${safeInteger(graph.nodeCount)} 节点
                        </span>
                        <span class="flex items-center gap-1">
                            <span class="iconify" data-icon="heroicons:eye" data-width="12"></span>
                            ${safeInteger(graph.viewCount)}
                        </span>
                        <span class="flex items-center gap-1">
                            <span class="iconify" data-icon="heroicons:heart" data-width="12"></span>
                            ${safeInteger(graph.collectCount)}
                        </span>
                    </div>
                </div>
            </div>
        `}).join('');
    }

    function isFavorited(graphId) {
        var id = String(graphId);
        var favorites = JSON.parse(localStorage.getItem('favorites') || '[]');
        return favorites.some(function(item) { return String(item.id) === id; });
    }

    function renderEmptyState() {
        const grid = document.getElementById('graphGrid');
        if (!grid) return;
        const message = hasActiveFilters() ? '暂无匹配图谱' : '暂无图谱';
        grid.innerHTML = `<div class="col-span-full text-center py-12"><span class="iconify text-base-content/30" data-icon="heroicons:cube-transparent" data-width="48"></span><p class="mt-4 text-base-content/60">${message}</p></div>`;
    }

    function showSearchSuggestions(query) {
        const suggestions = document.getElementById('searchSuggestions');
        if (!suggestions) return;

        const history = JSON.parse(localStorage.getItem('searchHistory') || '[]');
        const filtered = history.filter(h => h.toLowerCase().includes(query.toLowerCase())).slice(0, 5);

        if (filtered.length > 0) {
            suggestions.innerHTML = filtered.map(item => `<div class="suggestion-item" onclick="selectSuggestion('${escapeJsString(item)}')">${escapeHtml(item)}</div>`).join('');
            suggestions.classList.remove('hidden');
        } else {
            suggestions.classList.add('hidden');
        }
    }

    function hideSearchSuggestions() {
        const suggestions = document.getElementById('searchSuggestions');
        if (suggestions) suggestions.classList.add('hidden');
    }

    function debounce(func, wait) {
        let timeout;
        return function(...args) {
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(this, args), wait);
        };
    }

    // Global functions
    window.performSearch = function() {
        const query = document.getElementById('searchInput')?.value.trim();
        searchQuery = query || '';
        if (!query) {
            hideSearchSuggestions();
            loadGraphs(0);
            return;
        }

        saveSearchHistory(query);

        hideSearchSuggestions();
        loadGraphs(0);
    };

    function saveSearchHistory(query) {
        if (!query) return;
        let history = JSON.parse(localStorage.getItem('searchHistory') || '[]');
        if (!history.includes(query)) {
            history.unshift(query);
            history = history.slice(0, 10);
            localStorage.setItem('searchHistory', JSON.stringify(history));
        }
    }

    window.selectSuggestion = function(query) {
        const searchInput = document.getElementById('searchInput');
        if (searchInput) searchInput.value = query;
        performSearch();
    };

    window.filterGraphs = function(filter) {
        currentFilter = filter;
        
        // Update button states
        document.querySelectorAll('.filter-btn').forEach(btn => {
            btn.classList.toggle('active', btn.getAttribute('data-filter') === filter);
        });

        loadGraphs(0);
    };

    window.toggleView = function(view) {
        currentView = view;
        
        // Update button states
        document.querySelectorAll('.view-toggle').forEach(btn => {
            btn.classList.toggle('active', btn.getAttribute('data-view') === view);
        });

        currentPage = 0;
        loadGraphs(0);
    };

    window.resetFilters = function() {
        currentFilter = 'all';
        searchQuery = '';
        const searchInput = document.getElementById('searchInput');
        const keywordFilter = document.getElementById('keywordFilter');
        if (searchInput) searchInput.value = '';
        if (keywordFilter) keywordFilter.value = '';
        document.querySelectorAll('.filter-btn').forEach(btn => {
            btn.classList.toggle('active', btn.getAttribute('data-filter') === 'all');
        });
        
        // Reset range sliders
        document.querySelectorAll('input[type="range"]').forEach(input => {
            input.value = input.id.includes('min') ? input.min : input.max;
            const valueDisplay = document.getElementById(input.id.replace('Range', 'Value'));
            if (valueDisplay) valueDisplay.textContent = input.value;
        });

        loadGraphs(0);
    };

    window.goToGraphPage = function(page) {
        const nextPage = Number(page);
        if (!Number.isInteger(nextPage)) return;
        if (nextPage < 0 || nextPage >= totalPages || nextPage === currentPage) return;
        loadGraphs(nextPage);
    };

    window.viewGraph = function(graphId) {
        window.location.href = '/graph/graph_detail.html?id=' + graphId;
    };

    window.toggleFavorite = async function(graphId, graphName, event) {
        event.stopPropagation();
        
        var id = String(graphId);
        var btn = event.target.closest('.favorite-btn');
        var icon = btn.querySelector('.iconify');
        var wasFavorited = isFavorited(graphId);
        
        // 立即更新UI
        if (wasFavorited) {
            btn.classList.remove('favorited');
            icon.setAttribute('data-icon', 'heroicons:heart');
        } else {
            btn.classList.add('favorited');
            icon.setAttribute('data-icon', 'heroicons:heart-solid');
        }
        
        // 后台执行API和localStorage操作
        try {
            await fetch('/api/graph/' + graphId + '/favorite', { method: 'POST', credentials: 'include' });
            var favorites = JSON.parse(localStorage.getItem('favorites') || '[]');
            if (wasFavorited) {
                favorites = favorites.filter(function(item) { return String(item.id) !== id; });
                localStorage.setItem('favorites', JSON.stringify(favorites));
                showNotification('已取消收藏', 'info');
            } else {
                favorites.push({ id: id, name: graphName, addedAt: new Date().toISOString() });
                localStorage.setItem('favorites', JSON.stringify(favorites));
                showNotification('已添加到收藏', 'success');
            }
        } catch (e) {
            // 失败时回滚UI
            if (wasFavorited) {
                btn.classList.add('favorited');
                icon.setAttribute('data-icon', 'heroicons:heart-solid');
            } else {
                btn.classList.remove('favorited');
                icon.setAttribute('data-icon', 'heroicons:heart');
            }
            showNotification(wasFavorited ? '操作失败' : '收藏失败，请先登录', 'error');
        }
    };

    function showNotification(message, type) {
        if (window.showNotification) window.showNotification(message, type);
    }

})();
