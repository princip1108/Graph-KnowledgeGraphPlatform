/**
 * 图谱 API 服务模块
 * 用于前端调用后端 REST API
 */
(function() {
    'use strict';

    // 初始化全局命名空间
    window.APP_GLOBALS = window.APP_GLOBALS || {};

    // API 基础配置
    const API_BASE = '/api/graph';

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

    function unavailableApi(name) {
        throw new Error(name + ' 接口当前未开放');
    }

    // 通用请求方法
    async function request(url, options = {}) {
        const defaultOptions = {
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            }
        };

        try {
            const response = await fetch(url, { ...defaultOptions, ...options });
            const data = await response.json();
            
            if (!response.ok) {
                throw new Error(data.error || '请求失败');
            }
            
            return data;
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    }

    // 图谱 API 服务
    window.APP_GLOBALS.graphApi = {
        
        // ==================== 图谱 CRUD ====================
        
        /**
         * 创建图谱
         */
        create: async function(graphData) {
            return request(API_BASE, {
                method: 'POST',
                body: JSON.stringify(graphData)
            });
        },

        /**
         * 获取图谱详情
         */
        getById: async function(graphId, incrementView = true) {
            return request(`${API_BASE}/${graphId}?incrementView=${incrementView}`);
        },

        /**
         * 通过分享链接获取图谱
         */
        getByShareLink: async function(shareLink) {
            return request(`${API_BASE}/share/${shareLink}`);
        },

        /**
         * 更新图谱
         */
        update: async function(graphId, graphData) {
            return request(`${API_BASE}/${graphId}`, {
                method: 'PUT',
                body: JSON.stringify(graphData)
            });
        },

        /**
         * 更新图谱状态
         */
        updateStatus: async function(graphId, status) {
            return request(`${API_BASE}/${graphId}`, {
                method: 'PUT',
                body: JSON.stringify({ status })
            });
        },

        /**
         * 删除图谱
         */
        delete: async function(graphId) {
            return request(`${API_BASE}/${graphId}`, {
                method: 'DELETE'
            });
        },

        // ==================== 图谱列表查询 ====================

        /**
         * 获取我的图谱列表
         */
        getMyGraphs: async function(page = 0, size = 10, sortBy = null, status = null) {
            let url = `${API_BASE}/my?page=${page}&size=${size}`;
            if (sortBy) url += `&sortBy=${sortBy}`;
            if (status) url += `&status=${status}`;
            return request(url);
        },

        /**
         * 获取公开图谱列表
         */
        getPublicGraphs: async function(page = 0, size = 12, sortBy = null) {
            let url = `${API_BASE}/public?page=${page}&size=${size}`;
            if (sortBy) url += `&sortBy=${sortBy}`;
            return request(url);
        },

        /**
         * 搜索图谱
         */
        search: async function(keyword, page = 0, size = 12) {
            return request(`${API_BASE}/search?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`);
        },

        /**
         * 获取热门图谱
         */
        getPopular: async function(limit = 10) {
            return request(`${API_BASE}/popular?limit=${limit}`);
        },

        /**
         * 获取用户公开图谱
         */
        getUserGraphs: async function(userId, page = 0, size = 10) {
            return unavailableApi('getUserGraphs');
        },

        // ==================== 节点操作 ====================

        /**
         * 获取图谱节点
         */
        getNodes: async function(graphId) {
            return request(`${API_BASE}/${graphId}/nodes`);
        },

        /**
         * 创建节点
         */
        createNode: async function(graphId, nodeData) {
            return request(`${API_BASE}/${graphId}/nodes`, {
                method: 'POST',
                body: JSON.stringify(nodeData)
            });
        },

        /**
         * 批量创建节点
         */
        createNodes: async function(graphId, nodesData) {
            return request(`${API_BASE}/${graphId}/nodes/batch`, {
                method: 'POST',
                body: JSON.stringify(nodesData)
            });
        },

        /**
         * 搜索节点
         */
        searchNodes: async function(graphId, keyword) {
            return request(`${API_BASE}/${graphId}/nodes/search?keyword=${encodeURIComponent(keyword)}`);
        },

        /**
         * 获取节点邻居
         */
        getNeighbors: async function(graphId, nodeId, direction = 'all') {
            return request(`${API_BASE}/${graphId}/nodes/${nodeId}/neighbors?direction=${direction}`);
        },

        /**
         * 更新节点
         */
        updateNode: async function(graphId, nodeId, nodeData) {
            return request(`${API_BASE}/${graphId}/nodes/${nodeId}`, {
                method: 'PUT',
                body: JSON.stringify(nodeData)
            });
        },

        /**
         * 删除节点
         */
        deleteNode: async function(graphId, nodeId) {
            return request(`${API_BASE}/${graphId}/nodes/${nodeId}`, {
                method: 'DELETE'
            });
        },

        // ==================== 关系操作 ====================

        /**
         * 获取图谱关系
         */
        getRelations: async function(graphId) {
            return request(`${API_BASE}/${graphId}/relations`);
        },

        /**
         * 创建关系
         */
        createRelation: async function(graphId, relationData) {
            return request(`${API_BASE}/${graphId}/relations`, {
                method: 'POST',
                body: JSON.stringify(relationData)
            });
        },

        /**
         * 批量创建关系
         */
        createRelations: async function(graphId, relationsData) {
            return request(`${API_BASE}/${graphId}/relations/batch`, {
                method: 'POST',
                body: JSON.stringify(relationsData)
            });
        },

        /**
         * 获取关系类型统计
         */
        getRelationStats: async function(graphId) {
            return unavailableApi('getRelationStats');
        },

        /**
         * 删除关系
         */
        deleteRelation: async function(graphId, relationId) {
            return request(`${API_BASE}/${graphId}/relations/${relationId}`, {
                method: 'DELETE'
            });
        }
    };

    // ==================== UI 辅助函数 ====================

    /**
     * 判断是否已收藏
     */
    function isFavorited(graphId) {
        var id = String(graphId);
        var favorites = JSON.parse(localStorage.getItem('favorites') || '[]');
        return favorites.some(function(item) { return String(item.id) === id; });
    }

    /**
     * 生成图谱卡片 HTML
     * 收藏按钮统一使用 toggleFavorite(graphId, graphName, event) 调用方式
     */
    window.APP_GLOBALS.renderGraphCard = function(graph) {
        const graphTitle = graph.name || graph.graphName || 'Graph';
        const coverImage = safeImageUrl(graph.coverImage, graphPlaceholderDataUrl(graphTitle));
        const description = graph.description || '暂无描述';
        const nodeCount = graph.nodeCount || 0;
        const viewCount = graph.viewCount || 0;
        const uploadDate = graph.uploadDate || '';
        const graphName = escapeJsString(graphTitle);
        const favorited = isFavorited(graph.graphId);
        
        return `
            <div class="card bg-base-100 shadow-soft hover-lift cursor-pointer academic-border" 
                 onclick="window.location.href='/graph/graph_detail.html?id=${graph.graphId}'">
                <figure class="relative">
                    <img loading="lazy" src="${escapeHtml(coverImage)}" alt="${escapeHtml(graphTitle)}" class="w-full h-44 object-cover">
                    <button class="btn btn-circle btn-sm absolute top-3 right-3 bg-base-100/90 hover:bg-base-100 border-0 shadow-soft favorite-btn ${favorited ? 'favorited' : ''}" 
                            onclick="toggleFavorite(${graph.graphId}, '${graphName}', event)">
                        <span class="iconify" data-icon="heroicons:heart${favorited ? '-solid' : ''}" data-width="16"></span>
                    </button>
                </figure>
                <div class="card-body p-6">
                    <h3 class="card-title text-lg font-semibold mb-3">${escapeHtml(graphTitle)}</h3>
                    <p class="text-sm text-base-content/70 line-clamp-2 leading-relaxed mb-4">${escapeHtml(description)}</p>
                    <div class="flex items-center justify-between text-xs text-base-content/60">
                        <span class="flex items-center gap-1">
                            <span class="iconify" data-icon="heroicons:cube" data-width="14"></span>
                            ${Number.isFinite(Number(nodeCount)) ? Number(nodeCount) : 0} 节点
                        </span>
                        <span class="flex items-center gap-1">
                            <span class="iconify" data-icon="heroicons:eye" data-width="14"></span>
                            ${Number.isFinite(Number(viewCount)) ? Number(viewCount) : 0}
                        </span>
                        <span>${escapeHtml(uploadDate)}</span>
                    </div>
                </div>
            </div>
        `;
    };

    /**
     * 加载并渲染推荐图谱
     */
    window.APP_GLOBALS.loadRecommendedGraphs = async function(containerId, limit = 8) {
        const container = document.getElementById(containerId);
        if (!container) return;

        try {
            // 显示加载状态
            container.innerHTML = '<div class="col-span-full text-center py-8"><span class="loading loading-spinner loading-lg"></span></div>';
            
            const data = await window.APP_GLOBALS.graphApi.getPublicGraphs(0, limit, 'views');
            
            if (data.content && data.content.length > 0) {
                container.innerHTML = data.content.map(graph => 
                    window.APP_GLOBALS.renderGraphCard(graph)
                ).join('');
            } else {
                container.innerHTML = `
                    <div class="col-span-full text-center py-12">
                        <span class="iconify text-6xl text-base-content/30 mb-4" data-icon="heroicons:cube-transparent"></span>
                        <p class="text-base-content/60">暂无图谱数据</p>
                        <p class="text-sm text-base-content/40 mt-2">成为第一个创建图谱的人吧！</p>
                    </div>
                `;
            }
        } catch (error) {
            container.innerHTML = `
                <div class="col-span-full text-center py-12">
                    <span class="iconify text-6xl text-error/50 mb-4" data-icon="heroicons:exclamation-circle"></span>
                    <p class="text-base-content/60">加载失败</p>
                    <button class="btn btn-sm btn-outline mt-4" onclick="APP_GLOBALS.loadRecommendedGraphs('${containerId}', ${limit})">
                        重试
                    </button>
                </div>
            `;
        }
    };

    /**
     * 加载热门图谱
     */
    window.APP_GLOBALS.loadPopularGraphs = async function(containerId, limit = 4) {
        const container = document.getElementById(containerId);
        if (!container) return;

        try {
            const graphs = await window.APP_GLOBALS.graphApi.getPopular(limit);
            
            if (graphs && graphs.length > 0) {
                container.innerHTML = graphs.map(graph => 
                    window.APP_GLOBALS.renderGraphCard(graph)
                ).join('');
            }
        } catch (error) {
            console.error('Failed to load popular graphs:', error);
        }
    };

    console.log('Graph API module loaded');
})();
