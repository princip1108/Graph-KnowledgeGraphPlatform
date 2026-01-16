/**
 * Graph Detail Query Module
 * 图谱详情页查询模块 - 路径查询、邻居查询、子图分析
 */

(function() {
    'use strict';

    // 当前选中的节点ID
    let currentSelectedNodeId = null;
    
    // 设置视图控制
    window.setupViewControls = function() {
        const depthRange = document.getElementById('depthRange');
        if (depthRange) {
            depthRange.addEventListener('input', function() {
                const value = parseInt(this.value);
                if (currentSelectedNodeId) {
                    const direction = document.getElementById('neighborDirectionSelect')?.value || 'all';
                    highlightNeighborsByDepth(currentSelectedNodeId, value, direction);
                }
            });
        }
    };
    
    // 高亮节点的N层邻居（支持方向：all/out/in）
    function highlightNeighborsByDepth(nodeId, depth, direction) {
        direction = direction || 'all';
        const nodes = window.currentGraphData?.nodes || [];
        const edges = window.currentGraphData?.edges || [];
        
        // 根据方向构建邻接表
        const adjacencyList = {};
        nodes.forEach(n => adjacencyList[n.nodeId] = []);
        edges.forEach(edge => {
            if (direction === 'all' || direction === 'out') {
                if (adjacencyList[edge.sourceNodeId]) {
                    adjacencyList[edge.sourceNodeId].push(edge.targetNodeId);
                }
            }
            if (direction === 'all' || direction === 'in') {
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
        
        document.querySelectorAll('[data-label-for]').forEach(label => {
            const nId = label.getAttribute('data-label-for');
            label.style.opacity = visitedNodes.has(nId) ? '1' : '0.2';
        });
        
        document.querySelectorAll('.graph-edge').forEach(edge => {
            const source = edge.getAttribute('data-edge-source');
            const target = edge.getAttribute('data-edge-target');
            const isHighlighted = highlightedEdges.has(`${source}-${target}`) || highlightedEdges.has(`${target}-${source}`);
            edge.style.opacity = isHighlighted ? '0.7' : '0.1';
        });
        
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
    window.handleNeighborDirectionChange = function() {
        if (currentSelectedNodeId) {
            const depth = parseInt(document.getElementById('depthRange').value);
            const direction = document.getElementById('neighborDirectionSelect').value;
            highlightNeighborsByDepth(currentSelectedNodeId, depth, direction);
        }
    };
    
    // 节点排序查询
    window.handleNodeQuery = function(value) {
        if (!value) return;
        
        if (value === 'degree') {
            showNodesByDegree();
        }
        
        document.getElementById('nodeQuerySelect').value = '';
    };
    
    // 显示按度数排序的节点列表
    window.showNodesByDegree = function() {
        const nodes = window.currentGraphData?.nodes || [];
        const edges = window.currentGraphData?.edges || [];
        
        const degreeMap = {};
        nodes.forEach(n => degreeMap[n.nodeId] = 0);
        edges.forEach(edge => {
            if (degreeMap[edge.sourceNodeId] !== undefined) degreeMap[edge.sourceNodeId]++;
            if (degreeMap[edge.targetNodeId] !== undefined) degreeMap[edge.targetNodeId]++;
        });
        
        const sortedNodes = nodes.map(n => ({
            ...n,
            degree: degreeMap[n.nodeId] || 0
        })).sort((a, b) => b.degree - a.degree);
        
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
    };
    
    // 路径查询
    window.handlePathQuery = function(value) {
        if (!value) return;
        
        if (value === 'relation' || value === 'shortest') {
            openPathQueryModal(value);
        }
        
        document.getElementById('pathQuerySelect').value = '';
    };
    
    // 打开路径查询模态框
    function openPathQueryModal(queryType) {
        const typeNames = {
            'relation': '关系寻路',
            'shortest': '最短路径'
        };
        
        let modal = document.getElementById('pathQueryModal');
        if (!modal) {
            modal = document.createElement('dialog');
            modal.id = 'pathQueryModal';
            modal.className = 'modal';
            document.body.appendChild(modal);
        }
        
        const nodes = window.currentGraphData?.nodes || [];
        const sortedNodes = [...nodes].sort((a, b) => {
            const nameA = (a.name || '').toLowerCase();
            const nameB = (b.name || '').toLowerCase();
            return nameA.localeCompare(nameB, 'zh-CN');
        });
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
        
        const nodes = window._pathQueryNodes || window.currentGraphData?.nodes || [];
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
        const edges = window.currentGraphData?.edges || [];
        const nodes = window.currentGraphData?.nodes || [];
        
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
        document.querySelectorAll('.graph-node').forEach(n => n.style.opacity = '0.2');
        document.querySelectorAll('.graph-edge').forEach(e => e.style.opacity = '0.1');
        document.querySelectorAll('.edge-label').forEach(l => l.style.opacity = '0.1');
        document.querySelectorAll('[data-label-for]').forEach(l => l.style.opacity = '0.2');
        
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
        
        const nodes = window.currentGraphData?.nodes || [];
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
    function findAllPaths(startId, endId, maxDepth) {
        maxDepth = maxDepth || 5;
        const edges = window.currentGraphData?.edges || [];
        const nodes = window.currentGraphData?.nodes || [];
        
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
    
    // 打开邻居查询模态框
    window.openNeighborQueryModal = function() {
        const nodes = window.currentGraphData?.nodes || [];
        const sortedNodes = [...nodes].sort((a, b) => {
            const nameA = (a.name || '').toLowerCase();
            const nameB = (b.name || '').toLowerCase();
            return nameA.localeCompare(nameB, 'zh-CN');
        });
        
        const datalistOptions = sortedNodes.map(n => `<option value="${n.name || '未命名'}">`).join('');
        document.getElementById('neighborTargetNodeList').innerHTML = datalistOptions;
        document.getElementById('neighborTargetNodeInput').value = '';
        
        window._neighborQueryNodes = sortedNodes;
        
        document.getElementById('neighborResults').classList.add('hidden');
        document.getElementById('neighborQueryModal').showModal();
    };
    
    window.executeNeighborQuery = async function() {
        const graphId = window.getCurrentGraphId ? window.getCurrentGraphId() : null;
        const nodeName = document.getElementById('neighborTargetNodeInput').value.trim();
        const direction = document.getElementById('neighborDirection').value;
        
        if (!nodeName) {
            window.showNotification('请输入目标节点', 'warning');
            return;
        }
        
        const nodes = window._neighborQueryNodes || window.currentGraphData?.nodes || [];
        const targetNode = nodes.find(n => n.name === nodeName);
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
    
    // 子图分析
    window.executeSubgraphAnalysis = function() {
        const analysisType = document.getElementById('subgraphAnalysisType')?.value || 'connected';
        
        if (analysisType === 'connected') {
            findConnectedComponents();
        } else if (analysisType === 'core') {
            findCoreNodes();
        }
    };
    
    // 查找连通分量
    function findConnectedComponents() {
        const nodes = window.currentGraphData?.nodes || [];
        const edges = window.currentGraphData?.edges || [];
        
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
        
        const visited = new Set();
        const components = [];
        
        for (const node of nodes) {
            if (!visited.has(node.nodeId)) {
                const component = [];
                const queue = [node.nodeId];
                
                while (queue.length > 0) {
                    const current = queue.shift();
                    if (visited.has(current)) continue;
                    
                    visited.add(current);
                    component.push(current);
                    
                    for (const neighbor of (adjacencyList[current] || [])) {
                        if (!visited.has(neighbor)) {
                            queue.push(neighbor);
                        }
                    }
                }
                
                components.push(component);
            }
        }
        
        const nodeNameMap = {};
        nodes.forEach(n => nodeNameMap[n.nodeId] = n.name);
        
        const resultsContainer = document.getElementById('subgraphResults');
        const resultList = document.getElementById('subgraphResultList');
        
        if (resultsContainer && resultList) {
            resultList.innerHTML = components.map((comp, idx) => `
                <div class="p-2 bg-base-100 rounded mb-2">
                    <div class="font-medium text-sm mb-1">连通分量 ${idx + 1} (${comp.length} 个节点)</div>
                    <div class="text-xs text-base-content/70">${comp.slice(0, 5).map(id => nodeNameMap[id] || id).join(', ')}${comp.length > 5 ? '...' : ''}</div>
                </div>
            `).join('');
            resultsContainer.classList.remove('hidden');
        }
        
        window.showNotification(`发现 ${components.length} 个连通分量`, 'success');
    }
    
    // 查找核心节点
    function findCoreNodes() {
        const nodes = window.currentGraphData?.nodes || [];
        const edges = window.currentGraphData?.edges || [];
        
        const degreeMap = {};
        nodes.forEach(n => degreeMap[n.nodeId] = 0);
        edges.forEach(edge => {
            if (degreeMap[edge.sourceNodeId] !== undefined) degreeMap[edge.sourceNodeId]++;
            if (degreeMap[edge.targetNodeId] !== undefined) degreeMap[edge.targetNodeId]++;
        });
        
        const sortedNodes = nodes.map(n => ({
            ...n,
            degree: degreeMap[n.nodeId] || 0
        })).sort((a, b) => b.degree - a.degree).slice(0, 10);
        
        const resultsContainer = document.getElementById('subgraphResults');
        const resultList = document.getElementById('subgraphResultList');
        
        if (resultsContainer && resultList) {
            resultList.innerHTML = sortedNodes.map((node, idx) => `
                <div class="flex items-center gap-2 p-2 hover:bg-base-200 rounded cursor-pointer" onclick="highlightAndSelectNode('${node.nodeId}')">
                    <span class="w-5 text-xs text-base-content/50">${idx + 1}</span>
                    <span class="flex-1 truncate">${node.name}</span>
                    <span class="badge badge-sm badge-primary">${node.degree}</span>
                </div>
            `).join('');
            resultsContainer.classList.remove('hidden');
        }
        
        window.showNotification(`显示度数最高的 ${sortedNodes.length} 个核心节点`, 'success');
    }

})();

