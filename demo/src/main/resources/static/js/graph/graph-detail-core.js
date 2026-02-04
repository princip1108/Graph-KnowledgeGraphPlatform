/**
 * Graph Detail Core Module
 * 图谱详情页核心模块 - 初始化、数据加载、状态管理
 */

(function () {
    'use strict';

    // Graph state management - 全局变量供外部JS访问
    window.currentGraphData = window.currentGraphData || {
        id: null,
        name: '',
        description: '',
        nodes: [],
        edges: [],
        selectedNode: null,
        nodeCount: 0,
        relationCount: 0,
        nodePositions: {}
    };

    window.graphZoom = window.graphZoom || 1;
    window.originalLayout = null; // 保存原始布局用于重置

    // Get graph ID or share link from URL
    function getGraphParamsFromUrl() {
        let url = window.location.href;
        try {
            if (window.parent && window.parent.location) {
                url = window.parent.location.href;
            }
        } catch (e) { /* cross-origin, use current */ }

        const urlParams = new URLSearchParams(url.split('?')[1] || '');
        return {
            id: urlParams.get('id'),
            share: urlParams.get('share')
        };
    }

    // 兼容旧函数
    function getGraphIdFromUrl() {
        return getGraphParamsFromUrl().id;
    }

    // 获取当前图谱ID（优先从 currentGraphData，否则从 URL）
    window.getCurrentGraphId = function () {
        return window.currentGraphData.id || getGraphIdFromUrl();
    };

    // 根据分享链接加载图谱
    async function loadGraphByShareLink(shareLink) {
        try {
            const response = await fetch(`/api/graph/share/${shareLink}`);
            if (!response.ok) {
                throw new Error('分享链接无效');
            }
            const graphInfo = await response.json();
            return await loadGraphData(graphInfo.graphId);
        } catch (e) {
            console.error('加载分享图谱失败:', e);
            window.showNotification('分享链接无效或已过期', 'error');
            return null;
        }
    }

    // Load graph data from API
    async function loadGraphData(graphId) {
        if (!graphId) {
            return null;
        }

        try {
            // Load graph metadata
            const graphResponse = await fetch(`/api/graph/${graphId}?incrementView=true`);
            if (!graphResponse.ok) {
                throw new Error('Failed to load graph');
            }
            const graphInfo = await graphResponse.json();

            // Load nodes and relations via optimized visualization endpoint
            const vizResponse = await fetch(`/api/graph/${graphId}/visualization`);
            const vizData = vizResponse.ok ? await vizResponse.json() : { nodes: [], links: [] };

            const nodesData = { nodes: vizData.nodes || [] };
            const relationsData = { relations: vizData.links || [] };

            // Check edit permission
            let canEdit = false;
            try {
                const permResponse = await fetch(`/api/graph/${graphId}/can-edit`, { credentials: 'include' });
                if (permResponse.ok) {
                    const permData = await permResponse.json();
                    canEdit = permData.canEdit === true;
                }
            } catch (e) {
                // Permission check failed, defaulting to no edit
            }

            return {
                id: graphId,
                name: graphInfo.name || '未命名图谱',
                description: graphInfo.description || '',
                nodes: nodesData.nodes || [],
                edges: relationsData.relations || [],
                nodeCount: graphInfo.nodeCount || 0,
                relationCount: graphInfo.relationCount || 0,
                viewCount: graphInfo.viewCount || 0,
                collectCount: graphInfo.collectCount || 0,
                shareLink: graphInfo.shareLink || '',
                canEdit: canEdit,
                uploaderId: graphInfo.uploaderId,
                uploaderName: graphInfo.uploaderName,
                uploaderAvatar: graphInfo.uploaderAvatar
            };
        } catch (error) {
            console.error('Error loading graph data:', error);
            window.showNotification('加载图谱数据失败', 'error');
            return null;
        }
    }
    window.loadGraphData = loadGraphData;

    // Update page UI with graph data
    function updatePageWithGraphData(data) {
        if (!data) return;

        Object.assign(window.currentGraphData, data);

        // Update title
        const titleEl = document.getElementById('graphTitle');
        if (titleEl) titleEl.textContent = data.name || '未命名图谱';

        // Update description
        const descEl = document.getElementById('graphDescription');
        if (descEl) descEl.textContent = data.description || '暂无描述';

        // Update tags container
        updateGraphTags(data);

        // Update entity type filters from actual data
        updateEntityTypeFiltersFromData(data.nodes || []);

        // Update relationship type filters from actual data
        updateRelationshipTypeFiltersFromData(data.edges || []);

        // Update search type filter options
        if (window.updateSearchTypeFilter) {
            window.updateSearchTypeFilter(data.nodes || []);
        }

        // Update statistics
        updateStatistics(data);

        // Show/hide node management section based on edit permission
        const nodeManagementSection = document.getElementById('nodeManagementSection');
        if (nodeManagementSection) {
            nodeManagementSection.style.display = data.canEdit ? 'block' : 'none';
        }

        // Update uploader info
        updateUploaderInfo(data);
    }
    window.updatePageWithGraphData = updatePageWithGraphData;

    // Update uploader info
    function updateUploaderInfo(data) {
        const uploaderNameEl = document.getElementById('uploaderName');
        const uploaderAvatarEl = document.getElementById('uploaderAvatar');
        const uploaderLink = document.getElementById('uploaderLink');

        if (data.uploaderName) {
            if (uploaderNameEl) uploaderNameEl.textContent = data.uploaderName;
        }

        if (uploaderAvatarEl) {
            if (data.uploaderAvatar) {
                uploaderAvatarEl.innerHTML = '<img src="' + data.uploaderAvatar + '" class="w-full h-full rounded-full object-cover" alt="" />';
            } else if (data.uploaderName) {
                uploaderAvatarEl.textContent = data.uploaderName.charAt(0).toUpperCase();
            }
        }

        if (data.uploaderId && uploaderLink) {
            uploaderLink.href = '/user/profile.html?id=' + data.uploaderId;
        }
    }

    // Create post for this graph
    window.createPostForGraph = function () {
        const graphId = getCurrentGraphId();
        const graphName = document.getElementById('graphTitle')?.textContent || '';
        window.top.location.href = '/community/post_edit.html?graphId=' + graphId + '&graphName=' + encodeURIComponent(graphName);
    };

    // Update graph tags dynamically
    function updateGraphTags(data) {
        const tagsContainer = document.getElementById('graphTags');
        if (!tagsContainer) return;

        const nodeTypes = new Set();
        (data.nodes || []).forEach(node => {
            if (node.type) nodeTypes.add(node.type);
        });

        if (nodeTypes.size === 0) {
            tagsContainer.innerHTML = '<div class="badge badge-ghost badge-sm">暂无标签</div>';
            return;
        }

        const colors = ['primary', 'secondary', 'accent', 'info'];
        let html = '';
        let colorIndex = 0;
        nodeTypes.forEach(type => {
            html += `<div class="badge badge-${colors[colorIndex % colors.length]} badge-sm">${type}</div>`;
            colorIndex++;
        });
        tagsContainer.innerHTML = html;
    }

    // Update entity type filters from actual node data
    function updateEntityTypeFiltersFromData(nodes) {
        const container = document.getElementById('entityTypeFilters');
        if (!container) return;

        const typeCounts = {};
        nodes.forEach(node => {
            const type = node.type || '未分类';
            typeCounts[type] = (typeCounts[type] || 0) + 1;
        });

        if (Object.keys(typeCounts).length === 0) {
            container.innerHTML = '<div class="text-sm text-base-content/50">暂无节点类型数据</div>';
            return;
        }

        let html = '';
        Object.entries(typeCounts).forEach(([type, count]) => {
            html += `<label class="label cursor-pointer justify-start gap-3 py-1">
                <input type="checkbox" checked data-entity-type="${type}" class="checkbox checkbox-primary checkbox-sm entity-type-checkbox">
                <span class="label-text text-sm">${type} (${count})</span>
            </label>`;
        });
        container.innerHTML = html;

        container.querySelectorAll('.entity-type-checkbox').forEach(cb => {
            cb.addEventListener('change', updateNodeVisibilityByType);
        });
    }

    // Update relationship type filters from actual edge data
    function updateRelationshipTypeFiltersFromData(edges) {
        const container = document.getElementById('relationTypeFilters');
        if (!container) return;

        const typeCounts = {};
        edges.forEach(edge => {
            const type = edge.type || '关联';
            typeCounts[type] = (typeCounts[type] || 0) + 1;
        });

        if (Object.keys(typeCounts).length === 0) {
            container.innerHTML = '<div class="text-sm text-base-content/50">暂无关系类型数据</div>';
            return;
        }

        let html = '';
        Object.entries(typeCounts).forEach(([type, count]) => {
            html += `<label class="label cursor-pointer justify-start gap-3 py-1">
                <input type="checkbox" checked data-relation-type="${type}" class="checkbox checkbox-secondary checkbox-sm relation-type-checkbox">
                <span class="label-text text-sm">${type} (${count})</span>
            </label>`;
        });
        container.innerHTML = html;

        container.querySelectorAll('.relation-type-checkbox').forEach(cb => {
            cb.addEventListener('change', updateEdgeVisibilityByType);
        });
    }

    // Update node visibility based on type filters
    function updateNodeVisibilityByType() {
        const checkedTypes = [];
        document.querySelectorAll('.entity-type-checkbox:checked').forEach(cb => {
            checkedTypes.push(cb.getAttribute('data-entity-type'));
        });

        document.querySelectorAll('.graph-node').forEach(node => {
            const nodeType = node.getAttribute('data-node-type') || '未分类';
            node.style.opacity = checkedTypes.includes(nodeType) ? '1' : '0.2';
        });
    }

    // Update edge visibility based on type filters
    function updateEdgeVisibilityByType() {
        const checkedTypes = [];
        document.querySelectorAll('.relation-type-checkbox:checked').forEach(cb => {
            checkedTypes.push(cb.getAttribute('data-relation-type'));
        });

        const highlightedNodeIds = new Set();

        document.querySelectorAll('.graph-edge').forEach(edge => {
            const edgeType = edge.getAttribute('data-edge-type') || '关联';
            const isVisible = checkedTypes.includes(edgeType);
            edge.style.opacity = isVisible ? '0.7' : '0.1';

            if (isVisible) {
                const sourceId = edge.getAttribute('data-edge-source');
                const targetId = edge.getAttribute('data-edge-target');
                if (sourceId) highlightedNodeIds.add(sourceId);
                if (targetId) highlightedNodeIds.add(targetId);
            }
        });

        document.querySelectorAll('.edge-label').forEach(label => {
            const sourceId = label.getAttribute('data-label-source');
            const targetId = label.getAttribute('data-label-target');
            const edge = document.querySelector(`[data-edge-source="${sourceId}"][data-edge-target="${targetId}"]`);
            if (edge) {
                const edgeType = edge.getAttribute('data-edge-type') || '关联';
                label.style.opacity = checkedTypes.includes(edgeType) ? '1' : '0.1';
            }
        });

        document.querySelectorAll('.graph-node').forEach(node => {
            const nodeId = node.getAttribute('data-node-id');
            node.style.opacity = highlightedNodeIds.has(nodeId) ? '1' : '0.2';
        });

        document.querySelectorAll('[data-label-for]').forEach(label => {
            const nodeId = label.getAttribute('data-label-for');
            label.style.opacity = highlightedNodeIds.has(nodeId) ? '1' : '0.2';
        });
    }

    // Update statistics display
    function updateStatistics(data) {
        const stats = document.querySelectorAll('.stat .stat-value');
        if (stats.length >= 4) {
            stats[0].textContent = data.nodeCount || 0;
            stats[1].textContent = data.relationCount || 0;
            const avgDegree = data.nodeCount > 0 ? ((data.relationCount * 2) / data.nodeCount).toFixed(1) : '0';
            stats[2].textContent = avgDegree;
            const maxEdges = data.nodeCount * (data.nodeCount - 1) / 2;
            const density = maxEdges > 0 ? (data.relationCount / maxEdges).toFixed(2) : '0';
            stats[3].textContent = density;
        }
    }

    // Graph initialization
    function initializeGraph() {
        setTimeout(() => {
            const canvas = document.getElementById('graphCanvas');

            if (window.currentGraphData.nodes && window.currentGraphData.nodes.length > 0) {
                if (window.bindaoRenderGraph) {
                    window.bindaoRenderGraph();
                }
            } else if (!window.currentGraphData.id) {
                canvas.innerHTML = `
                    <div class="absolute inset-0 flex items-center justify-center">
                        <div class="text-center">
                            <div class="w-16 h-16 bg-warning/20 rounded-full flex items-center justify-center mb-4 mx-auto">
                                <span class="iconify text-warning" data-icon="heroicons:exclamation-triangle" data-width="32"></span>
                            </div>
                            <p class="text-base-content font-medium">未指定图谱</p>
                            <p class="text-sm text-base-content/70 mt-2">请从图谱列表选择一个图谱查看</p>
                            <button class="btn btn-primary btn-sm mt-4" onclick="navigateTo('graph_list_page')">浏览图谱</button>
                        </div>
                    </div>
                `;
            } else {
                canvas.innerHTML = `
                    <div class="absolute inset-0 flex items-center justify-center">
                        <div class="text-center">
                            <div class="w-16 h-16 bg-primary/20 rounded-full flex items-center justify-center mb-4 mx-auto">
                                <span class="iconify text-primary" data-icon="heroicons:cpu-chip" data-width="32"></span>
                            </div>
                            <p class="text-base-content font-medium">图谱加载完成</p>
                            <p class="text-sm text-base-content/70 mt-2">该图谱暂无节点数据</p>
                        </div>
                    </div>
                `;
            }
        }, 500);
    }

    // Initialize page
    document.addEventListener('DOMContentLoaded', async function () {
        const params = getGraphParamsFromUrl();
        let data = null;

        if (params.share) {
            data = await loadGraphByShareLink(params.share);
        } else if (params.id) {
            data = await loadGraphData(params.id);
        }

        if (data) {
            updatePageWithGraphData(data);
        }

        initializeGraph();

        // Initialize other modules if available
        if (window.updateFavoriteButton) window.updateFavoriteButton();
        if (window.setupSearchFunctionality) window.setupSearchFunctionality();
        if (window.setupViewControls) window.setupViewControls();
        if (window.initializeChart) window.initializeChart();
        if (window.checkAndShowEditSection) window.checkAndShowEditSection();
        if (window.updateSearchTypeFilter) window.updateSearchTypeFilter();
    });

})();

