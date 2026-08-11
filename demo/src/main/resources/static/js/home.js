/**
 * Home Page JavaScript
 * 首页 JavaScript 模块
 * 收藏功能完全照搬探索页 (graph-list.js)
 */

(function() {
    'use strict';

    const RECOMMENDED_PAGE_SIZE = 8;
    let recommendedPage = 0;
    let recommendedTotalPages = 0;
    let recommendedTotalElements = 0;

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
        const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="225" viewBox="0 0 400 225"><defs><linearGradient id="g" x1="0" x2="1" y1="0" y2="1"><stop stop-color="#1e3a8a"/><stop offset="1" stop-color="#0f766e"/></linearGradient></defs><rect width="400" height="225" fill="url(#g)"/><circle cx="118" cy="84" r="28" fill="#ffffff" fill-opacity=".18"/><circle cx="206" cy="132" r="36" fill="#ffffff" fill-opacity=".14"/><circle cx="297" cy="78" r="24" fill="#ffffff" fill-opacity=".18"/><path d="M142 96 178 116M239 116 276 88" stroke="#fff" stroke-opacity=".35" stroke-width="4"/><text x="200" y="185" text-anchor="middle" font-family="Arial, sans-serif" font-size="22" font-weight="700" fill="#fff">${safeText}</text></svg>`;
        return 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(svg);
    }

    // Initialize when DOM is loaded
    document.addEventListener('DOMContentLoaded', function() {
        initializeSearchFunctionality();
        initializeFilters();
        loadRecommendedGraphs();
    });

    // Load recommended graphs from API
    async function loadRecommendedGraphs(page = recommendedPage) {
        const container = document.getElementById('recommendedGraphsGrid');
        const meta = document.getElementById('recommendedGraphsMeta');
        const pager = document.getElementById('recommendedGraphsPager');
        if (!container) return;

        const requestedPage = Math.max(0, Number.isInteger(Number(page)) ? Number(page) : 0);
        recommendedPage = requestedPage;
        container.innerHTML = `
            <div class="col-span-full flex flex-col items-center justify-center py-16">
                <span class="loading loading-spinner loading-lg text-primary"></span>
                <p class="mt-4 text-base-content/60">加载图谱中...</p>
            </div>
        `;
        if (meta) {
            meta.textContent = '正在加载公开图谱...';
        }
        if (pager) {
            pager.innerHTML = '';
        }
        
        try {
            const params = new URLSearchParams({
                page: String(requestedPage),
                size: String(RECOMMENDED_PAGE_SIZE)
            });
            const response = await fetch('/api/graph/recommended?' + params.toString(), {
                method: 'GET',
                credentials: 'include'
            });
            
            if (!response.ok) {
                throw new Error('加载失败');
            }
            
            const data = await response.json();
            const graphs = Array.isArray(data.content) ? data.content : [];
            recommendedPage = safeInteger(data.number ?? requestedPage);
            recommendedTotalPages = safeInteger(data.totalPages);
            recommendedTotalElements = safeInteger(data.totalElements);

            if (recommendedTotalPages > 0 && recommendedPage >= recommendedTotalPages) {
                loadRecommendedGraphs(recommendedTotalPages - 1);
                return;
            }
            
            if (graphs.length === 0) {
                container.innerHTML = `
                    <div class="col-span-full flex flex-col items-center justify-center py-16">
                        <span class="iconify text-base-content/30" data-icon="heroicons:cube-transparent" data-width="64"></span>
                        <p class="mt-4 text-base-content/60">暂无已上线的图谱</p>
                        <p class="text-sm text-base-content/40 mt-2">成为第一个分享图谱的人吧！</p>
                    </div>
                `;
                renderRecommendedGraphsMeta();
                renderRecommendedGraphsPager();
                return;
            }
            
            container.innerHTML = graphs.map(graph => createGraphCardHTML(graph)).join('');
            renderRecommendedGraphsMeta();
            renderRecommendedGraphsPager();
            
        } catch (error) {
            console.error('加载推荐图谱失败:', error);
            container.innerHTML = `
                <div class="col-span-full flex flex-col items-center justify-center py-16">
                    <span class="iconify text-error/50" data-icon="heroicons:exclamation-circle" data-width="64"></span>
                    <p class="mt-4 text-base-content/60">加载失败，请刷新重试</p>
                    <button class="btn btn-sm btn-outline mt-4" onclick="loadRecommendedGraphs(${recommendedPage})">重试</button>
                </div>
            `;
            if (meta) {
                meta.textContent = '公开图谱加载失败';
            }
            if (pager) {
                pager.innerHTML = '';
            }
        }
    }

    function renderRecommendedGraphsMeta() {
        const meta = document.getElementById('recommendedGraphsMeta');
        if (!meta) return;

        if (recommendedTotalElements === 0) {
            meta.textContent = '暂无已上线的公开图谱';
            return;
        }

        const totalPages = Math.max(recommendedTotalPages, 1);
        meta.textContent = `共 ${recommendedTotalElements} 个公开图谱，当前第 ${recommendedPage + 1}/${totalPages} 页`;
    }

    function renderRecommendedGraphsPager() {
        const pager = document.getElementById('recommendedGraphsPager');
        if (!pager) return;

        if (recommendedTotalPages <= 1) {
            pager.innerHTML = '';
            return;
        }

        const buttons = [];
        buttons.push(createRecommendedPagerButton(recommendedPage - 1, '上一页', 'heroicons:chevron-left', recommendedPage === 0));

        const pages = getVisiblePages(recommendedPage, recommendedTotalPages);
        let previous = -1;
        pages.forEach(page => {
            if (previous >= 0 && page - previous > 1) {
                buttons.push('<span class="join-item btn btn-sm btn-disabled home-pager-ellipsis">...</span>');
            }
            buttons.push(createRecommendedPagerButton(page, String(page + 1), null, false, page === recommendedPage));
            previous = page;
        });

        buttons.push(createRecommendedPagerButton(recommendedPage + 1, '下一页', 'heroicons:chevron-right', recommendedPage >= recommendedTotalPages - 1));

        pager.innerHTML = `<div class="join">${buttons.join('')}</div>`;
    }

    function getVisiblePages(currentPage, totalPages) {
        const pages = new Set([0, totalPages - 1, currentPage - 1, currentPage, currentPage + 1]);
        return Array.from(pages)
            .filter(page => page >= 0 && page < totalPages)
            .sort((a, b) => a - b);
    }

    function createRecommendedPagerButton(page, label, icon, disabled, active) {
        const classes = ['join-item', 'btn', 'btn-sm'];
        if (active) classes.push('btn-primary');
        if (disabled) classes.push('btn-disabled');

        const content = icon
            ? `<span class="iconify" data-icon="${icon}" data-width="16"></span><span class="sr-only">${escapeHtml(label)}</span>`
            : escapeHtml(label);

        return `<button type="button" class="${classes.join(' ')}" ${disabled ? 'disabled' : ''} onclick="goToRecommendedGraphsPage(${page})">${content}</button>`;
    }

    window.goToRecommendedGraphsPage = function(page) {
        const nextPage = Number(page);
        if (!Number.isInteger(nextPage)) return;
        if (nextPage < 0 || nextPage >= recommendedTotalPages || nextPage === recommendedPage) return;
        loadRecommendedGraphs(nextPage);
    };

    // 判断是否已收藏 - 完全照搬探索页
    function isFavorited(graphId) {
        var id = String(graphId);
        var favorites = JSON.parse(localStorage.getItem('favorites') || '[]');
        return favorites.some(function(item) { return String(item.id) === id; });
    }

    // Create graph card HTML - 收藏按钮完全照搬探索页写法
    function createGraphCardHTML(graph) {
        var graphId = safeInteger(graph.graphId);
        var graphName = graph.name || graph.graphName || '未命名图谱';
        var coverImage = safeImageUrl(graph.coverImage, graphPlaceholderDataUrl(graphName));
        var description = graph.description || '暂无描述';
        var nodeCount = safeInteger(graph.nodeCount);
        var viewCount = safeInteger(graph.viewCount);
        var uploadDate = graph.uploadDate || '';
        
        return `
            <div data-repeatable="true" data-type="card" class="card bg-base-100 shadow-soft hover-lift cursor-pointer academic-border" onclick="viewGraphDetail(${graphId})">
                <figure class="relative">
                    <img loading="lazy" src="${escapeHtml(coverImage)}" alt="${escapeHtml(graphName)}" class="w-full h-44 object-cover">
                    <button class="btn btn-circle btn-sm absolute top-3 right-3 bg-base-100/90 hover:bg-base-100 border-0 shadow-soft favorite-btn ${isFavorited(graphId) ? 'favorited' : ''}" onclick="toggleFavorite(${graphId}, '${escapeJsString(graphName)}', event)">
                        <span class="iconify" data-icon="heroicons:heart${isFavorited(graphId) ? '-solid' : ''}" data-width="16"></span>
                    </button>
                </figure>
                <div class="card-body p-6">
                    <h3 class="card-title text-lg font-semibold mb-3">${escapeHtml(graphName)}</h3>
                    <p class="text-sm text-base-content/70 line-clamp-2 leading-relaxed mb-4">${escapeHtml(description)}</p>
                    <div class="flex items-center justify-between text-xs text-base-content/60">
                        <span class="flex items-center gap-1">
                            <span class="iconify" data-icon="heroicons:cube" data-width="14"></span>
                            ${nodeCount} 节点
                        </span>
                        <span class="flex items-center gap-1">
                            <span class="iconify" data-icon="heroicons:eye" data-width="14"></span>
                            ${viewCount}
                        </span>
                        <span>${escapeHtml(uploadDate)}</span>
                    </div>
                </div>
            </div>
        `;
    }

    // Navigate to graph detail page
    window.viewGraphDetail = function(graphId) {
        window.location.href = '/graph/graph_detail.html?id=' + graphId;
    };

    // Search functionality
    function initializeSearchFunctionality() {
        const searchInput = document.getElementById('searchInput');
        const suggestionsContainer = document.getElementById('searchSuggestions');
        const suggestionsList = document.getElementById('suggestionsList');

        if (!searchInput) return;

        // Show suggestions on input focus
        searchInput.addEventListener('focus', function() {
            showSearchSuggestions();
        });

        // Hide suggestions when clicking outside
        document.addEventListener('click', function(event) {
            if (!searchInput.contains(event.target) && !suggestionsContainer.contains(event.target)) {
                hideSearchSuggestions();
            }
        });

        // Handle input changes with debouncing
        let searchTimeout;
        searchInput.addEventListener('input', function() {
            clearTimeout(searchTimeout);
            const query = this.value.trim();
            
            searchTimeout = setTimeout(() => {
                if (query.length > 0) {
                    updateSearchSuggestions(query);
                    showSearchSuggestions();
                } else {
                    showDefaultSuggestions();
                }
            }, 300);
        });

        // Handle Enter key
        searchInput.addEventListener('keydown', function(event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                performSearch();
            }
        });

        // Handle arrow key navigation
        searchInput.addEventListener('keydown', function(event) {
            const suggestions = suggestionsList.querySelectorAll('.suggestion-item');
            let currentIndex = Array.from(suggestions).findIndex(item => item.classList.contains('active'));

            if (event.key === 'ArrowDown') {
                event.preventDefault();
                currentIndex = currentIndex < suggestions.length - 1 ? currentIndex + 1 : 0;
                highlightSuggestion(suggestions, currentIndex);
            } else if (event.key === 'ArrowUp') {
                event.preventDefault();
                currentIndex = currentIndex > 0 ? currentIndex - 1 : suggestions.length - 1;
                highlightSuggestion(suggestions, currentIndex);
            } else if (event.key === 'Enter' && currentIndex >= 0) {
                event.preventDefault();
                suggestions[currentIndex].click();
            }
        });
    }

    function highlightSuggestion(suggestions, index) {
        suggestions.forEach((item, i) => {
            if (i === index) {
                item.classList.add('active');
                item.style.backgroundColor = 'var(--color-primary)';
                item.style.color = 'var(--color-primary-content)';
            } else {
                item.classList.remove('active');
                item.style.backgroundColor = '';
                item.style.color = '';
            }
        });
    }

    function showSearchSuggestions() {
        const suggestionsContainer = document.getElementById('searchSuggestions');
        if (suggestionsContainer) {
            suggestionsContainer.classList.remove('hidden');
            showDefaultSuggestions();
        }
    }

    function hideSearchSuggestions() {
        const suggestionsContainer = document.getElementById('searchSuggestions');
        if (suggestionsContainer) {
            suggestionsContainer.classList.add('hidden');
        }
    }

    function showDefaultSuggestions() {
        const suggestionsList = document.getElementById('suggestionsList');
        if (!suggestionsList || !window.APP_GLOBALS || !window.APP_GLOBALS.search) return;
        
        const suggestions = window.APP_GLOBALS.search.getSearchSuggestions();

        suggestionsList.innerHTML = '';
        suggestions.slice(0, 5).forEach(suggestion => {
            const isHistory = window.APP_GLOBALS.search.history.includes(suggestion);
            const icon = isHistory ? 'heroicons:clock' : 'heroicons:magnifying-glass';
            const item = createSuggestionItem(suggestion, icon);
            suggestionsList.appendChild(item);
        });
    }

    function updateSearchSuggestions(query) {
        const suggestionsList = document.getElementById('suggestionsList');
        if (!suggestionsList || !window.APP_GLOBALS || !window.APP_GLOBALS.search) return;
        
        const allSuggestions = [
            ...window.APP_GLOBALS.search.history,
            ...window.APP_GLOBALS.search.recommendations
        ];
        const filtered = allSuggestions.filter(item => 
            item.toLowerCase().includes(query.toLowerCase())
        ).slice(0, 5);

        suggestionsList.innerHTML = '';
        filtered.forEach(suggestion => {
            const isHistory = window.APP_GLOBALS.search.history.includes(suggestion);
            const icon = isHistory ? 'heroicons:clock' : 'heroicons:magnifying-glass';
            const item = createSuggestionItem(suggestion, icon);
            suggestionsList.appendChild(item);
        });
    }

    function createSuggestionItem(text, iconName) {
        const item = document.createElement('div');
        item.className = 'suggestion-item';
        item.innerHTML = `
            <span class="iconify" data-icon="${iconName}" data-width="16"></span>
            <span class="flex-1">${escapeHtml(text)}</span>
        `;
        item.onclick = () => {
            document.getElementById('searchInput').value = text;
            performSearch();
        };
        return item;
    }

    window.performSearch = function() {
        const query = document.getElementById('searchInput').value.trim();
        if (query && window.APP_GLOBALS && window.APP_GLOBALS.search) {
            window.APP_GLOBALS.search.performSearch(query);
            hideSearchSuggestions();
        }
    };

    window.searchTag = function(tag) {
        document.getElementById('searchInput').value = tag;
        performSearch();
    };

    // ========== 收藏功能 - 完全照搬探索页 graph-list.js ==========
    window.toggleFavorite = async function(graphId, graphName, event) {
        if (event && event.stopPropagation) {
            event.stopPropagation();
        }
        
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

    // showNotification 包装函数 - 与探索页一致
    function showNotification(message, type) {
        if (window.showNotification) window.showNotification(message, type);
    }
    // ========== 收藏功能结束 ==========

    // Filter functionality
    function initializeFilters() {
        window.APP_GLOBALS = window.APP_GLOBALS || {};
        window.APP_GLOBALS.filters = {
            domain: 'all',
            time: 'all',
            popularity: 'all'
        };
    }

    window.applyFilter = function(filterType, value) {
        window.APP_GLOBALS.filters[filterType] = value;
        
        updateFilterButtonText(filterType, value);
        
        const filterLabels = {
            domain: '领域',
            time: '时间',
            popularity: '热门程度'
        };
        
        const valueLabels = {
            all: '全部',
            ai: '人工智能',
            medical: '医学健康',
            finance: '金融经济',
            education: '教育学习',
            week: '最近一周',
            month: '最近一月',
            year: '最近一年',
            hot: '最热门',
            views: '最多浏览',
            favorites: '最多收藏'
        };
        
        const filterLabel = filterLabels[filterType];
        const valueLabel = valueLabels[value] || value;
        
        if (window.showNotification) {
            window.showNotification(`已应用${filterLabel}筛选: ${valueLabel}`, 'info');
        }
        
        animateFilterResults();
    };

    function updateFilterButtonText(filterType, value) {
        const filterButtons = document.querySelectorAll('.dropdown button');
        const filterTypeMap = {
            domain: 0,
            time: 1,
            popularity: 2
        };
        
        const buttonIndex = filterTypeMap[filterType];
        if (buttonIndex !== undefined && filterButtons[buttonIndex]) {
            const button = filterButtons[buttonIndex];
            const textNodes = Array.from(button.childNodes).filter(node => node.nodeType === Node.TEXT_NODE);
            
            const valueLabels = {
                all: filterType === 'domain' ? '领域筛选' : filterType === 'time' ? '时间筛选' : '热门程度',
                ai: '人工智能',
                medical: '医学健康',
                finance: '金融经济',
                education: '教育学习',
                week: '最近一周',
                month: '最近一月',
                year: '最近一年',
                hot: '最热门',
                views: '最多浏览',
                favorites: '最多收藏'
            };
            
            if (textNodes.length > 0) {
                textNodes[0].textContent = valueLabels[value] || value;
            }
        }
    }

    function animateFilterResults() {
        const cards = document.querySelectorAll('.card');
        cards.forEach((card, index) => {
            card.style.opacity = '0.6';
            card.style.transform = 'scale(0.98)';
            
            setTimeout(() => {
                card.style.opacity = '1';
                card.style.transform = 'scale(1)';
            }, index * 30 + 150);
        });
    }

    // Accessibility - ESC key to close dropdowns
    document.addEventListener('keydown', function(event) {
        if (event.key === 'Escape') {
            hideSearchSuggestions();
            
            const openDropdowns = document.querySelectorAll('.dropdown[open]');
            openDropdowns.forEach(dropdown => {
                dropdown.removeAttribute('open');
            });
        }
    });

    // Expose loadRecommendedGraphs for retry button
    window.loadRecommendedGraphs = loadRecommendedGraphs;

})();
