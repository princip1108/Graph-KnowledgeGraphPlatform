/**
 * Home Page JavaScript
 * 首页 JavaScript 模块
 * 收藏功能完全照搬探索页 (graph-list.js)
 */

console.log('=== home.js v4 loaded ===');

(function() {
    'use strict';

    // Initialize when DOM is loaded
    document.addEventListener('DOMContentLoaded', function() {
        initializeSearchFunctionality();
        initializeFilters();
        loadRecommendedGraphs();
    });

    // 判断是否已收藏 - 直接读取 localStorage
    function isFavorited(graphId) {
        var id = String(graphId);
        var favorites = JSON.parse(localStorage.getItem('favorites') || '[]');
        return favorites.some(function(item) { return String(item.id) === id; });
    }

    // Load recommended graphs from API
    async function loadRecommendedGraphs() {
        const container = document.getElementById('recommendedGraphsGrid');
        if (!container) return;
        
        try {
            const response = await fetch('/api/graph/public?page=0&size=8&sortBy=viewCount', {
                method: 'GET',
                credentials: 'include'
            });
            
            if (!response.ok) {
                throw new Error('加载失败');
            }
            
            const data = await response.json();
            const graphs = data.content || [];
            
            if (graphs.length === 0) {
                container.innerHTML = `
                    <div class="col-span-full flex flex-col items-center justify-center py-16">
                        <span class="iconify text-base-content/30" data-icon="heroicons:cube-transparent" data-width="64"></span>
                        <p class="mt-4 text-base-content/60">暂无已上线的图谱</p>
                        <p class="text-sm text-base-content/40 mt-2">成为第一个分享图谱的人吧！</p>
                    </div>
                `;
                return;
            }
            
            container.innerHTML = graphs.map(graph => createGraphCardHTML(graph)).join('');
            
        } catch (error) {
            console.error('加载推荐图谱失败:', error);
            container.innerHTML = `
                <div class="col-span-full flex flex-col items-center justify-center py-16">
                    <span class="iconify text-error/50" data-icon="heroicons:exclamation-circle" data-width="64"></span>
                    <p class="mt-4 text-base-content/60">加载失败，请刷新重试</p>
                    <button class="btn btn-sm btn-outline mt-4" onclick="loadRecommendedGraphs()">重试</button>
                </div>
            `;
        }
    }

    // Create graph card HTML - 统一使用 toggleFavorite(graphId, graphName, event)
    function createGraphCardHTML(graph) {
        var coverImage = graph.coverImage || 'https://placehold.co/400x225/1E3A8A/FFFFFF?text=Graph';
        var description = graph.description || '暂无描述';
        var nodeCount = graph.nodeCount || 0;
        var uploadDate = graph.uploadDate || '';
        var graphId = graph.graphId;
        var graphName = (graph.name || '').replace(/'/g, "\\'");
        var favorited = isFavorited(graphId);
        
        return `
            <div data-repeatable="true" data-type="card" class="card bg-base-100 shadow-soft hover-lift cursor-pointer academic-border" onclick="viewGraphDetail(${graphId})">
                <figure class="relative">
                    <img loading="lazy" src="${coverImage}" alt="${graph.name}" class="w-full h-44 object-cover">
                    <button class="btn btn-circle btn-sm absolute top-3 right-3 bg-base-100/90 hover:bg-base-100 border-0 shadow-soft favorite-btn ${favorited ? 'favorited' : ''}" onclick="toggleFavorite(${graphId}, '${graphName}', event)">
                        <span class="iconify" data-icon="heroicons:heart${favorited ? '-solid' : ''}" data-width="16"></span>
                    </button>
                </figure>
                <div class="card-body p-6">
                    <h3 class="card-title text-lg font-semibold mb-3">${graph.name}</h3>
                    <p class="text-sm text-base-content/70 line-clamp-2 leading-relaxed mb-4">${description}</p>
                    <div class="flex items-center justify-between text-xs text-base-content/60">
                        <span class="flex items-center gap-1">
                            <span class="iconify" data-icon="heroicons:cube" data-width="14"></span>
                            ${nodeCount} 节点
                        </span>
                        <span class="flex items-center gap-1">
                            <span class="iconify" data-icon="heroicons:eye" data-width="14"></span>
                            ${graph.viewCount || 0}
                        </span>
                        <span>${uploadDate}</span>
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
            <span class="flex-1">${text}</span>
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
        console.log('[home.js] toggleFavorite called:', graphId, graphName);
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
            console.error('[home.js] toggleFavorite error:', e);
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
