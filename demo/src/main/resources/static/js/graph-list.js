/**
 * Graph List Page JavaScript
 * 图谱列表页面 JavaScript 模块
 */

(function() {
    'use strict';

    let currentFilter = 'all';
    let currentView = 'recommended';
    let allGraphs = [];

    document.addEventListener('DOMContentLoaded', function() {
        loadGraphs();
        bindEvents();
        handleUrlParams();
    });

    function bindEvents() {
        // Range sliders - apply filters on change
        const rangeInputs = document.querySelectorAll('input[type="range"]');
        rangeInputs.forEach(input => {
            input.addEventListener('input', function() {
                const valueDisplay = document.getElementById(this.id.replace('Range', 'Value'));
                if (valueDisplay) {
                    let val = this.value;
                    if (this.id.includes('Density')) {
                        val = (parseFloat(val) / 10).toFixed(1);
                    }
                    valueDisplay.textContent = val;
                }
            });
            // Apply filters after user stops dragging
            input.addEventListener('change', debounce(applyFilters, 200));
        });

        // Search input
        const searchInput = document.getElementById('searchInput');
        if (searchInput) {
            searchInput.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') performSearch();
            });
            searchInput.addEventListener('input', function() {
                if (this.value.length > 0) showSearchSuggestions(this.value);
                else hideSearchSuggestions();
            });
        }

        // Keyword filter
        const keywordFilter = document.getElementById('keywordFilter');
        if (keywordFilter) {
            keywordFilter.addEventListener('input', debounce(applyFilters, 300));
        }
    }
    
    // Global function for slider value update (called from HTML oninput)
    window.updateSliderValue = function(input, format) {
        const valueDisplay = document.getElementById(input.id.replace('Range', 'Value'));
        if (valueDisplay) {
            let val = input.value;
            if (format === 'decimal') {
                val = (parseFloat(val) / 10).toFixed(1);
            }
            valueDisplay.textContent = val;
        }
    };

    function handleUrlParams() {
        const params = new URLSearchParams(window.location.search);
        const q = params.get('q');
        if (q) {
            const searchInput = document.getElementById('searchInput');
            if (searchInput) searchInput.value = q;
            performSearch();
        }
    }

    async function loadGraphs() {
        const grid = document.getElementById('graphGrid');
        if (!grid) return;

        grid.innerHTML = '<div class="col-span-full text-center py-12"><span class="loading loading-spinner loading-lg text-primary"></span><p class="mt-4 text-base-content/60">加载图谱中...</p></div>';

        try {
            const response = await fetch('/api/graph/public?page=0&size=20&sortBy=' + currentView);
            const data = await response.json();

            if (data.content && data.content.length > 0) {
                allGraphs = data.content;
                renderGraphs(allGraphs);
                document.getElementById('resultCount').textContent = '共找到 ' + data.totalElements + ' 个图谱';
            } else {
                grid.innerHTML = '<div class="col-span-full text-center py-12"><span class="iconify text-base-content/30" data-icon="heroicons:cube-transparent" data-width="48"></span><p class="mt-4 text-base-content/60">暂无图谱</p></div>';
            }
        } catch (e) {
            grid.innerHTML = '<div class="col-span-full text-center py-12"><span class="iconify text-error" data-icon="heroicons:exclamation-circle" data-width="48"></span><p class="mt-4 text-base-content/60">加载失败，请刷新重试</p></div>';
        }
    }

    function renderGraphs(graphs) {
        const grid = document.getElementById('graphGrid');
        if (!grid) return;

        grid.innerHTML = graphs.map(graph => `
            <div class="card bg-base-100 shadow-soft graph-card fade-in" onclick="viewGraph(${graph.graphId})">
                <figure>
                    <img src="${graph.coverImage || 'https://placehold.co/400x225/eee/999?text=' + encodeURIComponent(graph.name || '图谱')}" alt="${graph.name}" class="graph-card-image">
                </figure>
                <div class="card-body p-4">
                    <div class="flex items-center justify-between mb-2">
                        <span class="badge badge-primary badge-sm domain-badge">${graph.domain || '未分类'}</span>
                        <button class="btn btn-ghost btn-xs favorite-btn ${isFavorited(graph.graphId) ? 'favorited' : ''}" onclick="toggleFavorite(${graph.graphId}, '${(graph.name || graph.graphName || '未命名图谱').replace(/'/g, "\\'")}', event)">
                            <span class="iconify" data-icon="heroicons:heart${isFavorited(graph.graphId) ? '-solid' : ''}" data-width="16"></span>
                        </button>
                    </div>
                    <h3 class="card-title text-base font-semibold line-clamp-1">${graph.name || '未命名图谱'}</h3>
                    <p class="text-sm text-base-content/70 line-clamp-2 mb-3">${graph.description || '暂无描述'}</p>
                    <div class="provider-info mb-2">
                        <span class="iconify" data-icon="heroicons:user" data-width="14"></span>
                        <span>${graph.uploaderName || '匿名'}</span>
                    </div>
                    <div class="stats-info">
                        <span class="flex items-center gap-1" title="节点数">
                            <span class="iconify" data-icon="heroicons:circle" data-width="12"></span>
                            ${graph.nodeCount || 0}
                        </span>
                        <span class="flex items-center gap-1" title="关系数">
                            <span class="iconify" data-icon="heroicons:arrow-right" data-width="12"></span>
                            ${graph.relationCount || 0}
                        </span>
                        <span class="flex items-center gap-1" title="浏览数">
                            <span class="iconify" data-icon="heroicons:eye" data-width="12"></span>
                            ${graph.viewCount || 0}
                        </span>
                        <span class="flex items-center gap-1" title="收藏数">
                            <span class="iconify" data-icon="heroicons:heart" data-width="12"></span>
                            ${graph.collectCount || 0}
                        </span>
                    </div>
                </div>
            </div>
        `).join('');
    }

    function isFavorited(graphId) {
        const favorites = JSON.parse(localStorage.getItem('favorites') || '[]');
        return favorites.some(item => String(item.id) === String(graphId));
    }

    function applyFilters() {
        const keyword = document.getElementById('keywordFilter')?.value.toLowerCase() || '';
        
        // Get range filter values
        const minNodes = parseInt(document.getElementById('minNodesRange')?.value || 0);
        const maxNodes = parseInt(document.getElementById('maxNodesRange')?.value || 10000);
        const minEdges = parseInt(document.getElementById('minEdgesRange')?.value || 0);
        const maxEdges = parseInt(document.getElementById('maxEdgesRange')?.value || 20000);
        const minDensity = parseFloat(document.getElementById('minDensityRange')?.value || 0) / 10;
        const maxDensity = parseFloat(document.getElementById('maxDensityRange')?.value || 100) / 10;
        const minViewCount = parseInt(document.getElementById('minViewCountRange')?.value || 0);
        const maxViewCount = parseInt(document.getElementById('maxViewCountRange')?.value || 30000);
        const minDownloadCount = parseInt(document.getElementById('minDownloadCountRange')?.value || 0);
        const maxDownloadCount = parseInt(document.getElementById('maxDownloadCountRange')?.value || 5000);
        
        const filtered = allGraphs.filter(graph => {
            // Domain filter
            if (currentFilter !== 'all' && graph.domain !== currentFilter) return false;
            
            // Keyword filter
            if (keyword && !graph.name?.toLowerCase().includes(keyword) && !graph.description?.toLowerCase().includes(keyword)) return false;
            
            // Node count filter
            const nodeCount = graph.nodeCount || 0;
            if (nodeCount < minNodes || nodeCount > maxNodes) return false;
            
            // Edge/Relation count filter
            const relationCount = graph.relationCount || 0;
            if (relationCount < minEdges || relationCount > maxEdges) return false;
            
            // Density filter
            const density = graph.density || 0;
            if (density < minDensity || density > maxDensity) return false;
            
            // View count filter
            const viewCount = graph.viewCount || 0;
            if (viewCount < minViewCount || viewCount > maxViewCount) return false;
            
            // Download count filter
            const downloadCount = graph.downloadCount || 0;
            if (downloadCount < minDownloadCount || downloadCount > maxDownloadCount) return false;
            
            return true;
        });
        renderGraphs(filtered);
        document.getElementById('resultCount').textContent = '共找到 ' + filtered.length + ' 个图谱';
    }

    function showSearchSuggestions(query) {
        const suggestions = document.getElementById('searchSuggestions');
        if (!suggestions) return;

        const history = JSON.parse(localStorage.getItem('searchHistory') || '[]');
        const filtered = history.filter(h => h.toLowerCase().includes(query.toLowerCase())).slice(0, 5);

        if (filtered.length > 0) {
            suggestions.innerHTML = filtered.map(item => `<div class="suggestion-item" onclick="selectSuggestion('${item}')">${item}</div>`).join('');
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
        if (!query) return;

        // Save to history
        let history = JSON.parse(localStorage.getItem('searchHistory') || '[]');
        if (!history.includes(query)) {
            history.unshift(query);
            history = history.slice(0, 10);
            localStorage.setItem('searchHistory', JSON.stringify(history));
        }

        hideSearchSuggestions();
        
        // Filter graphs
        const filtered = allGraphs.filter(graph => 
            graph.name?.toLowerCase().includes(query.toLowerCase()) || 
            graph.description?.toLowerCase().includes(query.toLowerCase())
        );
        renderGraphs(filtered);
        document.getElementById('resultCount').textContent = '搜索 "' + query + '" 找到 ' + filtered.length + ' 个图谱';
    };

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

        applyFilters();
    };

    window.toggleView = function(view) {
        currentView = view;
        
        // Update button states
        document.querySelectorAll('.view-toggle').forEach(btn => {
            btn.classList.toggle('active', btn.getAttribute('data-view') === view);
        });

        loadGraphs();
    };

    window.resetFilters = function() {
        currentFilter = 'all';
        document.getElementById('searchInput').value = '';
        document.getElementById('keywordFilter').value = '';
        document.querySelectorAll('.filter-btn').forEach(btn => {
            btn.classList.toggle('active', btn.getAttribute('data-filter') === 'all');
        });
        
        // Reset range sliders with correct display values
        const sliderDefaults = {
            'minNodesRange': { value: 0, display: '0' },
            'maxNodesRange': { value: 10000, display: '10000' },
            'minEdgesRange': { value: 0, display: '0' },
            'maxEdgesRange': { value: 20000, display: '20000' },
            'minDensityRange': { value: 0, display: '0.0' },
            'maxDensityRange': { value: 100, display: '10.0' },
            'minViewCountRange': { value: 0, display: '0' },
            'maxViewCountRange': { value: 30000, display: '30000' },
            'minDownloadCountRange': { value: 0, display: '0' },
            'maxDownloadCountRange': { value: 5000, display: '5000' }
        };
        
        Object.keys(sliderDefaults).forEach(id => {
            const input = document.getElementById(id);
            const valueDisplay = document.getElementById(id.replace('Range', 'Value'));
            if (input) input.value = sliderDefaults[id].value;
            if (valueDisplay) valueDisplay.textContent = sliderDefaults[id].display;
        });

        loadGraphs();
        showNotification('筛选条件已重置', 'info');
    };

    window.viewGraph = function(graphId) {
        window.location.href = '/graph/graph_detail.html?id=' + graphId;
    };

    window.toggleFavorite = async function(graphId, graphName, event) {
        event.stopPropagation();
        
        const btn = event.target.closest('.favorite-btn');
        const icon = btn.querySelector('.iconify');
        const wasFavorited = isFavorited(graphId);
        
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
            let favorites = JSON.parse(localStorage.getItem('favorites') || '[]');
            if (wasFavorited) {
                favorites = favorites.filter(item => String(item.id) !== String(graphId));
                localStorage.setItem('favorites', JSON.stringify(favorites));
                showNotification('已取消收藏', 'info');
            } else {
                favorites.push({ id: String(graphId), name: graphName, addedAt: new Date().toISOString() });
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
