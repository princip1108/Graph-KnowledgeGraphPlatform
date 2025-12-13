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
            return request(`${API_BASE}/${graphId}/status`, {
                method: 'PATCH',
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
            return request(`${API_BASE}/user/${userId}?page=${page}&size=${size}`);
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
            return request(`${API_BASE}/${graphId}/relation-stats`);
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
     * 生成图谱卡片 HTML
     */
    window.APP_GLOBALS.renderGraphCard = function(graph) {
        const coverImage = graph.coverImage || 'https://placehold.co/400x225/1E3A8A/FFFFFF?text=Graph';
        const description = graph.description || '暂无描述';
        const nodeCount = graph.nodeCount || 0;
        const viewCount = graph.viewCount || 0;
        const uploadDate = graph.uploadDate || '';
        
        return `
            <div class="card bg-base-100 shadow-soft hover-lift cursor-pointer academic-border" 
                 onclick="window.location.href='/graph/graph_detail.html?id=${graph.graphId}'">
                <figure class="relative">
                    <img loading="lazy" src="${coverImage}" alt="${graph.name}" class="w-full h-44 object-cover">
                    <button class="btn btn-circle btn-sm absolute top-3 right-3 bg-base-100/90 hover:bg-base-100 border-0 shadow-soft" 
                            onclick="event.stopPropagation(); toggleFavorite('${graph.graphId}', this)">
                        <span class="iconify text-base-content/60 hover:text-error transition-colors" 
                              data-icon="heroicons:heart" data-width="16"></span>
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
                            ${viewCount}
                        </span>
                        <span>${uploadDate}</span>
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
