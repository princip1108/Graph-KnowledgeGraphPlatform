
(function() {
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
        isCustomCover: false
    };
    var currentGraphData = window.currentGraphData;

    window.graphZoom = window.graphZoom || 1;
    var graphZoom = window.graphZoom;
    let graphPan = { x: 0, y: 0 };
    let isFullscreen = false;
    let statisticsCollapsed = false;
    let currentChartType = 'pie';

    // Chart type mapping
    const chartTypes = {
        'nodeTypePie': { name: '节点类型分布饼状图' },
        'relationTypePie': { name: '关系类型分布饼状图' },
        'coreNodes': { name: '核心节点排行榜' },
        'stats': { name: '图谱基础统计' },
        'thumbnail': { name: '图谱缩略图' }
    };

    // Get graph ID or share link from URL
    function getGraphParamsFromUrl() {
        // Check parent window URL first (iframe case)
        let url = window.location.href;
        try {
            if (window.parent && window.parent.location) {
                url = window.parent.location.href;
            }
        } catch(e) { /* cross-origin, use current */ }
        
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
    window.getCurrentGraphId = function() {
        return currentGraphData.id || getGraphIdFromUrl();
    };
    
    // 根据分享链接加载图谱
    async function loadGraphByShareLink(shareLink) {
        try {
            const response = await fetch(`/api/graph/share/${shareLink}`);
            if (!response.ok) {
                throw new Error('分享链接无效');
            }
            const graphInfo = await response.json();
            // 使用获取到的 graphId 加载完整数据
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
            
            // Load nodes
            const nodesResponse = await fetch(`/api/graph/${graphId}/nodes`);
            const nodesData = nodesResponse.ok ? await nodesResponse.json() : { nodes: [] };
            
            // Load relations
            const relationsResponse = await fetch(`/api/graph/${graphId}/relations`);
            const relationsData = relationsResponse.ok ? await relationsResponse.json() : { relations: [] };
            
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
                uploaderAvatar: graphInfo.uploaderAvatar,
                isCustomCover: graphInfo.isCustomCover || false
            };
        } catch (error) {
            console.error('Error loading graph data:', error);
            window.showNotification('加载图谱数据失败', 'error');
            return null;
        }
    }

    // Update page UI with graph data
    function updatePageWithGraphData(data) {
        if (!data) return;
        
        Object.assign(window.currentGraphData, data);
        currentGraphData = window.currentGraphData;
        
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
        updateSearchTypeFilter(data.nodes || []);
        
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
    
    // Update uploader info
    function updateUploaderInfo(data) {
        const uploaderNameEl = document.getElementById('uploaderName');
        const uploaderAvatarEl = document.getElementById('uploaderAvatar');
        const uploaderLink = document.getElementById('uploaderLink');
        
        if (data.uploaderName) {
            if (uploaderNameEl) uploaderNameEl.textContent = data.uploaderName;
        }
        
        // 更新头像：如果有头像URL则显示图片，否则显示首字母
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
    window.createPostForGraph = function() {
        const graphId = getCurrentGraphId();
        const graphName = document.getElementById('graphTitle')?.textContent || '';
        window.top.location.href = '/community/post_edit.html?graphId=' + graphId + '&graphName=' + encodeURIComponent(graphName);
    }
    
    // Update search type filter options
    function updateSearchTypeFilter(nodes) {
        const typeSelect = document.getElementById('searchTypeFilter');
        if (!typeSelect) return;
        
        const types = [...new Set(nodes.map(n => n.type || '未分类'))];
        typeSelect.innerHTML = '<option value="">全部类型</option>' + 
            types.map(t => `<option value="${t}">${t}</option>`).join('');
    }
    
    // Update graph tags dynamically
    function updateGraphTags(data) {
        const tagsContainer = document.getElementById('graphTags');
        if (!tagsContainer) return;
        
        // 从节点类型中提取标签
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
        
        // 统计每种类型的节点数量
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
        
        // 重新绑定事件
        container.querySelectorAll('.entity-type-checkbox').forEach(cb => {
            cb.addEventListener('change', updateNodeVisibilityByType);
        });
    }
    
    // Update relationship type filters from actual edge data
    function updateRelationshipTypeFiltersFromData(edges) {
        const container = document.getElementById('relationTypeFilters');
        if (!container) return;
        
        // 统计每种关系类型的数量
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
        
        // 重新绑定事件
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
        
        // 收集选中关系类型两端的节点ID
        const highlightedNodeIds = new Set();
        
        document.querySelectorAll('.graph-edge').forEach(edge => {
            const edgeType = edge.getAttribute('data-edge-type') || '关联';
            const isVisible = checkedTypes.includes(edgeType);
            edge.style.opacity = isVisible ? '0.7' : '0.1';
            
            // 如果边可见，记录两端节点
            if (isVisible) {
                const sourceId = edge.getAttribute('data-edge-source');
                const targetId = edge.getAttribute('data-edge-target');
                if (sourceId) highlightedNodeIds.add(sourceId);
                if (targetId) highlightedNodeIds.add(targetId);
            }
        });
        
        // 更新边标签透明度
        document.querySelectorAll('.edge-label').forEach(label => {
            const sourceId = label.getAttribute('data-label-source');
            const targetId = label.getAttribute('data-label-target');
            const edge = document.querySelector(`[data-edge-source="${sourceId}"][data-edge-target="${targetId}"]`);
            if (edge) {
                const edgeType = edge.getAttribute('data-edge-type') || '关联';
                label.style.opacity = checkedTypes.includes(edgeType) ? '1' : '0.1';
            }
        });
        
        // 高亮关系两端的节点
        document.querySelectorAll('.graph-node').forEach(node => {
            const nodeId = node.getAttribute('data-node-id');
            node.style.opacity = highlightedNodeIds.has(nodeId) ? '1' : '0.2';
        });
        
        // 更新节点标签透明度
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
            // Calculate average degree
            const avgDegree = data.nodeCount > 0 ? ((data.relationCount * 2) / data.nodeCount).toFixed(1) : '0';
            stats[2].textContent = avgDegree;
            // Calculate density
            const maxEdges = data.nodeCount * (data.nodeCount - 1) / 2;
            const density = maxEdges > 0 ? (data.relationCount / maxEdges).toFixed(2) : '0';
            stats[3].textContent = density;
        }
    }

    // Initialize page
    document.addEventListener('DOMContentLoaded', async function() {
        const params = getGraphParamsFromUrl();
        let data = null;
        
        // 优先处理分享链接
        if (params.share) {
            data = await loadGraphByShareLink(params.share);
        } else if (params.id) {
            data = await loadGraphData(params.id);
        }
        
        if (data) {
            updatePageWithGraphData(data);
        }
        
        initializeGraph();
        updateFavoriteButton();
        setupSearchFunctionality();
        setupViewControls();
        setupEntityTypeFilters();
        setupRelationshipFilters();
        initializeChart();
        checkAndShowEditSection();
        updateSearchTypeFilter();
        
        // 侧边栏拖拽已通过 IIFE 自动初始化
    });

// Chart selection function
    window.selectChart = function(chartType) {
        currentChartType = chartType;
        const chart = chartTypes[chartType];
        if (!chart) return;
        
        document.getElementById('selectedChartType').textContent = chart.name;
        renderChart(chartType);
        document.activeElement.blur();
    };

    // Render chart based on type
    function renderChart(chartType) {
        const container = document.getElementById('chartContainer');
        const nodes = currentGraphData.nodes || [];
        const edges = currentGraphData.edges || [];
        
        switch(chartType) {
            case 'nodeTypePie':
                renderNodeTypePie(container, nodes);
                break;
            case 'relationTypePie':
                renderRelationTypePie(container, edges);
                break;
            case 'coreNodes':
                renderCoreNodes(container, nodes);
                break;
            case 'stats':
                renderStats(container, nodes, edges);
                break;
            case 'thumbnail':
                renderThumbnail(container);
                break;
            default:
                container.innerHTML = '<div class="text-center text-base-content/50">未知图表类型</div>';
        }
    }

    // 1. 节点类型分布饼状图
    function renderNodeTypePie(container, nodes) {
        const typeCount = {};
        nodes.forEach(function(n) {
            const type = n.type || '默认';
            typeCount[type] = (typeCount[type] || 0) + 1;
        });
        
        const data = Object.entries(typeCount).map(function(entry) {
            return { name: entry[0], value: entry[1], color: getNodeColor(entry[0]) };
        });
        
        if (data.length === 0) {
            container.innerHTML = '<div class="flex items-center justify-center h-full text-base-content/50">暂无节点数据</div>';
            return;
        }
        
        const total = nodes.length;
        
        // 获取容器尺寸，计算自适应布局
        const containerWidth = container.offsetWidth || 400;
        const containerHeight = container.offsetHeight || 150;
        const pieSize = 96; // 饼图尺寸
        const padding = 32; // 内边距
        const gap = 16; // 间距
        
        // 可用于图例的宽度和高度
        const legendWidth = containerWidth - pieSize - padding - gap;
        const legendHeight = containerHeight - padding;
        
        // 估算每个图例项的尺寸
        const itemWidth = 120; // 每个图例项估算宽度
        const itemHeight = 20; // 每个图例项高度
        
        // 计算最大列数和行数
        const maxColumns = Math.max(1, Math.floor(legendWidth / itemWidth));
        const maxRows = Math.max(1, Math.floor(legendHeight / itemHeight));
        const maxVisibleItems = maxColumns * maxRows;
        
        // 根据数据量和可用空间计算实际列数
        const columnCount = Math.min(maxColumns, Math.max(1, Math.ceil(data.length / maxRows)));
        const needScroll = data.length > maxVisibleItems;
        
        let html = '<div class="flex items-center justify-center h-full gap-4 p-4">';
        html += '<div class="grid gap-x-3 gap-y-1' + (needScroll ? ' overflow-y-auto' : '') + '" style="grid-template-columns: repeat(' + columnCount + ', auto);' + (needScroll ? ' max-height: ' + legendHeight + 'px;' : '') + '">';
        data.forEach(function(item) {
            const percent = ((item.value / total) * 100).toFixed(1);
            html += '<div class="flex items-center gap-1 text-xs whitespace-nowrap">';
            html += '<div class="w-2 h-2 rounded-full flex-shrink-0" style="background-color: ' + item.color + ';"></div>';
            html += '<span class="truncate" style="max-width: 80px;">' + item.name + '</span>';
            html += '<span class="text-base-content/50">' + item.value + '(' + percent + '%)</span>';
            html += '</div>';
        });
        html += '</div>';
        html += '<div class="relative flex-shrink-0" style="width: ' + pieSize + 'px; height: ' + pieSize + 'px;">';
        html += renderPieSVG(data, total);
        html += '</div>';
        html += '</div>';
        container.innerHTML = html;
    }
    
    // SVG饼图渲染
    function renderPieSVG(data, total) {
        let svg = '<svg viewBox="0 0 100 100" class="w-full h-full">';
        let startAngle = 0;
        data.forEach(function(item) {
            const angle = (item.value / total) * 360;
            const endAngle = startAngle + angle;
            const x1 = 50 + 40 * Math.cos((startAngle - 90) * Math.PI / 180);
            const y1 = 50 + 40 * Math.sin((startAngle - 90) * Math.PI / 180);
            const x2 = 50 + 40 * Math.cos((endAngle - 90) * Math.PI / 180);
            const y2 = 50 + 40 * Math.sin((endAngle - 90) * Math.PI / 180);
            const largeArc = angle > 180 ? 1 : 0;
            svg += '<path d="M50,50 L' + x1 + ',' + y1 + ' A40,40 0 ' + largeArc + ',1 ' + x2 + ',' + y2 + ' Z" fill="' + item.color + '"/>';
            startAngle = endAngle;
        });
        svg += '</svg>';
        return svg;
    }

    // 2. 关系类型分布饼状图
    function renderRelationTypePie(container, edges) {
        const typeCount = {};
        edges.forEach(function(e) {
            const type = e.type || '关联';
            typeCount[type] = (typeCount[type] || 0) + 1;
        });
        
        const colors = ['#6366f1', '#8b5cf6', '#ec4899', '#f43f5e', '#f97316', '#eab308', '#22c55e', '#14b8a6'];
        const data = Object.entries(typeCount).map(function(entry, i) {
            return { name: entry[0], value: entry[1], color: colors[i % colors.length] };
        });
        
        if (data.length === 0) {
            container.innerHTML = '<div class="flex items-center justify-center h-full text-base-content/50">暂无关系数据</div>';
            return;
        }
        
        const total = edges.length;
        let html = '<div class="flex flex-col h-full p-3 overflow-hidden">';
        html += '<div class="flex items-center justify-center mb-2">';
        html += '<div class="w-24 h-24 flex-shrink-0">';
        html += renderPieSVG(data, total);
        html += '</div>';
        html += '</div>';
        html += '<div class="flex flex-wrap gap-x-3 gap-y-1 justify-center overflow-y-auto">';
        data.forEach(function(item) {
            const percent = ((item.value / total) * 100).toFixed(1);
            html += '<div class="flex items-center gap-1 text-xs whitespace-nowrap">';
            html += '<div class="w-2 h-2 rounded-full flex-shrink-0" style="background-color: ' + item.color + ';"></div>';
            html += '<span>' + item.name + '</span>';
            html += '<span class="text-base-content/50">' + item.value + '(' + percent + '%)</span>';
            html += '</div>';
        });
        html += '</div>';
        html += '</div>';
        container.innerHTML = html;
    }

    // 3. 核心节点排行榜
    function renderCoreNodes(container, nodes) {
        const edges = currentGraphData.edges || [];
        
        // 根据边计算每个节点的度数
        const degreeMap = {};
        nodes.forEach(function(n) { degreeMap[n.nodeId] = 0; });
        edges.forEach(function(e) {
            if (degreeMap[e.sourceNodeId] !== undefined) degreeMap[e.sourceNodeId]++;
            if (degreeMap[e.targetNodeId] !== undefined) degreeMap[e.targetNodeId]++;
        });
        
        // 将度数附加到节点并排序
        const nodesWithDegree = nodes.map(function(n) {
            return { ...n, calculatedDegree: degreeMap[n.nodeId] || 0 };
        });
        
        const sorted = nodesWithDegree.sort(function(a, b) {
            return b.calculatedDegree - a.calculatedDegree;
        }).slice(0, 10);
        
        if (sorted.length === 0) {
            container.innerHTML = '<div class="flex items-center justify-center h-full text-base-content/50">暂无节点数据</div>';
            return;
        }
        
        const maxDegree = sorted[0].calculatedDegree || 1;
        let html = '<div class="p-4 overflow-y-auto h-full">';
        html += '<div class="text-xs text-base-content/50 mb-2">按连接数排序 (Top 10)</div>';
        sorted.forEach(function(n, i) {
            const degree = n.calculatedDegree;
            const percent = (degree / maxDegree) * 100;
            html += '<div class="flex items-center gap-2 mb-2">';
            html += '<span class="w-5 text-xs text-base-content/50">' + (i + 1) + '</span>';
            html += '<div class="flex-1">';
            html += '<div class="flex justify-between text-xs mb-1">';
            html += '<span class="truncate max-w-[120px]">' + (n.name || '未命名') + '</span>';
            html += '<span class="text-base-content/50">' + degree + '</span>';
            html += '</div>';
            html += '<div class="h-1.5 bg-base-200 rounded-full overflow-hidden">';
            html += '<div class="h-full bg-primary rounded-full" style="width: ' + percent + '%"></div>';
            html += '</div>';
            html += '</div>';
            html += '</div>';
        });
        html += '</div>';
        container.innerHTML = html;
    }

    // 4. 图谱基础统计
    function renderStats(container, nodes, edges) {
        const nodeCount = nodes.length;
        const edgeCount = edges.length;
        const avgDegree = nodeCount > 0 ? ((edgeCount * 2) / nodeCount).toFixed(1) : 0;
        const typeCount = new Set(nodes.map(function(n) { return n.type || '默认'; })).size;
        
        let html = '<div class="grid grid-cols-2 gap-4 p-4 h-full content-center">';
        html += '<div class="bg-base-200 rounded-lg p-3 text-center">';
        html += '<div class="text-2xl font-bold text-primary">' + nodeCount + '</div>';
        html += '<div class="text-xs text-base-content/50">节点数量</div>';
        html += '</div>';
        html += '<div class="bg-base-200 rounded-lg p-3 text-center">';
        html += '<div class="text-2xl font-bold text-secondary">' + edgeCount + '</div>';
        html += '<div class="text-xs text-base-content/50">关系数量</div>';
        html += '</div>';
        html += '<div class="bg-base-200 rounded-lg p-3 text-center">';
        html += '<div class="text-2xl font-bold text-accent">' + avgDegree + '</div>';
        html += '<div class="text-xs text-base-content/50">平均度数</div>';
        html += '</div>';
        html += '<div class="bg-base-200 rounded-lg p-3 text-center">';
        html += '<div class="text-2xl font-bold text-info">' + typeCount + '</div>';
        html += '<div class="text-xs text-base-content/50">节点类型</div>';
        html += '</div>';
        html += '</div>';
        container.innerHTML = html;
    }

    // 5. 图谱缩略图 - 与主图谱布局一致
    function renderThumbnail(container) {
        const nodes = currentGraphData.nodes || [];
        const edges = currentGraphData.edges || [];
        const mainPositions = currentGraphData.nodePositions || {};
        
        if (nodes.length === 0) {
            container.innerHTML = '<div class="flex items-center justify-center h-full text-base-content/50">暂无图谱数据</div>';
            return;
        }
        
        // 使用主图谱的布局位置，如果没有则使用圆形布局
        let nodePositions = {};
        const hasMainPositions = Object.keys(mainPositions).length > 0;
        const thumbWidth = 280, thumbHeight = 160;
        
        if (hasMainPositions) {
            // 计算主图谱的边界
            const xs = Object.values(mainPositions).map(function(p) { return p.x; });
            const ys = Object.values(mainPositions).map(function(p) { return p.y; });
            const minX = Math.min.apply(null, xs), maxX = Math.max.apply(null, xs);
            const minY = Math.min.apply(null, ys), maxY = Math.max.apply(null, ys);
            const graphWidth = maxX - minX || 1;
            const graphHeight = maxY - minY || 1;
            
            // 缩略图边距
            const padding = 15;
            const availWidth = thumbWidth - padding * 2;
            const availHeight = thumbHeight - padding * 2;
            
            // 计算缩放比例（保持宽高比）
            const scale = Math.min(availWidth / graphWidth, availHeight / graphHeight);
            
            // 缩放并居中
            const offsetX = padding + (availWidth - graphWidth * scale) / 2;
            const offsetY = padding + (availHeight - graphHeight * scale) / 2;
            
            nodes.forEach(function(n) {
                const pos = mainPositions[n.nodeId];
                if (pos) {
                    nodePositions[n.nodeId] = {
                        x: (pos.x - minX) * scale + offsetX,
                        y: (pos.y - minY) * scale + offsetY
                    };
                }
            });
        } else {
            // 回退到圆形布局
            const centerX = thumbWidth / 2, centerY = thumbHeight / 2;
            const radius = Math.min(thumbWidth, thumbHeight) / 2 - 20;
            nodes.forEach(function(n, i) {
                const angle = (2 * Math.PI * i) / nodes.length;
                nodePositions[n.nodeId] = {
                    x: centerX + radius * Math.cos(angle),
                    y: centerY + radius * Math.sin(angle)
                };
            });
        }
        
        let svg = '<svg viewBox="0 0 ' + thumbWidth + ' ' + thumbHeight + '" class="w-full h-full">';
        
        // 绘制边
        edges.forEach(function(e) {
            const src = nodePositions[e.sourceNodeId];
            const tgt = nodePositions[e.targetNodeId];
            if (src && tgt) {
                svg += '<line x1="' + src.x + '" y1="' + src.y + '" x2="' + tgt.x + '" y2="' + tgt.y + '" stroke="#6b7280" stroke-width="0.5" opacity="0.4"/>';
            }
        });
        
        // 绘制节点（使用类型颜色）
        nodes.forEach(function(n) {
            const pos = nodePositions[n.nodeId];
            if (pos) {
                const color = getNodeColor(n.type || 'default');
                svg += '<circle cx="' + pos.x + '" cy="' + pos.y + '" r="3" fill="' + color + '"/>';
            }
        });
        
        svg += '</svg>';
        
        // 添加节点数和关系数标注
        const statsHtml = '<div class="text-center text-xs text-base-content/60 mt-1">' +
            '<span class="mr-3">节点: ' + nodes.length + '</span>' +
            '<span>关系: ' + edges.length + '</span>' +
            '</div>';
        
        container.innerHTML = '<div class="p-2 h-full flex flex-col">' +
            '<div class="flex-1">' + svg + '</div>' +
            statsHtml +
            '</div>';
    }

    // Initialize chart display
    function initializeChart() {
        currentChartType = 'nodeTypePie';
        document.getElementById('selectedChartType').textContent = chartTypes[currentChartType].name;
        renderChart(currentChartType);
    }

    // Graph initialization
    function initializeGraph() {
        // Use real data if available, otherwise show placeholder
        setTimeout(() => {
            const canvas = document.getElementById('graphCanvas');
            
            if (currentGraphData.nodes && currentGraphData.nodes.length > 0) {
                bindaoRenderGraph();
            } else if (!currentGraphData.id) {
                // No graph ID, show demo graph
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

    // ==================== 布局和渲染已移至外部 JS 文件 ====================
    // graph-bindao.js: 布局算法 (layoutGraph, LAYOUT_CONFIG 等)
    // graph-bindao-bindao.js: 渲染和交互 (bindaoRenderGraph, 拖动, 缩放等)
    
    const NODE_RADIUS = 14;

    // ==================== 布局/渲染代码已移至外部 JS ====================
    // graph-bindao.js: 布局算法 (window.layoutGraph)
    // graph-bindao-bindao.js: 渲染和交互 (window.bindaoRenderGraph)

    // ==================== 拖动/平移/颜色/选择等功能已移至外部 JS ====================
    // 由 graph-bindao-bindao.js 提供: startDrag, onDrag, endDrag, initCanvasPanning,
    // getNodeColor, selectNode, clearNodeDetails, resetView, zoomIn, zoomOut

    // Update node details panel (legacy - now using right sidebar)
    function updateNodeDetails(node) {
        // 此函数保留用于兼容，实际显示在右侧详情栏
    }

    // 从实际边数据获取相关节点
    function getRelatedNodesFromData(nodeId) {
        const edges = currentGraphData.edges || [];
        const nodes = currentGraphData.nodes || [];
        const relatedIds = new Set();
        
        edges.forEach(edge => {
            if (edge.sourceNodeId === nodeId) {
                relatedIds.add(edge.targetNodeId);
    }
            if (edge.targetNodeId === nodeId) {
                relatedIds.add(edge.sourceNodeId);
            }
        });
        
        return nodes.filter(n => relatedIds.has(n.nodeId)).map(n => ({
            id: n.nodeId,
            name: n.name || '未命名'
        }));
    }

    // 获取节点连接数
    function getNodeConnectionCount(nodeId) {
        const edges = currentGraphData.edges || [];
        return edges.filter(edge => 
            edge.sourceNodeId === nodeId || edge.targetNodeId === nodeId
        ).length;
    }

    // 高亮指定节点
    window.highlightNode = function(nodeId) {
        clearSearchHighlight();
        const node = document.querySelector(`[data-node-id="${nodeId}"]`);
        if (node) {
            node.style.stroke = 'var(--color-warning)';
            node.style.strokeWidth = '4px';
            
            // 滚动到该节点（如果需要）
            node.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    };

    // Search functionality
    function setupSearchFunctionality() {
        const searchInput = document.getElementById('graphSearchInput');
        searchInput.addEventListener('input', handleSearch);
        searchInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                handleSearch();
            }
        });
    }

    // 更新搜索类型筛选下拉框
    function updateSearchTypeFilter() {
        const nodes = currentGraphData.nodes || [];
        const types = [...new Set(nodes.map(n => n.type || '未分类'))];
        
        const typeSelect = document.getElementById('searchTypeFilter');
        if (typeSelect) {
            typeSelect.innerHTML = `
                <option value="">全部类型</option>
                <optgroup label="按类型筛选">
                    ${types.map(t => `<option value="type_${t}">${t}</option>`).join('')}
                </optgroup>
                <optgroup label="排序方式">
                    <option value="sort_degree">按度数排序</option>
                </optgroup>
            `;
        }
    }

    function handleSearch() {
        const query = document.getElementById('graphSearchInput').value.toLowerCase().trim();
        const typeFilter = document.getElementById('searchTypeFilter')?.value || '';
        const resultsPanel = document.getElementById('searchResultsPanel');
        const resultsList = document.getElementById('searchResultList');
        const resultCount = document.getElementById('searchResultCount');
        
        if (!query && !typeFilter) {
            clearSearchHighlight();
            restoreAllElementsOpacity();
            if (resultsPanel) resultsPanel.classList.add('hidden');
            // 空搜索时还原最初的节点布局
            if (originalLayout) {
                animateToNewLayout(originalLayout, null, new Set(), []);
                originalLayout = null;
            }
            return;
        }

        // 搜索实际节点数据
        const nodes = currentGraphData.nodes || [];
        let results = nodes;
        
        // 按类型筛选
        if (typeFilter) {
            results = results.filter(node => (node.type || '未分类') === typeFilter);
        }
        
        // 按关键词搜索
        if (query) {
            results = results.filter(node => {
                const name = (node.name || '').toLowerCase();
                return name.includes(query);
            });
        }
        
        results = results.map(node => ({
            id: node.nodeId,
            label: node.name,
            type: node.type
        }));

        // 如果只有一个结果，显示以该节点为中心的图谱视图
        if (results.length === 1) {
            showCenteredGraphView(results[0].id);
        } else {
            highlightSearchResults(results);
        }
        
        // 显示搜索结果面板
        if (resultsPanel && resultsList && resultCount) {
            resultCount.textContent = results.length;
            if (results.length > 0) {
                resultsList.innerHTML = results.map(node => `
                    <div class="flex items-center gap-2 p-2 hover:bg-base-200 rounded cursor-pointer text-xs" onclick="highlightAndSelectNode('${node.id}')">
                        <span class="flex-1 truncate">${node.label || '未命名'}</span>
                        <span class="badge badge-ghost badge-xs">${node.type || '未分类'}</span>
                    </div>
                `).join('');
                resultsPanel.classList.remove('hidden');
            } else {
                resultsList.innerHTML = '<div class="text-xs text-base-content/50 text-center py-2">未找到匹配结果</div>';
                resultsPanel.classList.remove('hidden');
            }
        }
    }
    
    /**
     * 以指定节点为中心重新布局图谱
     * 使用新布局系统，直接重绘
     */
    function showCenteredGraphView(centerNodeId) {
        const nodes = currentGraphData.nodes || [];
        
        // 使用新布局系统重新渲染
        bindaoRenderGraph(centerNodeId);
        
        // 选中中心节点并更新详情面板
        const centerNode = nodes.find(n => n.nodeId === centerNodeId);
        if (centerNode) {
            const nodeInfo = {
                id: centerNode.nodeId,
                nodeId: centerNode.nodeId,
                label: centerNode.name,
                name: centerNode.name,
                type: centerNode.type || 'default',
                description: centerNode.description
            };
            selectNode(nodeInfo);
            
            if (window.updateNodeDetailPanel) {
                window.updateNodeDetailPanel(nodeInfo);
            }
        }
        
        window.showNotification(`以"${centerNode?.name || '节点'}"为中心重新布局`, 'info');
    }
    
    // 更新所有边的位置
    function updateAllEdges() {
        const radius = 14;
        
        // 先缓存所有节点位置
        const nodePositionsMap = {};
        document.querySelectorAll('.graph-node').forEach(node => {
            const nodeId = node.getAttribute('data-node-id');
            nodePositionsMap[nodeId] = {
                x: parseFloat(node.getAttribute('cx')),
                y: parseFloat(node.getAttribute('cy'))
            };
        });
        
        // 更新边
        document.querySelectorAll('.graph-edge').forEach(edge => {
            const sourceId = edge.getAttribute('data-edge-source');
            const targetId = edge.getAttribute('data-edge-target');
            
            const sourcePos = nodePositionsMap[sourceId];
            const targetPos = nodePositionsMap[targetId];
            
            if (sourcePos && targetPos) {
                const x1 = sourcePos.x;
                const y1 = sourcePos.y;
                const x2 = targetPos.x;
                const y2 = targetPos.y;
                
                const dx = x2 - x1;
                const dy = y2 - y1;
                const dist = Math.sqrt(dx * dx + dy * dy);
                
                if (dist > 0) {
                    const nx = dx / dist;
                    const ny = dy / dist;
                    edge.setAttribute('x1', x1 + nx * radius);
                    edge.setAttribute('y1', y1 + ny * radius);
                    edge.setAttribute('x2', x2 - nx * (radius + 5));
                    edge.setAttribute('y2', y2 - ny * (radius + 5));
                }
            }
        });
        
        // 单独更新所有边标签
        document.querySelectorAll('.edge-label').forEach(label => {
            const sourceId = label.getAttribute('data-label-source');
            const targetId = label.getAttribute('data-label-target');
            
            const sourcePos = nodePositionsMap[sourceId];
            const targetPos = nodePositionsMap[targetId];
            
            if (sourcePos && targetPos) {
                label.setAttribute('x', (sourcePos.x + targetPos.x) / 2);
                label.setAttribute('y', (sourcePos.y + targetPos.y) / 2 - 8);
            }
        });
    }
    
    // 应用聚焦样式
    function applyFocusStyles(centerNodeId, connectedIds, disconnectedIds) {
        const disconnectedSet = new Set(disconnectedIds);
        
        // 节点样式
        document.querySelectorAll('.graph-node').forEach(nodeEl => {
            const nodeId = nodeEl.getAttribute('data-node-id');
            if (nodeId === centerNodeId) {
                // 中心节点 - 红色高亮
                nodeEl.style.stroke = '#ef4444';
                nodeEl.style.strokeWidth = '4px';
                nodeEl.style.filter = 'drop-shadow(0 0 12px #ef4444)';
                nodeEl.style.opacity = '1';
            } else if (disconnectedSet.has(nodeId)) {
                // 不连通节点 - 变暗
                nodeEl.style.stroke = '';
                nodeEl.style.strokeWidth = '';
                nodeEl.style.filter = 'grayscale(80%)';
                nodeEl.style.opacity = '0.2';
            } else {
                // 连通节点 - 正常显示
                nodeEl.style.stroke = '';
                nodeEl.style.strokeWidth = '';
                nodeEl.style.filter = '';
                nodeEl.style.opacity = '1';
            }
        });
        
        // 标签样式
        document.querySelectorAll('.node-label').forEach(label => {
            const nodeId = label.getAttribute('data-label-for');
            if (nodeId === centerNodeId) {
                label.style.opacity = '1';
                label.style.fontWeight = '700';
            } else if (disconnectedSet.has(nodeId)) {
                label.style.opacity = '0.15';
                label.style.fontWeight = '';
            } else {
                label.style.opacity = '1';
                label.style.fontWeight = '';
            }
        });
        
        // 边样式 - 只有两端都是连通节点的边才正常显示
        document.querySelectorAll('.graph-edge').forEach(edge => {
            const sourceId = edge.getAttribute('data-edge-source');
            const targetId = edge.getAttribute('data-edge-target');
            const bothConnected = connectedIds.has(sourceId) && connectedIds.has(targetId);
            
            if (bothConnected) {
                // 与中心直接相连的边特殊高亮
                if (sourceId === centerNodeId || targetId === centerNodeId) {
                    edge.style.stroke = '#ef4444';
                    edge.style.strokeWidth = '2.5px';
                    edge.style.opacity = '0.9';
                } else {
                    edge.style.stroke = '#1E3A8A';
                    edge.style.strokeWidth = '2px';
                    edge.style.opacity = '0.7';
                }
            } else {
                // 涉及不连通节点的边变暗
                edge.style.stroke = '#999';
                edge.style.strokeWidth = '1px';
                edge.style.opacity = '0.1';
            }
        });
        
        // 边标签样式
        document.querySelectorAll('.edge-label').forEach(label => {
            const sourceId = label.getAttribute('data-label-source');
            const targetId = label.getAttribute('data-label-target');
            const bothConnected = connectedIds.has(sourceId) && connectedIds.has(targetId);
            label.style.opacity = bothConnected ? '1' : '0.1';
        });
    }
    
    // 恢复所有元素的透明度
    function restoreAllElementsOpacity() {
        document.querySelectorAll('.graph-node').forEach(node => {
            node.style.opacity = '';
            node.style.filter = '';
            if (!node.classList.contains('selected')) {
                node.style.stroke = '';
                node.style.strokeWidth = '';
            }
        });
        document.querySelectorAll('.node-label').forEach(label => {
            label.style.opacity = '';
            label.style.fontWeight = '';
        });
        document.querySelectorAll('.graph-edge').forEach(edge => {
            edge.style.opacity = '';
            edge.style.stroke = '#1E3A8A';
            edge.style.strokeWidth = '2px';
        });
        document.querySelectorAll('.edge-label').forEach(label => {
            label.style.opacity = '';
        });
    }
    
    // 重置图谱视图（恢复原始布局）
    window.resetGraphView = function() {
        // 清空搜索框
        const searchInput = document.getElementById('graphSearchInput');
        const typeFilter = document.getElementById('searchTypeFilter');
        if (searchInput) searchInput.value = '';
        if (typeFilter) typeFilter.value = '';
        
        // 隐藏搜索结果面板
        const resultsPanel = document.getElementById('searchResultsPanel');
        if (resultsPanel) resultsPanel.classList.add('hidden');
        
        // 如果有保存的原始布局，恢复它
        if (originalLayout) {
            animateToOriginalLayout();
        } else {
            restoreAllElementsOpacity();
            clearSearchHighlight();
        }
        
        window.showNotification('已恢复原始图谱布局', 'info');
    };
    
    // 动画恢复到原始布局
    function animateToOriginalLayout() {
        if (!originalLayout) return;
        
        const duration = 400;
        const startTime = performance.now();
        
        // 记录当前位置
        const currentPositions = {};
        document.querySelectorAll('.graph-node').forEach(nodeEl => {
            const nodeId = nodeEl.getAttribute('data-node-id');
            currentPositions[nodeId] = {
                x: parseFloat(nodeEl.getAttribute('cx')),
                y: parseFloat(nodeEl.getAttribute('cy'))
            };
        });
        
        function animate(currentTime) {
            const elapsed = currentTime - startTime;
            const progress = Math.min(elapsed / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3);
            
            Object.keys(originalLayout).forEach(nodeId => {
                const start = currentPositions[nodeId];
                const end = originalLayout[nodeId];
                if (!start || !end) return;
                
                const x = start.x + (end.x - start.x) * eased;
                const y = start.y + (end.y - start.y) * eased;
                
                const nodeEl = document.querySelector(`[data-node-id="${nodeId}"]`);
                if (nodeEl) {
                    nodeEl.setAttribute('cx', x);
                    nodeEl.setAttribute('cy', y);
                }
                
                const labelEl = document.querySelector(`[data-label-for="${nodeId}"]`);
                if (labelEl) {
                    labelEl.setAttribute('x', x);
                    labelEl.setAttribute('y', y + 28);
                }
                
                if (currentGraphData.nodePositions?.[nodeId]) {
                    currentGraphData.nodePositions[nodeId].x = x;
                    currentGraphData.nodePositions[nodeId].y = y;
                }
            });
            
            updateAllEdges();
            
            if (progress < 1) {
                requestAnimationFrame(animate);
            } else {
                // 动画结束，恢复样式
                restoreAllElementsOpacity();
                clearSearchHighlight();
                originalLayout = null; // 清除保存的布局
            }
        }
        
        requestAnimationFrame(animate);
    }

    function highlightSearchResults(results) {
        clearSearchHighlight();
        restoreAllElementsOpacity();
        results.forEach(result => {
            const node = document.querySelector(`[data-node-id="${result.id}"]`);
            if (node) {
                node.style.stroke = 'var(--color-warning)';
                node.style.strokeWidth = '3px';
            }
        });
    }

    function clearSearchHighlight() {
        document.querySelectorAll('.graph-node').forEach(node => {
            if (!node.classList.contains('selected')) {
                node.style.stroke = '';
                node.style.strokeWidth = '';
                node.style.filter = '';
            }
        });
    }

    // View controls setup
    function setupViewControls() {
        const depthRange = document.getElementById('depthRange');
        if (depthRange) {
            depthRange.addEventListener('input', function() {
                const value = parseInt(this.value);
                // 如果有选中的节点，则高亮其N层邻居
                if (currentSelectedNodeId) {
                    const direction = document.getElementById('neighborDirectionSelect')?.value || 'all';
                    highlightNeighborsByDepth(currentSelectedNodeId, value, direction);
                }
            });
        }
    }
    
    // 当前选中的节点ID
    let currentSelectedNodeId = null;
    
    // 高亮节点的N层邻居（支持方向：all/out/in）
    function highlightNeighborsByDepth(nodeId, depth, direction) {
        direction = direction || 'all';
        const nodes = currentGraphData.nodes || [];
        const edges = currentGraphData.edges || [];
        
        // 根据方向构建邻接表
        const adjacencyList = {};
        nodes.forEach(n => adjacencyList[n.nodeId] = []);
        edges.forEach(edge => {
            if (direction === 'all' || direction === 'out') {
                // 出边：source -> target
                if (adjacencyList[edge.sourceNodeId]) {
                    adjacencyList[edge.sourceNodeId].push(edge.targetNodeId);
                }
            }
            if (direction === 'all' || direction === 'in') {
                // 入边：target -> source
                if (adjacencyList[edge.targetNodeId]) {
                    adjacencyList[edge.targetNodeId].push(edge.sourceNodeId);
                }
            }
        });
        
        // BFS获取N层内的所有邻居
        const visitedNodes = new Set([nodeId]);
        let currentLevel = [nodeId];
        
        for (let d = 0; d < depth; d++) {
            const nextLevel = [];
            for (const n of currentLevel) {
                for (const neighbor of (adjacencyList[n] || [])) {
                    if (!visitedNodes.has(neighbor)) {
                        visitedNodes.add(neighbor);
                        nextLevel.push(neighbor);
                    }
                }
            }
            currentLevel = nextLevel;
        }
        
        // 收集需要高亮的边
        const highlightedEdges = new Set();
        edges.forEach(edge => {
            if (visitedNodes.has(edge.sourceNodeId) && visitedNodes.has(edge.targetNodeId)) {
                highlightedEdges.add(`${edge.sourceNodeId}-${edge.targetNodeId}`);
            }
        });
        
        // 更新节点透明度
        document.querySelectorAll('.graph-node').forEach(node => {
            const nId = node.getAttribute('data-node-id');
            node.style.opacity = visitedNodes.has(nId) ? '1' : '0.2';
        });
        
        // 更新节点标签透明度
        document.querySelectorAll('[data-label-for]').forEach(label => {
            const nId = label.getAttribute('data-label-for');
            label.style.opacity = visitedNodes.has(nId) ? '1' : '0.2';
        });
        
        // 更新边透明度
        document.querySelectorAll('.graph-edge').forEach(edge => {
            const source = edge.getAttribute('data-edge-source');
            const target = edge.getAttribute('data-edge-target');
            const isHighlighted = highlightedEdges.has(`${source}-${target}`) || highlightedEdges.has(`${target}-${source}`);
            edge.style.opacity = isHighlighted ? '0.7' : '0.1';
        });
        
        // 更新边标签透明度
        document.querySelectorAll('.edge-label').forEach(label => {
            const source = label.getAttribute('data-label-source');
            const target = label.getAttribute('data-label-target');
            const isHighlighted = highlightedEdges.has(`${source}-${target}`) || highlightedEdges.has(`${target}-${source}`);
            label.style.opacity = isHighlighted ? '1' : '0.1';
        });
        
        const directionText = direction === 'all' ? '所有' : (direction === 'out' ? '出边' : '入边');
        window.showNotification(`已显示 ${depth} 层${directionText}邻居，共 ${visitedNodes.size} 个节点`, 'info');
    }
    
    // 邻居方向改变时的处理函数
    function handleNeighborDirectionChange() {
        if (currentSelectedNodeId) {
            const depth = parseInt(document.getElementById('depthRange').value);
            const direction = document.getElementById('neighborDirectionSelect').value;
            highlightNeighborsByDepth(currentSelectedNodeId, depth, direction);
        }
    }
    window.handleNeighborDirectionChange = handleNeighborDirectionChange;
    
    // 节点排序查询
    window.handleNodeQuery = function(value) {
        if (!value) return;
        
        if (value === 'degree') {
            showNodesByDegree();
        }
        
        // 重置下拉框
        document.getElementById('nodeQuerySelect').value = '';
    };
    
    // 显示按度数排序的节点列表
    function showNodesByDegree() {
        const nodes = currentGraphData.nodes || [];
        const edges = currentGraphData.edges || [];
        
        // 计算每个节点的度数
        const degreeMap = {};
        nodes.forEach(n => degreeMap[n.nodeId] = 0);
        edges.forEach(edge => {
            if (degreeMap[edge.sourceNodeId] !== undefined) degreeMap[edge.sourceNodeId]++;
            if (degreeMap[edge.targetNodeId] !== undefined) degreeMap[edge.targetNodeId]++;
        });
        
        // 按度数排序
        const sortedNodes = nodes.map(n => ({
            ...n,
            degree: degreeMap[n.nodeId] || 0
        })).sort((a, b) => b.degree - a.degree);
        
        // 显示结果
        const resultsPanel = document.getElementById('searchResultsPanel');
        const resultsList = document.getElementById('searchResultList');
        const resultCount = document.getElementById('searchResultCount');
        
        if (resultsPanel && resultsList && resultCount) {
            resultCount.textContent = sortedNodes.length;
            resultsList.innerHTML = sortedNodes.map(node => `
                <div class="flex items-center justify-between gap-2 p-2 hover:bg-base-200 rounded cursor-pointer text-xs" onclick="highlightAndSelectNode('${node.nodeId}')">
                    <span class="truncate">${node.name}</span>
                    <span class="badge badge-sm badge-primary">${node.degree}</span>
                </div>
            `).join('');
            resultsPanel.classList.remove('hidden');
        }
        
        window.showNotification('已按度数排序，点击节点可选中', 'info');
    }
    
    // 路径查询
    window.handlePathQuery = function(value) {
        if (!value) return;
        
        if (value === 'relation' || value === 'shortest') {
            openPathQueryModal(value);
        }
        
        // 重置下拉框
        document.getElementById('pathQuerySelect').value = '';
    };
    
    // 打开路径查询模态框
    function openPathQueryModal(queryType) {
        const typeNames = {
            'relation': '关系寻路',
            'shortest': '最短路径'
        };
        
        // 创建模态框（如果不存在）
        let modal = document.getElementById('pathQueryModal');
        if (!modal) {
            modal = document.createElement('dialog');
            modal.id = 'pathQueryModal';
            modal.className = 'modal';
            document.body.appendChild(modal);
        }
        
        const nodes = currentGraphData.nodes || [];
        // 按字典序排列节点
        const sortedNodes = [...nodes].sort((a, b) => {
            const nameA = (a.name || '').toLowerCase();
            const nameB = (b.name || '').toLowerCase();
            return nameA.localeCompare(nameB, 'zh-CN');
        });
        // 使用 datalist 实现可输入搜索
        const nodeDatalistOptions = sortedNodes.map(n => `<option value="${n.name}" data-node-id="${n.nodeId}">`).join('');
        
        modal.innerHTML = `
            <div class="modal-box">
                <h3 class="font-bold text-lg mb-4">${typeNames[queryType]}</h3>
                <div class="form-control mb-3">
                    <label class="label"><span class="label-text">起始节点</span></label>
                    <input type="text" id="pathStartNodeInput" list="pathStartNodeList" class="input input-bordered input-sm w-full" placeholder="输入或选择起始节点">
                    <datalist id="pathStartNodeList">${nodeDatalistOptions}</datalist>
                </div>
                <div class="form-control mb-4">
                    <label class="label"><span class="label-text">目标节点</span></label>
                    <input type="text" id="pathEndNodeInput" list="pathEndNodeList" class="input input-bordered input-sm w-full" placeholder="输入或选择目标节点">
                    <datalist id="pathEndNodeList">${nodeDatalistOptions}</datalist>
                </div>
                <div class="modal-action">
                    <button class="btn btn-sm" onclick="document.getElementById('pathQueryModal').close()">取消</button>
                    <button class="btn btn-primary btn-sm" onclick="executePathQuery('${queryType}')">查询</button>
                </div>
            </div>
            <form method="dialog" class="modal-backdrop"><button>close</button></form>
        `;
        
        // 保存节点映射供查询使用
        window._pathQueryNodes = sortedNodes;
        
        modal.showModal();
    }
    
    // 执行路径查询
    window.executePathQuery = function(queryType) {
        const startName = document.getElementById('pathStartNodeInput').value.trim();
        const endName = document.getElementById('pathEndNodeInput').value.trim();
        
        if (!startName || !endName) {
            window.showNotification('请输入起始节点和目标节点', 'warning');
            return;
        }
        
        // 通过名称查找节点ID
        const nodes = window._pathQueryNodes || currentGraphData.nodes || [];
        const startNode = nodes.find(n => n.name === startName);
        const endNode = nodes.find(n => n.name === endName);
        
        if (!startNode) {
            window.showNotification('未找到起始节点: ' + startName, 'warning');
            return;
        }
        if (!endNode) {
            window.showNotification('未找到目标节点: ' + endName, 'warning');
            return;
        }
        
        const startId = startNode.nodeId;
        const endId = endNode.nodeId;
        
        if (startId === endId) {
            window.showNotification('起始节点和目标节点不能相同', 'warning');
            return;
        }
        
        document.getElementById('pathQueryModal').close();
        
        if (queryType === 'shortest' || queryType === 'relation') {
            findShortestPath(startId, endId, queryType === 'relation');
        }
    };
    
    // BFS查找最短路径
    function findShortestPath(startId, endId, showRelations) {
        const edges = currentGraphData.edges || [];
        const nodes = currentGraphData.nodes || [];
        
        // 构建邻接表
        const adjacencyList = {};
        const edgeMap = {};
        nodes.forEach(n => adjacencyList[n.nodeId] = []);
        edges.forEach(edge => {
            if (adjacencyList[edge.sourceNodeId]) {
                adjacencyList[edge.sourceNodeId].push(edge.targetNodeId);
                edgeMap[`${edge.sourceNodeId}-${edge.targetNodeId}`] = edge;
            }
            if (adjacencyList[edge.targetNodeId]) {
                adjacencyList[edge.targetNodeId].push(edge.sourceNodeId);
                edgeMap[`${edge.targetNodeId}-${edge.sourceNodeId}`] = edge;
            }
        });
        
        // BFS
        const visited = new Set([startId]);
        const queue = [[startId]];
        let path = null;
        
        while (queue.length > 0) {
            const currentPath = queue.shift();
            const currentNode = currentPath[currentPath.length - 1];
            
            if (currentNode === endId) {
                path = currentPath;
                break;
            }
            
            for (const neighbor of (adjacencyList[currentNode] || [])) {
                if (!visited.has(neighbor)) {
                    visited.add(neighbor);
                    queue.push([...currentPath, neighbor]);
                }
            }
        }
        
        if (path) {
            highlightPath(path, edgeMap, showRelations);
        } else {
            window.showNotification('未找到连接这两个节点的路径', 'warning');
        }
    }
    
    // 高亮路径
    function highlightPath(path, edgeMap, showRelations) {
        // 降低所有节点和边的透明度
        document.querySelectorAll('.graph-node').forEach(n => n.style.opacity = '0.2');
        document.querySelectorAll('.graph-edge').forEach(e => e.style.opacity = '0.1');
        document.querySelectorAll('.edge-label').forEach(l => l.style.opacity = '0.1');
        document.querySelectorAll('[data-label-for]').forEach(l => l.style.opacity = '0.2');
        
        // 高亮路径上的节点
        const pathNodeIds = new Set(path);
        document.querySelectorAll('.graph-node').forEach(node => {
            if (pathNodeIds.has(node.getAttribute('data-node-id'))) {
                node.style.opacity = '1';
            }
        });
        document.querySelectorAll('[data-label-for]').forEach(label => {
            if (pathNodeIds.has(label.getAttribute('data-label-for'))) {
                label.style.opacity = '1';
            }
        });
        
        // 高亮路径上的边
        const pathEdges = [];
        for (let i = 0; i < path.length - 1; i++) {
            const edgeKey1 = `${path[i]}-${path[i+1]}`;
            const edgeKey2 = `${path[i+1]}-${path[i]}`;
            const edge = edgeMap[edgeKey1] || edgeMap[edgeKey2];
            if (edge) pathEdges.push(edge);
        }
        
        document.querySelectorAll('.graph-edge').forEach(edgeEl => {
            const source = edgeEl.getAttribute('data-edge-source');
            const target = edgeEl.getAttribute('data-edge-target');
            const isInPath = pathEdges.some(e => 
                (e.sourceNodeId === source && e.targetNodeId === target) ||
                (e.sourceNodeId === target && e.targetNodeId === source)
            );
            if (isInPath) {
                edgeEl.style.opacity = '1';
                edgeEl.style.stroke = '#3b82f6';
                edgeEl.style.strokeWidth = '3';
            }
        });
        
        // 构建路径描述
        const nodes = currentGraphData.nodes || [];
        const nodeNameMap = {};
        nodes.forEach(n => nodeNameMap[n.nodeId] = n.name);
        
        let pathDesc = path.map(id => nodeNameMap[id] || id).join(' → ');
        if (showRelations) {
            pathDesc = '';
            for (let i = 0; i < path.length; i++) {
                pathDesc += nodeNameMap[path[i]] || path[i];
                if (i < path.length - 1) {
                    const edge = pathEdges[i];
                    const relType = edge ? (edge.type || '关联') : '?';
                    pathDesc += ` -[${relType}]→ `;
                }
            }
        }
        
        window.showNotification(`路径: ${pathDesc}`, 'success');
    }
    
    // DFS查找所有路径
    function findAllPaths(startId, endId, maxDepth = 5) {
        const edges = currentGraphData.edges || [];
        const nodes = currentGraphData.nodes || [];
        
        // 构建邻接表
        const adjacencyList = {};
        nodes.forEach(n => adjacencyList[n.nodeId] = []);
        edges.forEach(edge => {
            if (adjacencyList[edge.sourceNodeId]) {
                adjacencyList[edge.sourceNodeId].push(edge.targetNodeId);
            }
            if (adjacencyList[edge.targetNodeId]) {
                adjacencyList[edge.targetNodeId].push(edge.sourceNodeId);
            }
        });
        
        const allPaths = [];
        const visited = new Set();
        
        function dfs(current, path) {
            if (path.length > maxDepth) return;
            if (current === endId) {
                allPaths.push([...path]);
                return;
            }
            
            visited.add(current);
            for (const neighbor of (adjacencyList[current] || [])) {
                if (!visited.has(neighbor)) {
                    path.push(neighbor);
                    dfs(neighbor, path);
                    path.pop();
                }
            }
            visited.delete(current);
        }
        
        dfs(startId, [startId]);
        
        if (allPaths.length === 0) {
            window.showNotification('未找到连接这两个节点的路径', 'warning');
            return;
        }
        
        // 显示路径列表
        const nodeNameMap = {};
        nodes.forEach(n => nodeNameMap[n.nodeId] = n.name);
        
        const resultsPanel = document.getElementById('searchResultsPanel');
        const resultsList = document.getElementById('searchResultList');
        const resultCount = document.getElementById('searchResultCount');
        
        if (resultsPanel && resultsList && resultCount) {
            resultCount.textContent = allPaths.length;
            resultsList.innerHTML = allPaths.map((path, idx) => {
                const pathStr = path.map(id => nodeNameMap[id] || id).join(' → ');
                return `<div class="p-2 hover:bg-base-200 rounded cursor-pointer text-xs" onclick="highlightPathByIndex(${idx})">
                    <span class="font-medium">路径${idx + 1}:</span> ${pathStr}
                </div>`;
            }).join('');
            resultsPanel.classList.remove('hidden');
        }
        
        // 保存路径供高亮使用
        window._allPaths = allPaths;
        window._edgeMap = {};
        edges.forEach(edge => {
            window._edgeMap[`${edge.sourceNodeId}-${edge.targetNodeId}`] = edge;
            window._edgeMap[`${edge.targetNodeId}-${edge.sourceNodeId}`] = edge;
        });
        
        window.showNotification(`找到 ${allPaths.length} 条路径`, 'success');
    }
    
    // 按索引高亮路径
    window.highlightPathByIndex = function(idx) {
        if (window._allPaths && window._allPaths[idx]) {
            highlightPath(window._allPaths[idx], window._edgeMap, false);
        }
    };

    // Entity type filters - now handled dynamically in updateEntityTypeFiltersFromData
    function setupEntityTypeFilters() {
        // Filters are set up dynamically when data loads
    }

    // Relationship filters - now handled dynamically in updateRelationshipTypeFiltersFromData
    function setupRelationshipFilters() {
        // Filters are set up dynamically when data loads
    }

    // Statistics toggle
    window.toggleStatistics = function() {
        const content = document.getElementById('statisticsContent');
        const icon = document.getElementById('statsToggleIcon');
        
        if (statisticsCollapsed) {
            content.style.display = 'block';
            icon.setAttribute('data-icon', 'heroicons:chevron-up');
            statisticsCollapsed = false;
        } else {
            content.style.display = 'none';
            icon.setAttribute('data-icon', 'heroicons:chevron-down');
            statisticsCollapsed = true;
        }
    };

    // Graph control functions
    window.searchInGraph = function() {
        const searchTarget = document.getElementById('searchTargetSelect')?.value || 'node';
        if (searchTarget === 'node') {
            handleSearch();
        } else {
            handleRelationSearch();
        }
    };
    
    // 更新搜索框placeholder
    window.updateSearchPlaceholder = function() {
        const searchTarget = document.getElementById('searchTargetSelect')?.value || 'node';
        const searchInput = document.getElementById('graphSearchInput');
        if (searchInput) {
            searchInput.placeholder = searchTarget === 'node' ? '搜索节点名称...' : '搜索关系类型...';
        }
    };
    
    // 关系搜索
    function handleRelationSearch() {
        const query = document.getElementById('graphSearchInput').value.toLowerCase().trim();
        const resultsPanel = document.getElementById('searchResultsPanel');
        const resultsList = document.getElementById('searchResultList');
        const resultCount = document.getElementById('searchResultCount');
        
        if (!query) {
            clearSearchHighlight();
            restoreAllElementsOpacity();
            if (resultsPanel) resultsPanel.classList.add('hidden');
            return;
        }
        
        const edges = currentGraphData.edges || [];
        const nodes = currentGraphData.nodes || [];
        
        // 搜索匹配的关系
        const matchedEdges = edges.filter(edge => {
            const edgeType = (edge.type || '关联').toLowerCase();
            return edgeType.includes(query);
        });
        
        if (matchedEdges.length === 0) {
            if (resultsPanel && resultsList && resultCount) {
                resultCount.textContent = 0;
                resultsList.innerHTML = '<div class="text-xs text-base-content/50 text-center py-2">未找到匹配的关系</div>';
                resultsPanel.classList.remove('hidden');
            }
            return;
        }
        
        // 收集匹配关系两端的节点ID
        const highlightedNodeIds = new Set();
        matchedEdges.forEach(edge => {
            highlightedNodeIds.add(edge.sourceNodeId);
            highlightedNodeIds.add(edge.targetNodeId);
        });
        
        // 高亮匹配的边和节点
        document.querySelectorAll('.graph-node').forEach(node => {
            const nodeId = node.getAttribute('data-node-id');
            node.style.opacity = highlightedNodeIds.has(nodeId) ? '1' : '0.2';
        });
        
        document.querySelectorAll('[data-label-for]').forEach(label => {
            const nodeId = label.getAttribute('data-label-for');
            label.style.opacity = highlightedNodeIds.has(nodeId) ? '1' : '0.2';
        });
        
        document.querySelectorAll('.graph-edge').forEach(edge => {
            const source = edge.getAttribute('data-edge-source');
            const target = edge.getAttribute('data-edge-target');
            const isMatched = matchedEdges.some(e => 
                (e.sourceNodeId === source && e.targetNodeId === target) ||
                (e.sourceNodeId === target && e.targetNodeId === source)
            );
            edge.style.opacity = isMatched ? '0.8' : '0.1';
            if (isMatched) {
                edge.style.stroke = '#3b82f6';
                edge.style.strokeWidth = '2.5';
            }
        });
        
        document.querySelectorAll('.edge-label').forEach(label => {
            const source = label.getAttribute('data-label-source');
            const target = label.getAttribute('data-label-target');
            const isMatched = matchedEdges.some(e => 
                (e.sourceNodeId === source && e.targetNodeId === target) ||
                (e.sourceNodeId === target && e.targetNodeId === source)
            );
            label.style.opacity = isMatched ? '1' : '0.1';
        });
        
        // 去重：同一对节点只保留一条关系
        const seenPairs = new Set();
        const uniqueEdges = matchedEdges.filter(edge => {
            const pairKey1 = `${edge.sourceNodeId}-${edge.targetNodeId}`;
            const pairKey2 = `${edge.targetNodeId}-${edge.sourceNodeId}`;
            if (seenPairs.has(pairKey1) || seenPairs.has(pairKey2)) {
                return false;
            }
            seenPairs.add(pairKey1);
            return true;
        });
        
        // 按关系类型分组显示结果
        const groupedByType = {};
        uniqueEdges.forEach(edge => {
            const type = edge.type || '关联';
            if (!groupedByType[type]) groupedByType[type] = [];
            groupedByType[type].push(edge);
        });
        
        // 构建节点名称映射
        const nodeNameMap = {};
        nodes.forEach(n => nodeNameMap[n.nodeId] = n.name);
        
        // 显示结果
        if (resultsPanel && resultsList && resultCount) {
            resultCount.textContent = uniqueEdges.length;
            let html = '';
            for (const [type, typeEdges] of Object.entries(groupedByType)) {
                html += `<div class="text-xs font-medium text-primary mb-1">${type} (${typeEdges.length})</div>`;
                html += typeEdges.slice(0, 5).map(edge => `
                    <div class="flex items-center gap-1 p-1 hover:bg-base-200 rounded cursor-pointer text-xs" onclick="highlightSingleRelation('${edge.sourceNodeId}', '${edge.targetNodeId}')">
                        <span class="truncate">${nodeNameMap[edge.sourceNodeId] || '?'}</span>
                        <span class="text-base-content/50">→</span>
                        <span class="truncate">${nodeNameMap[edge.targetNodeId] || '?'}</span>
                    </div>
                `).join('');
                if (typeEdges.length > 5) {
                    html += `<div class="text-xs text-base-content/50 pl-2">...还有 ${typeEdges.length - 5} 条</div>`;
                }
            }
            resultsList.innerHTML = html;
            resultsPanel.classList.remove('hidden');
        }
        
        window.showNotification(`找到 ${uniqueEdges.length} 条匹配的关系`, 'info');
    }
    
    // 高亮单条关系
    window.highlightSingleRelation = function(sourceId, targetId) {
        // 先恢复所有元素透明度
        restoreAllElementsOpacity();
        
        // 降低所有透明度
        document.querySelectorAll('.graph-node').forEach(n => n.style.opacity = '0.2');
        document.querySelectorAll('[data-label-for]').forEach(l => l.style.opacity = '0.2');
        document.querySelectorAll('.graph-edge').forEach(e => e.style.opacity = '0.1');
        document.querySelectorAll('.edge-label').forEach(l => l.style.opacity = '0.1');
        
        // 高亮选中的关系两端节点
        [sourceId, targetId].forEach(nodeId => {
            const node = document.querySelector(`[data-node-id="${nodeId}"]`);
            if (node) node.style.opacity = '1';
            const label = document.querySelector(`[data-label-for="${nodeId}"]`);
            if (label) label.style.opacity = '1';
        });
        
        // 高亮选中的边
        document.querySelectorAll('.graph-edge').forEach(edge => {
            const source = edge.getAttribute('data-edge-source');
            const target = edge.getAttribute('data-edge-target');
            if ((source === sourceId && target === targetId) || (source === targetId && target === sourceId)) {
                edge.style.opacity = '1';
                edge.style.stroke = '#3b82f6';
                edge.style.strokeWidth = '3';
            }
        });
        
        // 高亮边标签
        document.querySelectorAll('.edge-label').forEach(label => {
            const source = label.getAttribute('data-label-source');
            const target = label.getAttribute('data-label-target');
            if ((source === sourceId && target === targetId) || (source === targetId && target === sourceId)) {
                label.style.opacity = '1';
            }
        });
    };

    // zoomIn, zoomOut, applyViewBoxZoom 已移至外部 JS (graph-bindao-bindao.js)
    // 滚轮缩放已在外部 JS 中通过 DOMContentLoaded 事件绑定

    // Action functions
    window.toggleFavorite = function() {
        const isFavorited = window.APP_GLOBALS.favorites.isFavorited(currentGraphData.id);
        
        if (isFavorited) {
            window.APP_GLOBALS.favorites.remove(currentGraphData.id);
        } else {
            window.APP_GLOBALS.favorites.add(currentGraphData.id, currentGraphData);
            navigateTo('user_profile_page');
        }
        
        updateFavoriteButton();
    };

    function updateFavoriteButton() {
        const btn = document.getElementById('favoriteBtn');
        const icon = btn.querySelector('.iconify');
        const isFavorited = window.APP_GLOBALS.favorites.isFavorited(currentGraphData.id);
        
        if (isFavorited) {
            icon.setAttribute('data-icon', 'heroicons:heart-solid');
            icon.classList.add('favorite-active');
        } else {
            icon.setAttribute('data-icon', 'heroicons:heart');
            icon.classList.remove('favorite-active');
        }
    }

    window.shareGraph = function() {
        // 获取正确的 origin（处理 iframe 情况）
        let origin = window.location.origin;
        if (!origin || origin === 'null') {
            try {
                origin = window.parent.location.origin;
            } catch(e) {
                origin = '';
            }
        }
        if (!origin || origin === 'null') {
            origin = window.location.protocol + '//' + window.location.host;
        }
        
        // Generate share link - use the stored shareLink if available
        let shareUrl;
        if (currentGraphData.shareLink) {
            shareUrl = `${origin}/graph/graph_detail.html?share=${currentGraphData.shareLink}`;
        } else if (currentGraphData.id) {
            shareUrl = `${origin}/graph/graph_detail.html?id=${currentGraphData.id}`;
        } else {
            shareUrl = window.location.href;
        }
        document.getElementById('shareLink').value = shareUrl;
        
        // Show modal
        document.getElementById('shareModal').showModal();
    };

    window.downloadGraph = function() {
        document.getElementById('downloadModal').showModal();
    };

    // Copy share link function
    window.copyShareLink = function() {
        const shareLink = document.getElementById('shareLink');
        shareLink.select();
        shareLink.setSelectionRange(0, 99999);
        
        navigator.clipboard.writeText(shareLink.value).then(() => {
            const btn = document.getElementById('copyShareBtn');
            const originalText = btn.innerHTML;
            
            btn.innerHTML = '<span class="iconify" data-icon="heroicons:check" data-width="16"></span>已复制';
            btn.classList.add('btn-success');
            btn.classList.remove('btn-primary');
            
            setTimeout(() => {
                btn.innerHTML = originalText;
                btn.classList.remove('btn-success');
                btn.classList.add('btn-primary');
            }, 2000);
            
            window.showNotification('分享链接已复制到剪贴板', 'success');
        }).catch(() => {
            window.showNotification('复制失败，请手动复制', 'error');
        });
    };

    // Download format selection - 直接触发下载
    window.downloadFormat = function(format) {
        const graphId = getCurrentGraphId();
        if (!graphId) {
            window.showNotification('请先选择图谱', 'warning');
            return;
        }
        
        const downloadUrl = `/api/download/${graphId}?format=${format}`;
        
        // 直接触发下载
        const link = document.createElement('a');
        link.href = downloadUrl;
        link.download = '';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        
        // 关闭模态框
        document.getElementById('downloadModal').close();
        window.showNotification(`正在下载 ${format.toUpperCase()} 格式文件...`, 'info');
    };

    window.toggleFullscreen = function() {
        const graphContainer = document.querySelector('.flex-1.flex');
        
        if (!isFullscreen) {
            if (graphContainer.requestFullscreen) {
                graphContainer.requestFullscreen();
            }
            isFullscreen = true;
        } else {
            if (document.exitFullscreen) {
                document.exitFullscreen();
            }
            isFullscreen = false;
        }
    };

    // Handle fullscreen change
    document.addEventListener('fullscreenchange', function() {
        isFullscreen = !!document.fullscreenElement;
    });

    // ==================== 节点管理功能 ====================
    
    // 当前导入模式
    let currentImportMode = 'single';
    
    // 检查编辑权限并显示编辑区域
    async function checkAndShowEditSection() {
        const graphId = getCurrentGraphId();
        if (!graphId) return;
        
        try {
            // 检查用户是否有编辑权限（图谱创建者或管理员）
            const response = await fetch(`/api/graph/${graphId}/can-edit`, {
                credentials: 'include'
            });
            
            if (response.ok) {
                const result = await response.json();
                if (result.canEdit) {
                    document.getElementById('nodeManagementSection').style.display = 'block';
                }
            }
        } catch (error) {
            // 权限检查失败，默认不显示编辑区域
        }
    }
    
    // 切换导入标签页
    window.switchImportTab = function(mode) {
        currentImportMode = mode;
        
        const singleTab = document.getElementById('singleImportTab');
        const batchTab = document.getElementById('batchImportTab');
        const singleForm = document.getElementById('singleImportForm');
        const batchForm = document.getElementById('batchImportForm');
        
        if (mode === 'single') {
            singleTab.classList.add('tab-active');
            batchTab.classList.remove('tab-active');
            singleForm.classList.remove('hidden');
            batchForm.classList.add('hidden');
        } else {
            singleTab.classList.remove('tab-active');
            batchTab.classList.add('tab-active');
            singleForm.classList.add('hidden');
            batchForm.classList.remove('hidden');
        }
    };
    
    // 打开导入节点模态框
    window.openBatchImportModal = function() {
        document.getElementById('singleNodeName').value = '';
        document.getElementById('singleNodeType').value = '';
        document.getElementById('singleNodeDescription').value = '';
        document.getElementById('batchImportInput').value = '';
        switchImportTab('single');
        document.getElementById('batchImportModal').showModal();
    };
    
    // 执行导入节点（支持单个和批量）
    window.executeImportNodes = async function() {
        const graphId = getCurrentGraphId();
        if (!graphId) {
            window.showNotification('请先选择图谱', 'error');
            return;
        }
        
        let nodes = [];
        
        if (currentImportMode === 'single') {
            const name = document.getElementById('singleNodeName').value.trim();
            const type = document.getElementById('singleNodeType').value.trim() || '默认';
            const description = document.getElementById('singleNodeDescription').value.trim();
            
            if (!name) {
                window.showNotification('请输入节点名称', 'warning');
                return;
            }
            
            nodes = [{ name, type, description }];
        } else {
            const input = document.getElementById('batchImportInput').value.trim();
            if (!input) {
                window.showNotification('请输入要导入的节点', 'warning');
                return;
            }
            
            const lines = input.split('\n').filter(line => line.trim());
            nodes = lines.map(line => {
                const parts = line.split(',').map(p => p.trim());
                return {
                    name: parts[0] || '',
                    type: parts[1] || '默认',
                    description: parts[2] || ''
                };
            }).filter(n => n.name);
        }
        
        if (nodes.length === 0) {
            window.showNotification('未找到有效的节点数据', 'warning');
            return;
        }
        
        try {
            const endpoint = nodes.length === 1 ? `/api/graph/${graphId}/nodes` : `/api/graph/${graphId}/nodes/batch`;
            const body = nodes.length === 1 ? nodes[0] : nodes;
            
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify(body)
            });
            
            const result = await response.json();
            if (response.ok && result.success) {
                const msg = nodes.length === 1 ? '节点创建成功' : `成功导入 ${result.count} 个节点`;
                window.showNotification(msg, 'success');
                document.getElementById('batchImportModal').close();
                const data = await loadGraphData(graphId);
                if (data) {
                    updatePageWithGraphData(data);
                    bindaoRenderGraph();
                    // 同步更新封面
                    setTimeout(function() { 
                        if (window.updateGraphCover) {
                            window.updateGraphCover(); 
                        }
                    }, 500);
                }
            } else {
                window.showNotification(result.error || '操作失败', 'error');
            }
        } catch (error) {
            window.showNotification('请求失败: ' + error.message, 'error');
        }
    };
    
    // 打开删除节点模态框
    window.openBatchDeleteModal = function() {
        populateDeleteModal();
        document.getElementById('batchDeleteModal').showModal();
    };
    
    // 填充删除模态框数据
    function populateDeleteModal() {
        const nodes = currentGraphData.nodes || [];
        const types = [...new Set(nodes.map(n => n.type || '未分类'))];
        
        const typeSelect = document.getElementById('deleteTypeFilter');
        typeSelect.innerHTML = '<option value="">全部类型</option>' + 
            types.map(t => `<option value="${t}">${t}</option>`).join('');
        
        document.getElementById('deleteSearchInput').value = '';
        renderDeleteNodeList(nodes);
        updateSelectedDeleteCount();
    }
    
    // 渲染删除节点列表
    function renderDeleteNodeList(nodes) {
        const container = document.getElementById('deleteNodeList');
        if (nodes.length === 0) {
            container.innerHTML = '<div class="text-sm text-base-content/50 text-center">暂无节点</div>';
            return;
        }
        
        container.innerHTML = nodes.map(node => `
            <label class="flex items-center gap-2 p-2 hover:bg-base-200 rounded cursor-pointer">
                <input type="checkbox" class="checkbox checkbox-sm delete-node-checkbox" data-node-id="${node.nodeId}">
                <span class="flex-1">${node.name || '未命名'}</span>
                <span class="badge badge-ghost badge-xs">${node.type || '未分类'}</span>
            </label>
        `).join('');
        
        container.querySelectorAll('.delete-node-checkbox').forEach(cb => {
            cb.addEventListener('change', updateSelectedDeleteCount);
        });
    }
    
    window.filterDeleteNodes = function() {
        const typeFilter = document.getElementById('deleteTypeFilter').value;
        const searchKeyword = document.getElementById('deleteSearchInput').value.toLowerCase().trim();
        
        let filtered = currentGraphData.nodes || [];
        if (typeFilter) {
            filtered = filtered.filter(n => (n.type || '未分类') === typeFilter);
        }
        if (searchKeyword) {
            filtered = filtered.filter(n => (n.name || '').toLowerCase().includes(searchKeyword));
        }
        
        renderDeleteNodeList(filtered);
    };
    
    window.selectAllDeleteNodes = function() {
        document.querySelectorAll('.delete-node-checkbox').forEach(cb => cb.checked = true);
        updateSelectedDeleteCount();
    };
    
    window.deselectAllDeleteNodes = function() {
        document.querySelectorAll('.delete-node-checkbox').forEach(cb => cb.checked = false);
        updateSelectedDeleteCount();
    };
    
    function updateSelectedDeleteCount() {
        const count = document.querySelectorAll('.delete-node-checkbox:checked').length;
        document.getElementById('selectedDeleteCount').textContent = `已选择 ${count} 个节点`;
    }
    
    window.executeBatchDelete = async function() {
        const graphId = getCurrentGraphId();
        if (!graphId) {
            window.showNotification('请先选择图谱', 'error');
            return;
        }
        
        const selectedNodes = [];
        document.querySelectorAll('.delete-node-checkbox:checked').forEach(cb => {
            selectedNodes.push(cb.getAttribute('data-node-id'));
        });
        
        if (selectedNodes.length === 0) {
            window.showNotification('请选择要删除的节点', 'warning');
            return;
        }
        
        if (!confirm(`确定要删除选中的 ${selectedNodes.length} 个节点吗？此操作不可恢复！`)) {
            return;
        }
        
        try {
            const response = await fetch(`/api/graph/${graphId}/nodes/batch`, {
                method: 'DELETE',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify(selectedNodes)
            });
            
            const result = await response.json();
            if (response.ok && result.success) {
                window.showNotification(result.message || '删除成功', 'success');
                document.getElementById('batchDeleteModal').close();
                const data = await loadGraphData(graphId);
                if (data) {
                    updatePageWithGraphData(data);
                    bindaoRenderGraph();
                    // 同步更新封面
                    setTimeout(function() { window.updateGraphCover(); }, 500);
                }
            } else {
                window.showNotification(result.error || '删除失败', 'error');
            }
        } catch (error) {
            window.showNotification('删除请求失败: ' + error.message, 'error');
        }
    };
    
    // ==================== 搜索增强功能 ====================
    
    // 筛选与排序处理
    window.handleFilterSort = function(value) {
        if (!value) {
            // 全部类型 - 显示所有节点
            handleSearch();
            return;
        }
        
        if (value === 'sort_degree') {
            // 按度数排序
            showNodesByDegree();
            document.getElementById('searchTypeFilter').value = '';
        } else if (value.startsWith('type_')) {
            // 按类型筛选
            const type = value.substring(5);
            filterByType(type);
        }
    };
    
    // 按类型筛选节点
    function filterByType(type) {
        const nodes = currentGraphData.nodes || [];
        const filteredNodes = nodes.filter(n => (n.type || '未分类') === type);
        
        const resultsPanel = document.getElementById('searchResultsPanel');
        const resultsList = document.getElementById('searchResultList');
        const resultCount = document.getElementById('searchResultCount');
        
        if (resultsPanel && resultsList && resultCount) {
            resultCount.textContent = filteredNodes.length;
            resultsList.innerHTML = filteredNodes.map(node => `
                <div class="flex items-center gap-2 p-2 hover:bg-base-200 rounded cursor-pointer text-xs" onclick="highlightAndSelectNode('${node.nodeId}')">
                    <span class="flex-1 truncate">${node.name || '未命名'}</span>
                    <span class="badge badge-ghost badge-xs">${node.type || '未分类'}</span>
                </div>
            `).join('');
            resultsPanel.classList.remove('hidden');
        }
        
        window.showNotification(`找到 ${filteredNodes.length} 个 "${type}" 类型节点`, 'info');
    }
    
    // 按类型筛选搜索（兼容旧代码）
    window.filterSearchByType = function() {
        handleSearch();
    };
    
    // 高亮并选中节点
    window.highlightAndSelectNode = function(nodeId) {
        // 调用showCenteredGraphView更新图谱布局并选中节点
        showCenteredGraphView(nodeId);
    };
    
    // 打开邻居查询模态框
    window.openNeighborQueryModal = function() {
        const nodes = currentGraphData.nodes || [];
        // 按字典序排序
        const sortedNodes = [...nodes].sort(function(a, b) {
            const nameA = (a.name || '').toLowerCase();
            const nameB = (b.name || '').toLowerCase();
            return nameA.localeCompare(nameB, 'zh-CN');
        });
        
        // 使用 datalist 实现可输入搜索
        const datalistOptions = sortedNodes.map(function(n) {
            return '<option value="' + (n.name || '未命名') + '">';
        }).join('');
        document.getElementById('neighborTargetNodeList').innerHTML = datalistOptions;
        document.getElementById('neighborTargetNodeInput').value = '';
        
        // 保存节点映射供查询使用
        window._neighborQueryNodes = sortedNodes;
        
        document.getElementById('neighborResults').classList.add('hidden');
        document.getElementById('neighborQueryModal').showModal();
    };
    
    window.executeNeighborQuery = async function() {
        const graphId = getCurrentGraphId();
        const nodeName = document.getElementById('neighborTargetNodeInput').value.trim();
        const direction = document.getElementById('neighborDirection').value;
        
        if (!nodeName) {
            window.showNotification('请输入目标节点', 'warning');
            return;
        }
        
        // 通过名称查找节点ID
        const nodes = window._neighborQueryNodes || currentGraphData.nodes || [];
        const targetNode = nodes.find(function(n) { return n.name === nodeName; });
        if (!targetNode) {
            window.showNotification('未找到节点: ' + nodeName, 'warning');
            return;
        }
        const nodeId = targetNode.nodeId;
        
        try {
            const response = await fetch(`/api/graph/${graphId}/nodes/${nodeId}/neighbors?direction=${direction}`, {
                credentials: 'include'
            });
            
            if (!response.ok) {
                throw new Error('查询失败');
            }
            
            const neighbors = await response.json();
            
            const resultsContainer = document.getElementById('neighborResults');
            const resultList = document.getElementById('neighborResultList');
            
            if (neighbors.length === 0) {
                resultList.innerHTML = '<div class="text-sm text-base-content/50 text-center">该节点没有邻居</div>';
            } else {
                resultList.innerHTML = neighbors.map(node => `
                    <div class="flex items-center gap-2 p-2 hover:bg-base-200 rounded cursor-pointer" onclick="highlightAndSelectNode('${node.nodeId}'); document.getElementById('neighborQueryModal').close();">
                        <span class="flex-1">${node.name || '未命名'}</span>
                        <span class="badge badge-ghost badge-xs">${node.type || '未分类'}</span>
                    </div>
                `).join('');
            }
            
            resultsContainer.classList.remove('hidden');
            window.showNotification(`找到 ${neighbors.length} 个邻居节点`, 'success');
            
        } catch (error) {
            window.showNotification('邻居查询失败: ' + error.message, 'error');
        }
    };
    
    // 打开添加关系模态框
    window.openAddRelationModal = function() {
        const nodes = currentGraphData.nodes || [];
        // 按字典序排序
        const sortedNodes = [...nodes].sort(function(a, b) {
            const nameA = (a.name || '').toLowerCase();
            const nameB = (b.name || '').toLowerCase();
            return nameA.localeCompare(nameB, 'zh-CN');
        });
        
        // 使用 datalist 实现可输入搜索
        const datalistOptions = sortedNodes.map(function(n) {
            return '<option value="' + (n.name || '未命名') + '">';
        }).join('');
        
        document.getElementById('newRelationSourceList').innerHTML = datalistOptions;
        document.getElementById('newRelationTargetList').innerHTML = datalistOptions;
        document.getElementById('newRelationSourceInput').value = '';
        document.getElementById('newRelationTargetInput').value = '';
        document.getElementById('newRelationType').value = '关联';
        
        // 保存节点映射供查询使用
        window._addRelationNodes = sortedNodes;
        
        document.getElementById('addRelationModal').showModal();
    };
    
    window.executeAddRelation = async function() {
        const graphId = getCurrentGraphId();
        if (!graphId) {
            window.showNotification('请先选择图谱', 'error');
            return;
        }
        
        const sourceName = document.getElementById('newRelationSourceInput').value.trim();
        const targetName = document.getElementById('newRelationTargetInput').value.trim();
        const type = document.getElementById('newRelationType').value.trim() || '关联';
        
        if (!sourceName || !targetName) {
            window.showNotification('请输入源节点和目标节点', 'warning');
            return;
        }
        
        // 通过名称查找节点ID
        const nodes = window._addRelationNodes || currentGraphData.nodes || [];
        const sourceNode = nodes.find(function(n) { return n.name === sourceName; });
        const targetNode = nodes.find(function(n) { return n.name === targetName; });
        
        if (!sourceNode) {
            window.showNotification('未找到源节点: ' + sourceName, 'warning');
            return;
        }
        if (!targetNode) {
            window.showNotification('未找到目标节点: ' + targetName, 'warning');
            return;
        }
        
        const sourceNodeId = sourceNode.nodeId;
        const targetNodeId = targetNode.nodeId;
        
        if (sourceNodeId === targetNodeId) {
            window.showNotification('源节点和目标节点不能相同', 'warning');
            return;
        }
        
        try {
            const response = await fetch(`/api/graph/${graphId}/relations`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ sourceNodeId, targetNodeId, type })
            });
            
            const result = await response.json();
            if (response.ok && result.success) {
                window.showNotification('关系创建成功', 'success');
                document.getElementById('addRelationModal').close();
                const data = await loadGraphData(graphId);
                if (data) {
                    updatePageWithGraphData(data);
                    bindaoRenderGraph();
                    // 同步更新封面
                    setTimeout(function() { window.updateGraphCover(); }, 500);
                }
            } else {
                window.showNotification(result.error || '创建失败', 'error');
            }
        } catch (error) {
            window.showNotification('创建请求失败: ' + error.message, 'error');
        }
    };
    
    // ==================== 批量关系操作功能 ====================
    
    // 切换添加关系Tab
    window.switchRelationTab = function(mode) {
        const singleTab = document.getElementById('singleRelationTab');
        const batchTab = document.getElementById('batchRelationTab');
        const singleForm = document.getElementById('singleRelationForm');
        const batchForm = document.getElementById('batchRelationForm');
        
        if (mode === 'single') {
            singleTab.classList.add('tab-active');
            batchTab.classList.remove('tab-active');
            singleForm.classList.remove('hidden');
            batchForm.classList.add('hidden');
        } else {
            singleTab.classList.remove('tab-active');
            batchTab.classList.add('tab-active');
            singleForm.classList.add('hidden');
            batchForm.classList.remove('hidden');
        }
    };
    
    // 预览批量关系
    window.previewBatchRelations = function() {
        const input = document.getElementById('batchRelationInput').value.trim();
        const previewContainer = document.getElementById('batchRelationPreview');
        const previewList = document.getElementById('batchRelationPreviewList');
        const nodes = currentGraphData.nodes || [];
        
        if (!input) {
            previewContainer.classList.add('hidden');
            return;
        }
        
        const lines = input.split('\n').filter(l => l.trim());
        const relations = [];
        const errors = [];
        
        lines.forEach((line, idx) => {
            const parts = line.split(',').map(p => p.trim());
            if (parts.length < 2) {
                errors.push(`第${idx + 1}行格式错误`);
                return;
            }
            
            const sourceName = parts[0];
            const targetName = parts[1];
            const type = parts[2] || '关联';
            
            const sourceNode = nodes.find(n => n.name === sourceName);
            const targetNode = nodes.find(n => n.name === targetName);
            
            if (!sourceNode) {
                errors.push(`第${idx + 1}行：找不到源节点"${sourceName}"`);
            } else if (!targetNode) {
                errors.push(`第${idx + 1}行：找不到目标节点"${targetName}"`);
            } else {
                relations.push({ sourceNode, targetNode, type, sourceName, targetName });
            }
        });
        
        let html = '';
        relations.forEach(r => {
            html += `<div class="flex items-center gap-2 text-sm p-1 bg-base-100 rounded">
                <span class="badge badge-sm badge-primary">${r.sourceName}</span>
                <span class="text-base-content/50">→</span>
                <span class="badge badge-sm badge-ghost">${r.type}</span>
                <span class="text-base-content/50">→</span>
                <span class="badge badge-sm badge-secondary">${r.targetName}</span>
            </div>`;
        });
        
        if (errors.length > 0) {
            html += `<div class="mt-2 text-error text-xs">${errors.join('<br>')}</div>`;
        }
        
        html += `<div class="mt-2 text-xs text-base-content/60">共 ${relations.length} 条有效关系</div>`;
        
        previewList.innerHTML = html;
        previewContainer.classList.remove('hidden');
    };
    
    // 执行批量添加关系
    window.executeBatchAddRelations = async function() {
        const graphId = getCurrentGraphId();
        if (!graphId) {
            window.showNotification('请先选择图谱', 'error');
            return;
        }
        
        const input = document.getElementById('batchRelationInput').value.trim();
        if (!input) {
            window.showNotification('请输入关系数据', 'warning');
            return;
        }
        
        const nodes = currentGraphData.nodes || [];
        const lines = input.split('\n').filter(l => l.trim());
        const relations = [];
        
        lines.forEach(line => {
            const parts = line.split(',').map(p => p.trim());
            if (parts.length < 2) return;
            
            const sourceNode = nodes.find(n => n.name === parts[0]);
            const targetNode = nodes.find(n => n.name === parts[1]);
            
            if (sourceNode && targetNode) {
                relations.push({
                    sourceNodeId: sourceNode.nodeId,
                    targetNodeId: targetNode.nodeId,
                    type: parts[2] || '关联'
                });
            }
        });
        
        if (relations.length === 0) {
            window.showNotification('没有有效的关系数据', 'warning');
            return;
        }
        
        let successCount = 0;
        let failCount = 0;
        
        for (const rel of relations) {
            try {
                const response = await fetch(`/api/graph/${graphId}/relations`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify(rel)
                });
                
                if (response.ok) {
                    const result = await response.json();
                    if (result.success) successCount++;
                    else failCount++;
                } else {
                    failCount++;
                }
            } catch (error) {
                failCount++;
            }
        }
        
        if (successCount > 0) {
            window.showNotification(`成功添加 ${successCount} 条关系${failCount > 0 ? `，${failCount} 条失败` : ''}`, successCount === relations.length ? 'success' : 'warning');
            document.getElementById('addRelationModal').close();
            document.getElementById('batchRelationInput').value = '';
            document.getElementById('batchRelationPreview').classList.add('hidden');
            
            const data = await loadGraphData(graphId);
            if (data) {
                updatePageWithGraphData(data);
                bindaoRenderGraph();
                // 同步更新封面
                setTimeout(function() { window.updateGraphCover(); }, 500);
            }
        } else {
            window.showNotification('批量添加失败', 'error');
        }
    };
    
    // ==================== 删除关系功能 ====================
    
    let deleteRelationData = []; // 存储关系数据用于删除
    
    // 打开删除关系模态框
    window.openDeleteRelationModal = function() {
        const edges = currentGraphData.edges || [];
        const nodes = currentGraphData.nodes || [];
        
        // 获取所有关系类型
        const types = [...new Set(edges.map(e => e.type || '关联'))];
        const typeSelect = document.getElementById('deleteRelationTypeFilter');
        typeSelect.innerHTML = '<option value="">全部类型</option>' + 
            types.map(t => `<option value="${t}">${t}</option>`).join('');
        
        // 构建关系列表数据
        deleteRelationData = edges.map(e => {
            const sourceNode = nodes.find(n => n.nodeId === e.sourceNodeId);
            const targetNode = nodes.find(n => n.nodeId === e.targetNodeId);
            return {
                ...e,
                sourceName: sourceNode?.name || '未知',
                targetName: targetNode?.name || '未知',
                selected: false
            };
        });
        
        document.getElementById('deleteRelationSearchInput').value = '';
        renderDeleteRelationList();
        document.getElementById('deleteRelationModal').showModal();
    };
    
    // 渲染删除关系列表
    function renderDeleteRelationList() {
        const container = document.getElementById('deleteRelationList');
        const typeFilter = document.getElementById('deleteRelationTypeFilter').value;
        const searchInput = document.getElementById('deleteRelationSearchInput').value.toLowerCase();
        
        let filtered = deleteRelationData;
        
        if (typeFilter) {
            filtered = filtered.filter(r => (r.type || '关联') === typeFilter);
        }
        
        if (searchInput) {
            filtered = filtered.filter(r => 
                r.sourceName.toLowerCase().includes(searchInput) ||
                r.targetName.toLowerCase().includes(searchInput)
            );
        }
        
        if (filtered.length === 0) {
            container.innerHTML = '<div class="text-sm text-base-content/50 text-center py-4">没有找到匹配的关系</div>';
        } else {
            container.innerHTML = filtered.map((r, idx) => `
                <label class="flex items-center gap-2 p-2 hover:bg-base-200 rounded cursor-pointer">
                    <input type="checkbox" class="checkbox checkbox-sm checkbox-error" 
                           data-relation-idx="${deleteRelationData.indexOf(r)}"
                           ${r.selected ? 'checked' : ''}
                           onchange="toggleDeleteRelation(${deleteRelationData.indexOf(r)})">
                    <span class="badge badge-sm badge-outline">${r.sourceName}</span>
                    <span class="text-xs text-base-content/50">→</span>
                    <span class="badge badge-xs badge-ghost">${r.type || '关联'}</span>
                    <span class="text-xs text-base-content/50">→</span>
                    <span class="badge badge-sm badge-outline">${r.targetName}</span>
                </label>
            `).join('');
        }
        
        updateDeleteRelationCount();
    }
    
    // 筛选删除关系
    window.filterDeleteRelations = function() {
        renderDeleteRelationList();
    };
    
    // 切换关系选中状态
    window.toggleDeleteRelation = function(idx) {
        deleteRelationData[idx].selected = !deleteRelationData[idx].selected;
        updateDeleteRelationCount();
    };
    
    // 全选
    window.selectAllDeleteRelations = function() {
        const typeFilter = document.getElementById('deleteRelationTypeFilter').value;
        const searchInput = document.getElementById('deleteRelationSearchInput').value.toLowerCase();
        
        deleteRelationData.forEach(r => {
            let match = true;
            if (typeFilter && (r.type || '关联') !== typeFilter) match = false;
            if (searchInput && !r.sourceName.toLowerCase().includes(searchInput) && !r.targetName.toLowerCase().includes(searchInput)) match = false;
            if (match) r.selected = true;
        });
        
        renderDeleteRelationList();
    };
    
    // 取消全选
    window.deselectAllDeleteRelations = function() {
        deleteRelationData.forEach(r => r.selected = false);
        renderDeleteRelationList();
    };
    
    // 更新选中数量
    function updateDeleteRelationCount() {
        const count = deleteRelationData.filter(r => r.selected).length;
        document.getElementById('selectedDeleteRelationCount').textContent = `已选择 ${count} 个关系`;
    }
    
    // 执行批量删除关系
    window.executeBatchDeleteRelations = async function() {
        const graphId = getCurrentGraphId();
        if (!graphId) {
            window.showNotification('请先选择图谱', 'error');
            return;
        }
        
        const selected = deleteRelationData.filter(r => r.selected);
        if (selected.length === 0) {
            window.showNotification('请选择要删除的关系', 'warning');
            return;
        }
        
        if (!confirm(`确定要删除选中的 ${selected.length} 条关系吗？此操作不可恢复。`)) {
            return;
        }
        
        let successCount = 0;
        let failCount = 0;
        
        for (const rel of selected) {
            try {
                const response = await fetch(`/api/graph/${graphId}/relations/${rel.relationId || rel.edgeId}`, {
                    method: 'DELETE',
                    credentials: 'include'
                });
                
                if (response.ok) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (error) {
                failCount++;
            }
        }
        
        if (successCount > 0) {
            window.showNotification(`成功删除 ${successCount} 条关系${failCount > 0 ? `，${failCount} 条失败` : ''}`, successCount === selected.length ? 'success' : 'warning');
            document.getElementById('deleteRelationModal').close();
            
            const data = await loadGraphData(graphId);
            if (data) {
                updatePageWithGraphData(data);
                bindaoRenderGraph();
                // 同步更新封面
                setTimeout(function() { window.updateGraphCover(); }, 500);
            }
        } else {
            window.showNotification('删除失败', 'error');
        }
    };

    // ==================== 节点详情显示功能 ====================
    
    // 更新节点详情面板
    window.updateNodeDetailPanel = function(node) {
        const container = document.getElementById('nodeDetailContent');
        if (!container || !node) return;
        
        // 从实际数据中获取完整节点信息（包括description）
        const nodeId = node.id || node.nodeId;
        const fullNodeData = (currentGraphData.nodes || []).find(n => n.nodeId === nodeId);
        const description = node.description || fullNodeData?.description || '';
        
        // 获取该节点的关联节点
        const relatedNodes = getRelatedNodesFromData(nodeId);
        const connectionCount = getNodeConnectionCount(nodeId);
        
        // 获取节点颜色
        const nodeColor = getNodeColor(node.type || 'default');
        
        container.innerHTML = `
            <div class="space-y-4">
                <!-- 节点基本信息 -->
                <div class="flex items-start gap-3">
                    <div class="w-10 h-10 rounded-full flex items-center justify-center text-white font-semibold text-sm" style="background-color: ${nodeColor};">
                        ${(node.label || node.name || '?').charAt(0).toUpperCase()}
                    </div>
                    <div class="flex-1 min-w-0">
                        <h4 class="font-semibold text-base-content truncate">${node.label || node.name || '未命名'}</h4>
                        <span class="badge badge-sm badge-ghost">${node.type || '未分类'}</span>
                    </div>
                </div>
                
                <!-- 统计信息 -->
                <div class="grid grid-cols-2 gap-2">
                    <div class="bg-base-100 rounded-lg p-3 text-center">
                        <div class="text-lg font-semibold text-primary">${connectionCount}</div>
                        <div class="text-xs text-base-content/60">连接数</div>
                    </div>
                    <div class="bg-base-100 rounded-lg p-3 text-center">
                        <div class="text-lg font-semibold text-secondary">${relatedNodes.length}</div>
                        <div class="text-xs text-base-content/60">相邻节点</div>
                    </div>
                </div>
                
                <!-- 节点简介 -->
                <div class="bg-base-100 rounded-lg p-3">
                    <div class="text-xs text-base-content/60 mb-1">简介</div>
                    <div class="text-sm text-base-content/80">${description || '<span class="text-base-content/40">暂无简介</span>'}</div>
                </div>
                
                <!-- 节点ID -->
                <div class="bg-base-100 rounded-lg p-3">
                    <div class="text-xs text-base-content/60 mb-1">节点ID</div>
                    <div class="text-sm font-mono text-base-content/80 break-all">${nodeId || '-'}</div>
                </div>
                
                <!-- 相邻节点列表 -->
                ${relatedNodes.length > 0 ? `
                <div>
                    <div class="text-xs text-base-content/60 mb-2">相邻节点</div>
                    <div class="space-y-1 max-h-40 overflow-y-auto">
                        ${relatedNodes.slice(0, 10).map(rn => `
                            <div class="flex items-center gap-2 p-2 bg-base-100 rounded hover:bg-base-300 cursor-pointer text-sm" onclick="showCenteredGraphView('${rn.id}')">
                                <div class="w-2 h-2 rounded-full" style="background-color: ${getNodeColor(rn.type || 'default')};"></div>
                                <span class="flex-1 truncate">${rn.name}</span>
                            </div>
                        `).join('')}
                        ${relatedNodes.length > 10 ? `<div class="text-xs text-center text-base-content/50 py-1">还有 ${relatedNodes.length - 10} 个节点...</div>` : ''}
                    </div>
                </div>
                ` : ''}
                
                <!-- 操作按钮 -->
                <div class="pt-2 border-t academic-border space-y-2">
                    <button class="btn btn-primary btn-sm w-full" onclick="showCenteredGraphView('${nodeId}')">
                        <span class="iconify" data-icon="heroicons:viewfinder-circle" data-width="16"></span>
                        以此节点为中心
                    </button>
                    <button class="btn btn-outline btn-sm w-full" onclick="openEditNodeModal('${nodeId}')">
                        <span class="iconify" data-icon="heroicons:pencil-square" data-width="16"></span>
                        编辑节点
                    </button>
                </div>
            </div>
        `;
    };
    
    // ==================== 编辑节点功能 ====================
    
    // 打开编辑节点模态框
    window.openEditNodeModal = function(nodeId) {
        const nodes = currentGraphData.nodes || [];
        const node = nodes.find(n => n.nodeId === nodeId);
        
        if (!node) {
            window.showNotification('找不到该节点', 'error');
            return;
        }
        
        document.getElementById('editNodeId').value = nodeId;
        document.getElementById('editNodeName').value = node.name || '';
        document.getElementById('editNodeType').value = node.type || '';
        document.getElementById('editNodeDescription').value = node.description || '';
        
        document.getElementById('editNodeModal').showModal();
    };
    
    // 执行编辑节点
    window.executeEditNode = async function() {
        const graphId = getCurrentGraphId();
        const nodeId = document.getElementById('editNodeId').value;
        
        if (!graphId || !nodeId) {
            window.showNotification('参数错误', 'error');
            return;
        }
        
        const name = document.getElementById('editNodeName').value.trim();
        const type = document.getElementById('editNodeType').value.trim();
        const description = document.getElementById('editNodeDescription').value.trim();
        
        if (!name) {
            window.showNotification('节点名称不能为空', 'warning');
            return;
        }
        
        try {
            const response = await fetch(`/api/graph/${graphId}/nodes/${nodeId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ name, type, description })
            });
            
            const result = await response.json();
            if (response.ok && result.success) {
                window.showNotification('节点更新成功', 'success');
                document.getElementById('editNodeModal').close();
                
                // 重新加载图谱数据
                const data = await loadGraphData(graphId);
                if (data) {
                    updatePageWithGraphData(data);
                    bindaoRenderGraph();
                    
                    // 更新右侧详情面板
                    const updatedNode = data.nodes.find(n => n.nodeId === nodeId);
                    if (updatedNode && window.updateNodeDetailPanel) {
                        window.updateNodeDetailPanel({
                            id: updatedNode.nodeId,
                            nodeId: updatedNode.nodeId,
                            name: updatedNode.name,
                            label: updatedNode.name,
                            type: updatedNode.type,
                            description: updatedNode.description
                        });
                    }
                    // 同步更新封面
                    setTimeout(function() { window.updateGraphCover(); }, 500);
                }
            } else {
                window.showNotification(result.error || '更新失败', 'error');
            }
        } catch (error) {
            window.showNotification('更新请求失败: ' + error.message, 'error');
        }
    };

    // ==================== 封面缩略图同步更新功能 ====================
    
    /**
     * 生成缩略图 SVG（使用当前布局）
     */
    function generateThumbnailSVG(width, height) {
        width = width || 400;
        height = height || 300;
        
        const nodes = currentGraphData.nodes || [];
        const edges = currentGraphData.edges || [];
        const mainPositions = currentGraphData.nodePositions || {};
        
        if (nodes.length === 0) {
            return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + width + ' ' + height + '"><rect width="100%" height="100%" fill="#f8fafc"/><text x="50%" y="50%" text-anchor="middle" fill="#9ca3af">暂无数据</text></svg>';
        }
        
        let nodePositions = {};
        const hasMainPositions = Object.keys(mainPositions).length > 0;
        
        if (hasMainPositions) {
            // 使用主图谱布局，缩放到缩略图尺寸
            const xs = Object.values(mainPositions).map(function(p) { return p.x; });
            const ys = Object.values(mainPositions).map(function(p) { return p.y; });
            const minX = Math.min.apply(null, xs), maxX = Math.max.apply(null, xs);
            const minY = Math.min.apply(null, ys), maxY = Math.max.apply(null, ys);
            const graphWidth = maxX - minX || 1;
            const graphHeight = maxY - minY || 1;
            
            const padding = 30;
            const availWidth = width - padding * 2;
            const availHeight = height - padding * 2;
            const scale = Math.min(availWidth / graphWidth, availHeight / graphHeight);
            const offsetX = padding + (availWidth - graphWidth * scale) / 2;
            const offsetY = padding + (availHeight - graphHeight * scale) / 2;
            
            nodes.forEach(function(n) {
                const pos = mainPositions[n.nodeId];
                if (pos) {
                    nodePositions[n.nodeId] = {
                        x: (pos.x - minX) * scale + offsetX,
                        y: (pos.y - minY) * scale + offsetY
                    };
                }
            });
        } else {
            // 圆形布局回退
            const centerX = width / 2, centerY = height / 2;
            const radius = Math.min(width, height) / 2 - 30;
            nodes.forEach(function(n, i) {
                const angle = (2 * Math.PI * i) / nodes.length;
                nodePositions[n.nodeId] = {
                    x: centerX + radius * Math.cos(angle),
                    y: centerY + radius * Math.sin(angle)
                };
            });
        }
        
        let svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + width + ' ' + height + '">';
        svg += '<rect width="100%" height="100%" fill="#f8fafc"/>';
        
        // 绘制边
        edges.forEach(function(e) {
            const src = nodePositions[e.sourceNodeId];
            const tgt = nodePositions[e.targetNodeId];
            if (src && tgt) {
                svg += '<line x1="' + src.x + '" y1="' + src.y + '" x2="' + tgt.x + '" y2="' + tgt.y + '" stroke="#cbd5e1" stroke-width="1.5" opacity="0.6"/>';
            }
        });
        
        // 绘制节点
        nodes.forEach(function(n) {
            const pos = nodePositions[n.nodeId];
            if (pos) {
                const color = getNodeColor(n.type || 'default');
                svg += '<circle cx="' + pos.x + '" cy="' + pos.y + '" r="8" fill="' + color + '"/>';
            }
        });
        
        svg += '</svg>';
        return svg;
    }
    
    /**
     * SVG 转 PNG Blob
     */
    function svgToPngBlob(svgString, width, height) {
        return new Promise(function(resolve) {
            width = width || 400;
            height = height || 300;
            
            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            const ctx = canvas.getContext('2d');
            
            const img = new Image();
            const svgBlob = new Blob([svgString], { type: 'image/svg+xml;charset=utf-8' });
            const url = URL.createObjectURL(svgBlob);
            
            img.onload = function() {
                ctx.fillStyle = '#f8fafc';
                ctx.fillRect(0, 0, width, height);
                ctx.drawImage(img, 0, 0, width, height);
                URL.revokeObjectURL(url);
                
                canvas.toBlob(function(blob) {
                    resolve(blob);
                }, 'image/png');
            };
            
            img.onerror = function() {
                URL.revokeObjectURL(url);
                resolve(null);
            };
            
            img.src = url;
        });
    }
    
    /**
     * 更新图谱封面（在修改节点/边后调用）
     * 如果是用户自定义封面(isCustomCover=true)，则不自动更新
     */
    window.updateGraphCover = async function() {
        const graphId = getCurrentGraphId();
        if (!graphId) return;
        
        // 如果是用户自定义封面，不自动更新
        if (currentGraphData.isCustomCover) {
            return;
        }
        
        try {
            const svgString = generateThumbnailSVG(400, 300);
            const pngBlob = await svgToPngBlob(svgString, 400, 300);
            
            if (!pngBlob) return;
            
            const formData = new FormData();
            formData.append('file', pngBlob, 'cover.png');
            formData.append('isCustom', 'false'); // 自动生成的缩略图
            
            await fetch('/api/graph/' + graphId + '/cover', {
                method: 'PUT',
                credentials: 'include',
                body: formData
            });
        } catch (e) {
            // 封面更新失败，静默处理
        }
    };
    
    // 首次加载后自动更新封面（仅当非用户自定义封面时）
    setTimeout(function() {
        if (window.updateGraphCover && currentGraphData.nodePositions && Object.keys(currentGraphData.nodePositions).length > 0) {
            // 只有非自定义封面才自动更新
            if (!currentGraphData.isCustomCover) {
                window.updateGraphCover();
            }
        }
    }, 3000);

})();

// ==================== 侧边栏折叠和拖拽调整功能 ====================

// 左侧栏折叠/展开
function toggleLeftSidebar() {
    var sidebar = document.getElementById('leftSidebar');
    var icon = document.getElementById('leftToggleIcon');
    if (sidebar.classList.contains('collapsed')) {
        sidebar.classList.remove('collapsed');
        sidebar.style.width = '320px';
        icon.setAttribute('data-icon', 'heroicons:chevron-left');
    } else {
        sidebar.classList.add('collapsed');
        icon.setAttribute('data-icon', 'heroicons:chevron-right');
    }
    setTimeout(function() { window.dispatchEvent(new Event('resize')); }, 350);
}

// 右侧栏折叠/展开
function toggleRightSidebar() {
    var sidebar = document.getElementById('rightSidebar');
    var icon = document.getElementById('rightToggleIcon');
    if (sidebar.classList.contains('collapsed')) {
        sidebar.classList.remove('collapsed');
        sidebar.style.width = '300px';
        icon.setAttribute('data-icon', 'heroicons:chevron-right');
    } else {
        sidebar.classList.add('collapsed');
        icon.setAttribute('data-icon', 'heroicons:chevron-left');
    }
    setTimeout(function() { window.dispatchEvent(new Event('resize')); }, 350);
}

// 左侧栏拖拽调整
(function initLeftResize() {
    var handle = document.getElementById('leftResizeHandle');
    var sidebar = document.getElementById('leftSidebar');
    if (!handle || !sidebar) return;
    
    var isResizing = false;
    
    handle.addEventListener('mousedown', function(e) {
        isResizing = true;
        handle.classList.add('active');
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none';
        e.preventDefault();
    });
    
    document.addEventListener('mousemove', function(e) {
        if (!isResizing) return;
        var newWidth = e.clientX;
        if (newWidth >= 200 && newWidth <= 500) {
            sidebar.style.width = newWidth + 'px';
            sidebar.classList.remove('collapsed');
        }
    });
    
    document.addEventListener('mouseup', function() {
        if (isResizing) {
            isResizing = false;
            handle.classList.remove('active');
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
        }
    });
})();

// 右侧栏拖拽调整
(function initRightResize() {
    var handle = document.getElementById('rightResizeHandle');
    var sidebar = document.getElementById('rightSidebar');
    if (!handle || !sidebar) return;
    
    var isResizing = false;
    
    handle.addEventListener('mousedown', function(e) {
        isResizing = true;
        handle.classList.add('active');
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none';
        e.preventDefault();
    });
    
    document.addEventListener('mousemove', function(e) {
        if (!isResizing) return;
        var newWidth = window.innerWidth - e.clientX;
        if (newWidth >= 200 && newWidth <= 500) {
            sidebar.style.width = newWidth + 'px';
            sidebar.classList.remove('collapsed');
        }
    });
    
    document.addEventListener('mouseup', function() {
        if (isResizing) {
            isResizing = false;
            handle.classList.remove('active');
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
        }
    });
})();
