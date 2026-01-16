/**
 * Graph Detail Search Module
 * 图谱详情页搜索模块 - 搜索、筛选、高亮功能
 */

(function() {
    'use strict';

    // Search functionality setup
    window.setupSearchFunctionality = function() {
        const searchInput = document.getElementById('graphSearchInput');
        if (searchInput) {
            searchInput.addEventListener('input', handleSearch);
            searchInput.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') {
                    handleSearch();
                }
            });
        }
    };

    // 更新搜索类型筛选下拉框
    window.updateSearchTypeFilter = function(nodes) {
        nodes = nodes || (window.currentGraphData ? window.currentGraphData.nodes : []) || [];
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
    };

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
            if (window.originalLayout) {
                animateToNewLayout(window.originalLayout, null, new Set(), []);
                window.originalLayout = null;
            }
            return;
        }

        const nodes = window.currentGraphData?.nodes || [];
        let results = nodes;
        
        // 按类型筛选
        if (typeFilter && typeFilter.startsWith('type_')) {
            const type = typeFilter.substring(5);
            results = results.filter(node => (node.type || '未分类') === type);
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
     */
    window.showCenteredGraphView = function(centerNodeId) {
        const nodes = window.currentGraphData?.nodes || [];
        
        // 使用新布局系统重新渲染
        if (window.bindaoRenderGraph) {
            window.bindaoRenderGraph(centerNodeId);
        }
        
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
            if (window.selectNode) {
                window.selectNode(nodeInfo);
            }
            
            if (window.updateNodeDetailPanel) {
                window.updateNodeDetailPanel(nodeInfo);
            }
        }
        
        window.showNotification(`以"${centerNode?.name || '节点'}"为中心重新布局`, 'info');
    };

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
    window.clearSearchHighlight = clearSearchHighlight;

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
    window.restoreAllElementsOpacity = restoreAllElementsOpacity;
    
    // 重置图谱视图（恢复原始布局）
    window.resetGraphView = function() {
        const searchInput = document.getElementById('graphSearchInput');
        const typeFilter = document.getElementById('searchTypeFilter');
        if (searchInput) searchInput.value = '';
        if (typeFilter) typeFilter.value = '';
        
        const resultsPanel = document.getElementById('searchResultsPanel');
        if (resultsPanel) resultsPanel.classList.add('hidden');
        
        if (window.originalLayout) {
            animateToOriginalLayout();
        } else {
            restoreAllElementsOpacity();
            clearSearchHighlight();
        }
        
        window.showNotification('已恢复原始图谱布局', 'info');
    };
    
    // 动画恢复到原始布局
    function animateToOriginalLayout() {
        if (!window.originalLayout) return;
        
        const duration = 400;
        const startTime = performance.now();
        
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
            
            Object.keys(window.originalLayout).forEach(nodeId => {
                const start = currentPositions[nodeId];
                const end = window.originalLayout[nodeId];
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
                
                if (window.currentGraphData?.nodePositions?.[nodeId]) {
                    window.currentGraphData.nodePositions[nodeId].x = x;
                    window.currentGraphData.nodePositions[nodeId].y = y;
                }
            });
            
            if (window.updateAllEdges) {
                window.updateAllEdges();
            }
            
            if (progress < 1) {
                requestAnimationFrame(animate);
            } else {
                restoreAllElementsOpacity();
                clearSearchHighlight();
                window.originalLayout = null;
            }
        }
        
        requestAnimationFrame(animate);
    }

    // 高亮指定节点
    window.highlightNode = function(nodeId) {
        clearSearchHighlight();
        const node = document.querySelector(`[data-node-id="${nodeId}"]`);
        if (node) {
            node.style.stroke = 'var(--color-warning)';
            node.style.strokeWidth = '4px';
            node.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    };

    // 高亮并选中节点
    window.highlightAndSelectNode = function(nodeId) {
        showCenteredGraphView(nodeId);
    };

    // 搜索目标切换
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
        
        const edges = window.currentGraphData?.edges || [];
        const nodes = window.currentGraphData?.nodes || [];
        
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
        
        // 去重
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
        
        // 按关系类型分组
        const groupedByType = {};
        uniqueEdges.forEach(edge => {
            const type = edge.type || '关联';
            if (!groupedByType[type]) groupedByType[type] = [];
            groupedByType[type].push(edge);
        });
        
        const nodeNameMap = {};
        nodes.forEach(n => nodeNameMap[n.nodeId] = n.name);
        
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
        restoreAllElementsOpacity();
        
        document.querySelectorAll('.graph-node').forEach(n => n.style.opacity = '0.2');
        document.querySelectorAll('[data-label-for]').forEach(l => l.style.opacity = '0.2');
        document.querySelectorAll('.graph-edge').forEach(e => e.style.opacity = '0.1');
        document.querySelectorAll('.edge-label').forEach(l => l.style.opacity = '0.1');
        
        [sourceId, targetId].forEach(nodeId => {
            const node = document.querySelector(`[data-node-id="${nodeId}"]`);
            if (node) node.style.opacity = '1';
            const label = document.querySelector(`[data-label-for="${nodeId}"]`);
            if (label) label.style.opacity = '1';
        });
        
        document.querySelectorAll('.graph-edge').forEach(edge => {
            const source = edge.getAttribute('data-edge-source');
            const target = edge.getAttribute('data-edge-target');
            if ((source === sourceId && target === targetId) || (source === targetId && target === sourceId)) {
                edge.style.opacity = '1';
                edge.style.stroke = '#3b82f6';
                edge.style.strokeWidth = '3';
            }
        });
        
        document.querySelectorAll('.edge-label').forEach(label => {
            const source = label.getAttribute('data-label-source');
            const target = label.getAttribute('data-label-target');
            if ((source === sourceId && target === targetId) || (source === targetId && target === sourceId)) {
                label.style.opacity = '1';
            }
        });
    };

    // 筛选与排序处理
    window.handleFilterSort = function(value) {
        if (!value) {
            handleSearch();
            return;
        }
        
        if (value === 'sort_degree') {
            if (window.showNodesByDegree) {
                window.showNodesByDegree();
            }
            document.getElementById('searchTypeFilter').value = '';
        } else if (value.startsWith('type_')) {
            const type = value.substring(5);
            filterByType(type);
        }
    };
    
    // 按类型筛选节点
    function filterByType(type) {
        const nodes = window.currentGraphData?.nodes || [];
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

})();

