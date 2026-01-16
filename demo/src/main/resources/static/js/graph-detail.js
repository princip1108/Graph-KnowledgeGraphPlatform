/**
 * Graph Detail Page Logic
 * Handles graph initialization, visualization, filtering, and interaction.
 */

// Global State
let graph = null;
let highlightedNodes = new Set();
let highlightedLinks = new Set();
let originalGraphData = null; // Store original data for implementing filtering/reset

// Mock Data (In production this would come from an API)
const mockGraphData = {
    meta: {
        name: "人工智能知识图谱 (AI Knowledge Graph)",
        description: "包含人工智能领域核心概念、算法、任务及应用场景的知识图谱。展示了机器学习、深度学习、自然语言处理等子领域的层级关系。",
        author: { name: "GraphAdmin", avatar: "" },
        tags: ["人工智能", "计算机科学", "机器学习"],
        stats: { nodes: 156, relations: 284, density: 0.23, avgDegree: 3.6 }
    },
    nodes: [
        { id: "root", label: "人工智能", type: "核心概念", size: 40, color: "#ea580c" },
        { id: "ml", label: "机器学习", type: "子领域", size: 30, color: "#0ea5e9" },
        { id: "dl", label: "深度学习", type: "子领域", size: 30, color: "#0ea5e9" },
        { id: "nlp", label: "自然语言处理", type: "子领域", size: 30, color: "#0ea5e9" },
        { id: "cv", label: "计算机视觉", type: "子领域", size: 30, color: "#0ea5e9" },
        { id: "algo1", label: "神经网络", type: "算法", size: 20, color: "#8b5cf6" },
        { id: "algo2", label: "支持向量机", type: "算法", size: 20, color: "#8b5cf6" },
        { id: "algo3", label: "Transformer", type: "算法", size: 25, color: "#8b5cf6" },
        { id: "task1", label: "文本分类", type: "任务", size: 15, color: "#10b981" },
        { id: "task2", label: "图像识别", type: "任务", size: 15, color: "#10b981" }
    ],
    links: [
        { source: "root", target: "ml", type: "包含" },
        { source: "root", target: "dl", type: "包含" },
        { source: "root", target: "nlp", type: "包含" },
        { source: "root", target: "cv", type: "包含" },
        { source: "ml", target: "algo1", type: "使用" },
        { source: "ml", target: "algo2", type: "使用" },
        { source: "dl", target: "algo1", type: "依赖" },
        { source: "nlp", target: "algo3", type: "依赖" },
        { source: "nlp", target: "task1", type: "应用" },
        { source: "cv", target: "task2", type: "应用" }
    ]
};

// Generate more mock data for demo visual density
Array.from({ length: 146 }, (_, i) => ({
    id: `n_${i}`,
    label: `Concept ${i}`,
    type: i % 3 === 0 ? "概念" : (i % 3 === 1 ? "实体" : "应用"),
    size: 10 + Math.random() * 10,
    color: i % 3 === 0 ? "#f59e0b" : (i % 3 === 1 ? "#ec4899" : "#6366f1")
})).forEach(n => mockGraphData.nodes.push(n));

Array.from({ length: 274 }, (_, i) => ({
    source: i < 50 ? "root" : `n_${Math.floor(Math.random() * 146)}`,
    target: `n_${Math.floor(Math.random() * 146)}`,
    type: Math.random() > 0.5 ? "关联" : "属于"
})).forEach(l => mockGraphData.links.push(l));


// Initialize Graph
function initGraph() {
    const container = document.getElementById('graphCanvas');
    if (!container) return;
    
    const width = container.clientWidth;
    const height = container.clientHeight;

    // Safety Check for ForceGraph Library
    if (typeof ForceGraph === 'undefined') {
        console.error("ForceGraph library not loaded!");
        showErrorState("图谱组件加载失败 (Library Missing)");
        return;
    }

    try {
        graph = ForceGraph()(container)
            .width(width)
            .height(height)
            .backgroundColor('rgba(0,0,0,0)') // Transparent background to let CSS handle it
            .nodeId('id')
            .nodeLabel('label')
            .nodeVal('size')
            .nodeAutoColorBy('type')
            .linkSource('source')
            .linkTarget('target')
            .linkLabel('type')
            .linkDirectionalParticles(2)
            .linkDirectionalParticleWidth(2)
            .onNodeClick(handleNodeClick)
            .onNodeDragEnd(node => {
                node.fx = node.x;
                node.fy = node.y;
            })
            .onBackgroundClick(handleBackgroundClick);

        // Load data
        loadGraphData('g123'); // Demo ID

        // Handle window resize
        new ResizeObserver(() => {
            if (graph && container) {
                graph.width(container.clientWidth);
                graph.height(container.clientHeight);
            }
        }).observe(container);

    } catch (e) {
        console.error("ForceGraph init failed:", e);
        showErrorState("图谱初始化失败: " + e.message);
    }
}

function loadGraphData(graphId) {
    // Show loading state
    setLoadingState(true);

    // Simulate network request
    setTimeout(() => {
        try {
            originalGraphData = JSON.parse(JSON.stringify(mockGraphData)); // Deep copy
            updatePageWithGraphData(mockGraphData);
            
            if (graph) {
                bindaoRenderGraph(mockGraphData);
            } else {
                console.warn('Graph instance is null, cannot render data');
            }

            // Check if favorited if globe available
            if (window.APP_GLOBALS && window.APP_GLOBALS.favorites && window.APP_GLOBALS.favorites.isFavorited(graphId)) {
                const btn = document.getElementById('favoriteBtn');
                if (btn) {
                    btn.classList.add('text-red-500');
                    btn.querySelector('span').setAttribute('data-icon', 'heroicons:heart-solid');
                }
            }
        } catch (e) {
            console.error("Error loading graph data:", e);
            showErrorState("数据加载失败");
        } finally {
            // ALWAYS remove loading state
            setLoadingState(false);
        }
    }, 800);
}

function setLoadingState(isLoading) {
    const loader = document.querySelector('#graphCanvas .loading');
    if (!loader) return;
    // The loader is inside a centered div, so we hide/show the parent's parent usually or just the container
    // Structure: #graphCanvas > div > div(.loading)
    const container = loader.closest('.absolute'); 
    if (container) {
        if (isLoading) container.classList.remove('hidden');
        else container.classList.add('hidden');
    }
}

function showErrorState(msg) {
    setLoadingState(false);
    const container = document.getElementById('graphCanvas');
    if (container) {
        // Check if error msg already exists
        let errorDiv = container.querySelector('.error-state');
        if (!errorDiv) {
            errorDiv = document.createElement('div');
            errorDiv.className = 'error-state absolute inset-0 flex items-center justify-center pointer-events-none';
            container.appendChild(errorDiv);
        }
        errorDiv.innerHTML = `<div class="bg-base-100 p-4 rounded shadow-lg border border-error text-error flex items-center gap-2"><span class="iconify" data-icon="heroicons:exclamation-triangle"></span><span>${msg}</span></div>`;
    }
}

function bindaoRenderGraph(data) {
    if (!graph) return;
    try {
        graph.graphData(data);

        // Custom Node Rendering (Canvas API)
        graph.nodeCanvasObject((node, ctx, globalScale) => {
            const label = node.label || '';
            const fontSize = 12 / globalScale;
            ctx.font = `${fontSize}px Sans-Serif`;
            const textWidth = ctx.measureText(label).width;
            const bckgDimensions = [textWidth, fontSize].map(n => n + fontSize * 0.2); // some padding

            // Highlight logic
            const isHighlighted = highlightedNodes.has(node.id);

            ctx.fillStyle = isHighlighted ? '#ff0000' : (node.color || '#3b82f6');
            if (isHighlighted) {
                ctx.shadowColor = '#ff0000';
                ctx.shadowBlur = 10;
            } else {
                ctx.shadowBlur = 0;
            }

            ctx.beginPath();
            ctx.arc(node.x, node.y, 5, 0, 2 * Math.PI, false);
            ctx.fill();

            // Text
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillStyle = 'rgba(0, 0, 0, 0.8)'; // text color
            
            // Ensure contrast (Simple check, in real app might check theme)
            // const isDark = document.documentElement.getAttribute('data-theme') === 'business';
            // if (isDark) ctx.fillStyle = 'rgba(255, 255, 255, 0.8)';

            if (globalScale >= 0.8) { // Only show text when zoomed in enough
                ctx.fillText(label, node.x, node.y + 8);
            }

            node.__bckgDimensions = bckgDimensions; // to re-use in nodePointerAreaPaint
        });
    } catch (e) {
        console.error("Render failed:", e);
    }
}

function updatePageWithGraphData(data) {
    // INFO
    const titleEl = document.getElementById('graphTitle');
    if (titleEl) titleEl.textContent = data.meta.name;
    
    const descEl = document.getElementById('graphDescription');
    if (descEl) descEl.textContent = data.meta.description;
    
    const uploaderEl = document.getElementById('uploaderName');
    if (uploaderEl) uploaderEl.textContent = data.meta.author.name || "GraphAdmin";

    // TAGS
    const tagsContainer = document.getElementById('graphTags');
    if (tagsContainer) {
        tagsContainer.innerHTML = '';
        (data.meta.tags || []).forEach(tag => {
            const badge = document.createElement('div');
            badge.className = 'badge badge-primary badge-outline badge-sm';
            badge.textContent = tag;
            tagsContainer.appendChild(badge);
        });
    }

    // STATS
    const stats = data.meta.stats;
    const statsValues = document.querySelectorAll('.stat-value');
    if (statsValues.length >= 4 && stats) {
        statsValues[0].textContent = stats.nodes;
        statsValues[1].textContent = stats.relations;
        statsValues[2].textContent = stats.avgDegree;
        statsValues[3].textContent = stats.density;
    }

    // Entity Types Filters
    if (data.nodes) {
        const types = [...new Set(data.nodes.map(n => n.type))];
        const entityContainer = document.getElementById('entityTypeFilters');
        if (entityContainer) {
            entityContainer.innerHTML = '';
            types.forEach(type => {
                const div = document.createElement('div');
                div.className = 'form-control';
                div.innerHTML = `
                 <label class="cursor-pointer label justify-start gap-3">
                   <input type="checkbox" checked class="checkbox checkbox-primary checkbox-xs" onchange="toggleNodeType('${type}')" />
                   <span class="label-text">${type}</span>
                   <span class="badge badge-ghost badge-xs ml-auto">${data.nodes.filter(n => n.type === type).length}</span>
                 </label>
                `;
                entityContainer.appendChild(div);
            });
        }
    }

    // Relation Types Filters
    if (data.links) {
        const relTypes = [...new Set(data.links.map(l => l.type))];
        const relContainer = document.getElementById('relationTypeFilters');
        if (relContainer) {
            relContainer.innerHTML = '';
            relTypes.forEach(type => {
                const div = document.createElement('div');
                div.className = 'form-control';
                div.innerHTML = `
                 <label class="cursor-pointer label justify-start gap-3">
                   <input type="checkbox" checked class="checkbox checkbox-secondary checkbox-xs" onchange="toggleRelType('${type}')" />
                   <span class="label-text">${type}</span>
                   <span class="badge badge-ghost badge-xs ml-auto">${data.links.filter(l => l.type === type).length}</span>
                 </label>
                `;
                relContainer.appendChild(div);
            });
        }
    }

    // Populate Datalists for Modals
    if (data.nodes) updateDatalists(data.nodes);
    updateDeleteLists(data);
}

function updateDatalists(nodes) {
    // Neighbor Query
    const list1 = document.getElementById('neighborTargetNodeList');
    if (list1) list1.innerHTML = nodes.map(n => `<option value="${n.id} | ${n.label}"></option>`).join('');

    // Add Relation
    const list2 = document.getElementById('newRelationSourceList');
    if (list2) list2.innerHTML = nodes.map(n => `<option value="${n.id} | ${n.label}"></option>`).join('');
    const list3 = document.getElementById('newRelationTargetList');
    if (list3) list3.innerHTML = nodes.map(n => `<option value="${n.id} | ${n.label}"></option>`).join('');
}

function updateDeleteLists(data) {
    // Delete Node Filter
    const delType = document.getElementById('deleteTypeFilter');
    if (delType && data.nodes) {
        const types = [...new Set(data.nodes.map(n => n.type))];
        delType.innerHTML = '<option value="">全部类型</option>' + types.map(t => `<option value="${t}">${t}</option>`).join('');
    }

    // Delete Relation Filter
    const delRelType = document.getElementById('deleteRelationTypeFilter');
    if (delRelType && data.links) {
        const types = [...new Set(data.links.map(l => l.type))];
        delRelType.innerHTML = '<option value="">全部类型</option>' + types.map(t => `<option value="${t}">${t}</option>`).join('');
    }

    // Refresh lists if modals are open
    const batchDelModal = document.getElementById('batchDeleteModal');
    if (batchDelModal && batchDelModal.open) filterDeleteNodes();
    
    const delRelModal = document.getElementById('deleteRelationModal');
    if (delRelModal && delRelModal.open) filterDeleteRelations();
}

function zoomIn() {
    if (graph) {
        graph.zoom(graph.zoom() * 1.2, 500);
    }
}

function zoomOut() {
    if (graph) {
        graph.zoom(graph.zoom() / 1.2, 500);
    }
}

function resetView() {
    if (graph) {
        graph.centerAt(0, 0, 1000);
        graph.zoom(1, 1000);
    }
}

function resetGraphView() {
    highlightedNodes.clear();
    highlightedLinks.clear();
    // Restore all data
    if (originalGraphData) {
        bindaoRenderGraph(JSON.parse(JSON.stringify(originalGraphData)));
    }
    // Reset UI
    const searchPanel = document.getElementById('searchResultsPanel');
    if (searchPanel) searchPanel.classList.add('hidden');
    
    const searchInput = document.getElementById('graphSearchInput');
    if (searchInput) searchInput.value = '';
}

// Interaction Logic
function handleNodeClick(node) {
    // Show details in Right Sidebar
    const container = document.getElementById('nodeDetailContent');
    if (!container) return;

    container.innerHTML = `
       <div class="px-2 space-y-4">
         <div class="flex items-start justify-between">
            <div>
              <h2 class="text-xl font-bold text-primary">${node.label}</h2>
              <div class="badge badge-outline mt-1">${node.type}</div>
            </div>
            <div class="text-xs text-base-content/50">ID: ${node.id}</div>
         </div>
         
         <div class="divider my-1"></div>
         
         <div>
            <h4 class="text-sm font-semibold opacity-70 mb-1">简介</h4>
            <p class="text-sm leading-relaxed">${node.description || '暂无简介描述。'}</p>
         </div>
         
         <div class="grid grid-cols-2 gap-2 text-sm">
            <div class="bg-base-100 p-2 rounded">
              <div class="text-xs opacity-60">度数</div>
              <div class="font-mono text-lg">${(node.val || 0).toFixed(0)}</div>
            </div>
            <div class="bg-base-100 p-2 rounded">
              <div class="text-xs opacity-60">权重</div>
              <div class="font-mono text-lg">${(node.size || 0).toFixed(1)}</div>
            </div>
         </div>
         
         <div class="flex flex-col gap-2 pt-2">
           <button class="btn btn-primary btn-sm btn-outline w-full" onclick="openEditNodeModal('${node.id}')">
             <span class="iconify" data-icon="heroicons:pencil-square"></span> 编辑属性
           </button>
           <button class="btn btn-sm btn-outline btn-error w-full" onclick="confirmDeleteNode('${node.id}')">
             <span class="iconify" data-icon="heroicons:trash"></span> 删除节点
           </button>
         </div>
         
         <div class="text-xs text-center text-base-content/30 pt-4">
           Click background to clear selection
         </div>
       </div>
    `;

    // Open sidebar if closed
    const rightSidebar = document.getElementById('rightSidebar');
    if (rightSidebar && rightSidebar.classList.contains('collapsed')) {
        toggleRightSidebar();
    }

    // Highlight in Graph
    highlightNode(node);
}

function handleBackgroundClick() {
    const container = document.getElementById('nodeDetailContent');
    if (container) {
        container.innerHTML = `
        <div class="text-center text-base-content/50 py-8">
            <span class="iconify mx-auto mb-3" data-icon="heroicons:cursor-arrow-rays" data-width="32"></span>
            <p class="text-sm">点击图谱中的节点</p>
            <p class="text-xs mt-1">查看详细信息</p>
        </div>
     `;
    }
    highlightedNodes.clear();
    highlightedLinks.clear(); 
}

function highlightNode(node) {
    if (!graph) return;
    highlightedNodes.clear();
    highlightedNodes.add(node.id);
    
    // Find neighbors based on current VISIBLE graph data
    const currentData = graph.graphData();
    if (currentData && currentData.links) {
        const neighbors = new Set();
        currentData.links.forEach(link => {
            const sId = typeof link.source === 'object' ? link.source.id : link.source;
            const tId = typeof link.target === 'object' ? link.target.id : link.target;
            
            if (sId === node.id) neighbors.add(tId);
            if (tId === node.id) neighbors.add(sId);
        });
        neighbors.forEach(id => highlightedNodes.add(id));
    }

    graph.centerAt(node.x, node.y, 1000);
    graph.zoom(3, 2000);
}

// Search
function updateSearchPlaceholder() {
    const type = document.getElementById('searchTargetSelect').value;
    const input = document.getElementById('graphSearchInput');
    if (input) input.placeholder = type === 'node' ? '搜索节点名称...' : '搜索关系类型...';
}

function searchInGraph() {
    const input = document.getElementById('graphSearchInput');
    const select = document.getElementById('searchTargetSelect');
    if (!input || !select) return;

    const query = input.value.toLowerCase();
    const type = select.value;

    if (!query) {
        resetGraphView();
        return;
    }

    const data = graph ? graph.graphData() : null;
    if (!data) return;
    
    const results = [];

    if (type === 'node') {
        data.nodes.forEach(node => {
            if (node.label.toLowerCase().includes(query)) {
                results.push({ type: 'node', data: node });
            }
        });
    } else {
        data.links.forEach(link => {
            const linkType = link.type || '';
            if (linkType.toLowerCase().includes(query)) {
                results.push({ type: 'link', data: link });
            }
        });
    }

    // Show results
    const resultPanel = document.getElementById('searchResultsPanel');
    const resultList = document.getElementById('searchResultList');
    const countSpan = document.getElementById('searchResultCount');

    if (resultPanel) resultPanel.classList.remove('hidden');
    if (countSpan) countSpan.textContent = results.length;
    
    if (resultList) {
        resultList.innerHTML = '';

        if (results.length === 0) {
            resultList.innerHTML = '<div class="text-xs text-center py-2">无匹配结果</div>';
        } else {
            results.forEach(item => {
                const el = document.createElement('div');
                el.className = 'p-2 text-xs bg-base-200 rounded hover:bg-primary hover:text-primary-content cursor-pointer transition';
                if (item.type === 'node') {
                    el.textContent = `[Node] ${item.data.label} (${item.data.type})`;
                    el.onclick = () => handleNodeClick(item.data);
                } else {
                    const sLabel = typeof item.data.source === 'object' ? item.data.source.label : item.data.source;
                    const tLabel = typeof item.data.target === 'object' ? item.data.target.label : item.data.target;
                    el.textContent = `[Link] ${sLabel} -> ${tLabel} (${item.data.type})`;
                    
                    el.onclick = () => {
                         const sNode = typeof item.data.source === 'object' ? item.data.source : data.nodes.find(n => n.id === item.data.source);
                         const tNode = typeof item.data.target === 'object' ? item.data.target : data.nodes.find(n => n.id === item.data.target);
                         if (sNode && tNode) {
                            graph.zoom(2, 1000);
                            graph.centerAt((sNode.x + tNode.x) / 2, (sNode.y + tNode.y) / 2, 1000);
                         }
                    };
                }
                resultList.appendChild(el);
            });
        }
    }
}

// Filter & Sort
function handleFilterSort(value) {
    if (value === 'sort_degree') {
        window.showNotification('已按度数排序（视觉大小更新）', 'success');
    }
}

// Path Query
function handlePathQuery(value) {
    if (value === 'shortest') {
        const start = prompt("输入起点节点ID (例如: root)", "root");
        const end = prompt("输入终点节点ID (例如: task1)", "task1");
        if (start && end) {
            findShortestPath(start, end);
        }
    }
}

function findShortestPath(startId, endId) {
    const data = graph ? graph.graphData() : null;
    if (!data) return;
    const links = data.links;

    // BFS
    const queue = [[startId]];
    const visited = new Set();
    visited.add(startId);

    let path = null;
    let maxIterations = 10000; 

    while (queue.length > 0 && maxIterations-- > 0) {
        const currentPath = queue.shift();
        const node = currentPath[currentPath.length - 1];

        if (node === endId) {
            path = currentPath;
            break;
        }

        // Find neighbors
        const neighbors = [];
        links.forEach(l => {
            const sId = typeof l.source === 'object' ? l.source.id : l.source;
            const tId = typeof l.target === 'object' ? l.target.id : l.target;
            
            if (sId === node && !visited.has(tId)) neighbors.push(tId);
            if (tId === node && !visited.has(sId)) neighbors.push(sId);
        });

        for (const neighbor of neighbors) {
            visited.add(neighbor);
            queue.push([...currentPath, neighbor]);
        }
    }

    if (path) {
        highlightPath(path);
        window.showNotification(`找到路径: ${path.length} 跳`, 'success');
    } else {
        window.showNotification('未找到路径', 'error');
    }
}

function highlightPath(nodeIds) {
    highlightedNodes.clear();
    nodeIds.forEach(id => highlightedNodes.add(id));
    // Center on path
    if (graph) {
        graph.zoomToFit(400, 100, node => nodeIds.includes(node.id));
    }
}

// Filter toggles
function toggleNodeType(type) {
    applyFilters();
}

function toggleRelType(type) {
    applyFilters();
}

function applyFilters() {
    if (!originalGraphData) return;

    const nodeCheckboxes = document.querySelectorAll('#entityTypeFilters input[type="checkbox"]');
    const activeNodeTypes = Array.from(nodeCheckboxes).filter(cb => cb.checked).map(cb => cb.nextElementSibling.innerText.trim()); 

    const relCheckboxes = document.querySelectorAll('#relationTypeFilters input[type="checkbox"]');
    const activeRelTypes = Array.from(relCheckboxes).filter(cb => cb.checked).map(cb => cb.nextElementSibling.innerText.trim());

    const filteredNodes = originalGraphData.nodes.filter(n => activeNodeTypes.includes(n.type));
    const filteredNodeIds = new Set(filteredNodes.map(n => n.id));

    // Links must have both source and target in filtered nodes AND match relation type
    const filteredLinks = originalGraphData.links.filter(l =>
        filteredNodeIds.has(typeof l.source === 'object' ? l.source.id : l.source) &&
        filteredNodeIds.has(typeof l.target === 'object' ? l.target.id : l.target) &&
        activeRelTypes.includes(l.type)
    );

    const newData = { ...originalGraphData, nodes: filteredNodes, links: filteredLinks };
    bindaoRenderGraph(newData);
}

// Actions
function createPostForGraph() {
    window.location.href = '/community/create?relatedGraphId=' + (graph ? 'g123' : '');
}

// Modals - Import
function openBatchImportModal() {
    const modal = document.getElementById('batchImportModal');
    if (modal) modal.showModal();
}

function switchImportTab(type) {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('tab-active'));
    const tab = document.getElementById(type + 'ImportTab');
    if (tab) tab.classList.add('tab-active');

    const sForm = document.getElementById('singleImportForm');
    const bForm = document.getElementById('batchImportForm');

    if (type === 'single') {
        if (sForm) sForm.classList.remove('hidden');
        if (bForm) bForm.classList.add('hidden');
    } else {
        if (sForm) sForm.classList.add('hidden');
        if (bForm) bForm.classList.remove('hidden');
    }
}

function executeImportNodes() {
    const tab = document.getElementById('batchImportTab');
    const isBatch = tab && tab.classList.contains('tab-active');
    let newNodes = [];

    if (isBatch) {
        const text = document.getElementById('batchImportInput').value;
        const lines = text.split('\n');
        lines.forEach(line => {
            if (line.trim()) {
                const parts = line.split(/[，,]/); 
                if (parts.length >= 2) {
                    newNodes.push({
                        id: 'new_' + Date.now() + Math.random(),
                        label: parts[0].trim(),
                        type: parts[1].trim(),
                        description: parts[2] ? parts[2].trim() : '',
                        size: parts[3] ? parseFloat(parts[3]) : 20,
                        color: '#6366f1' 
                    });
                }
            }
        });
    } else {
        const name = document.getElementById('singleNodeName').value;
        const type = document.getElementById('singleNodeType').value;
        if (!name) {
            window.showNotification('请输入节点名称', 'error');
            return;
        }
        newNodes.push({
            id: 'new_' + Date.now(),
            label: name,
            type: type || '未分类',
            description: document.getElementById('singleNodeDescription').value,
            size: 20,
            color: '#6366f1'
        });
    }

    if (newNodes.length > 0) {
        if (!graph) return;
        const data = graph.graphData();
        const updatedNodes = [...data.nodes, ...newNodes];

        if (originalGraphData) originalGraphData.nodes.push(...JSON.parse(JSON.stringify(newNodes)));

        graph.graphData({ nodes: updatedNodes, links: data.links });
        updatePageWithGraphData({ ...data, nodes: updatedNodes }); 

        window.showNotification(`成功添加 ${newNodes.length} 个节点`, 'success');
        document.getElementById('batchImportModal').close();
        document.getElementById('batchImportInput').value = '';
        document.getElementById('singleNodeName').value = '';
    }
}

// Modals - Delete
function openBatchDeleteModal() {
    document.getElementById('batchDeleteModal').showModal();
    filterDeleteNodes();
}

function filterDeleteNodes() {
    const type = document.getElementById('deleteTypeFilter').value;
    const search = document.getElementById('deleteSearchInput').value.toLowerCase();
    const list = document.getElementById('deleteNodeList');

    if (!graph) return;
    const data = graph.graphData();
    const nodes = data.nodes.filter(n => {
        const matchType = type ? n.type === type : true;
        const matchSearch = search ? n.label.toLowerCase().includes(search) : true;
        return matchType && matchSearch;
    });

    list.innerHTML = '';
    nodes.forEach(n => {
        const div = document.createElement('div');
        div.className = 'form-control bg-base-100 p-2 rounded border border-base-200';
        div.innerHTML = `
        <label class="cursor-pointer label justify-start gap-3">
          <input type="checkbox" class="checkbox checkbox-xs checkbox-error delete-node-checkbox" value="${n.id}" onchange="updateDeleteCount()">
          <span class="label-text flex-1">${n.label} <span class="badge badge-ghost badge-xs">${n.type}</span></span>
        </label>
      `;
        list.appendChild(div);
    });
    updateDeleteCount();
}

function updateDeleteCount() {
    const count = document.querySelectorAll('.delete-node-checkbox:checked').length;
    document.getElementById('selectedDeleteCount').textContent = `已选择 ${count} 个节点`;
}

function selectAllDeleteNodes() {
    document.querySelectorAll('.delete-node-checkbox').forEach(cb => cb.checked = true);
    updateDeleteCount();
}
function deselectAllDeleteNodes() {
    document.querySelectorAll('.delete-node-checkbox').forEach(cb => cb.checked = false);
    updateDeleteCount();
}
function executeBatchDelete() {
    const checkboxes = document.querySelectorAll('.delete-node-checkbox:checked');
    const idsToDelete = Array.from(checkboxes).map(cb => cb.value);

    if (idsToDelete.length === 0) {
        window.showNotification('请选择要删除的节点', 'warning');
        return;
    }

    if (confirm(`确定要删除选中的 ${idsToDelete.length} 个节点吗？这也会删除关联的边。`)) {
        const SetToDelete = new Set(idsToDelete);
        const data = graph.graphData();
        const newNodes = data.nodes.filter(n => !SetToDelete.has(n.id));
        const newLinks = data.links.filter(l => !SetToDelete.has(typeof l.source === 'object' ? l.source.id : l.source) && !SetToDelete.has(typeof l.target === 'object' ? l.target.id : l.target));

        if (originalGraphData) {
            originalGraphData.nodes = originalGraphData.nodes.filter(n => !SetToDelete.has(n.id));
            originalGraphData.links = originalGraphData.links.filter(l => !SetToDelete.has(typeof l.source === 'object' ? l.source.id : l.source) && !SetToDelete.has(typeof l.target === 'object' ? l.target.id : l.target));
        }

        graph.graphData({ nodes: newNodes, links: newLinks });
        updatePageWithGraphData({ ...data, nodes: newNodes, links: newLinks });

        window.showNotification('删除成功', 'success');
        document.getElementById('batchDeleteModal').close();
    }
}

// Modals - Neighbor Query
function openNeighborQueryModal() {
    document.getElementById('neighborQueryModal').showModal();
}
function executeNeighborQuery() {
    const inputVal = document.getElementById('neighborTargetNodeInput').value;
    const direction = document.getElementById('neighborDirection').value;

    const nodeId = inputVal.split('|')[0].trim();

    if (!nodeId) {
        window.showNotification('请选择节点', 'error');
        return;
    }

    if (!graph) return;
    const data = graph.graphData();
    const targetNode = data.nodes.find(n => n.id === nodeId);

    if (!targetNode) {
        window.showNotification('未找到节点', 'error');
        return;
    }

    const neighbors = new Set();
    data.links.forEach(link => {
        const sId = typeof link.source === 'object' ? link.source.id : link.source;
        const tId = typeof link.target === 'object' ? link.target.id : link.target;

        if (sId === nodeId && (direction === 'all' || direction === 'out')) {
            neighbors.add(tId);
        }
        if (tId === nodeId && (direction === 'all' || direction === 'in')) {
            neighbors.add(sId);
        }
    });

    const resultList = document.getElementById('neighborResultList');
    document.getElementById('neighborResults').classList.remove('hidden');
    resultList.innerHTML = '';

    if (neighbors.size === 0) {
        resultList.innerHTML = '<div class="text-xs text-center">无邻居节点</div>';
    } else {
        neighbors.forEach(nid => {
            const node = data.nodes.find(n => n.id === nid);
            if (node) {
                const div = document.createElement('div');
                div.className = 'p-2 bg-base-100 border rounded flex justify-between items-center';
                div.innerHTML = `<span>${node.label}</span> <span class="badge badge-sm">${node.type}</span>`;
                resultList.appendChild(div);
            }
        });
    }
}

// Modals - Add Relation
function openAddRelationModal() {
    document.getElementById('addRelationModal').showModal();
}
function switchRelationTab(type) {
    document.querySelectorAll('#addRelationModal .tab').forEach(t => t.classList.remove('tab-active'));
    document.getElementById(type + 'RelationTab').classList.add('tab-active');
    if (type === 'single') {
        document.getElementById('singleRelationForm').classList.remove('hidden');
        document.getElementById('batchRelationForm').classList.add('hidden');
    } else {
        document.getElementById('singleRelationForm').classList.add('hidden');
        document.getElementById('batchRelationForm').classList.remove('hidden');
    }
}
function executeAddRelation() {
    const sVal = document.getElementById('newRelationSourceInput').value;
    const tVal = document.getElementById('newRelationTargetInput').value;
    const rType = document.getElementById('newRelationType').value;

    const sId = sVal.split('|')[0].trim();
    const tId = tVal.split('|')[0].trim();

    if (!sId || !tId) { window.showNotification('请选择源节点和目标节点', 'error'); return; }

    if (!graph) return;
    const data = graph.graphData();

    const newLink = { source: sId, target: tId, type: rType || '关联' };

    const newLinks = [...data.links, newLink];
    if (originalGraphData) originalGraphData.links.push(JSON.parse(JSON.stringify(newLink)));

    graph.graphData({ nodes: data.nodes, links: newLinks });
    updatePageWithGraphData({ ...data, links: newLinks });

    window.showNotification('添加关系成功', 'success');
    document.getElementById('addRelationModal').close();
}

// Modals - Delete Relation
function openDeleteRelationModal() {
    document.getElementById('deleteRelationModal').showModal();
    filterDeleteRelations();
}
function filterDeleteRelations() {
    const type = document.getElementById('deleteRelationTypeFilter').value;
    const search = document.getElementById('deleteRelationSearchInput').value.toLowerCase(); 
    const list = document.getElementById('deleteRelationList');

    if (!graph) return;
    const data = graph.graphData();
    const links = data.links.filter(l => {
        const matchType = type ? l.type === type : true;
        const sourceLabel = (typeof l.source === 'object' ? l.source.label : l.source).toLowerCase();
        const targetLabel = (typeof l.target === 'object' ? l.target.label : l.target).toLowerCase();
        const matchSearch = search ? (sourceLabel.includes(search) || targetLabel.includes(search)) : true;
        return matchType && matchSearch;
    });

    list.innerHTML = '';
    links.forEach((l, idx) => {
        // We need a unique way to identify link for deletion. 
        const sId = typeof l.source === 'object' ? l.source.id : l.source;
        const tId = typeof l.target === 'object' ? l.target.id : l.target;
        const val = `${sId}__${tId}__${l.type}`;

        const sLabel = typeof l.source === 'object' ? l.source.label : sId;
        const tLabel = typeof l.target === 'object' ? l.target.label : tId;

        const div = document.createElement('div');
        div.className = 'form-control bg-base-100 p-2 rounded border border-base-200';
        div.innerHTML = `
        <label class="cursor-pointer label justify-start gap-3">
          <input type="checkbox" class="checkbox checkbox-xs checkbox-error delete-relation-checkbox" value="${val}" onchange="updateDeleteRelationCount()">
          <span class="label-text flex-1 text-xs">${sLabel} <span class="text-base-content/50">--${l.type}--></span> ${tLabel}</span>
        </label>
      `;
        list.appendChild(div);
    });
    updateDeleteRelationCount();
}
function updateDeleteRelationCount() {
    const count = document.querySelectorAll('.delete-relation-checkbox:checked').length;
    document.getElementById('selectedDeleteRelationCount').textContent = `已选择 ${count} 个关系`;
}
function selectAllDeleteRelations() {
    document.querySelectorAll('.delete-relation-checkbox').forEach(cb => cb.checked = true);
    updateDeleteRelationCount();
}
function deselectAllDeleteRelations() {
    document.querySelectorAll('.delete-relation-checkbox').forEach(cb => cb.checked = false);
    updateDeleteRelationCount();
}
function executeBatchDeleteRelations() {
    const checkboxes = document.querySelectorAll('.delete-relation-checkbox:checked');
    const valsToDelete = Array.from(checkboxes).map(cb => cb.value);

    if (valsToDelete.length === 0) { window.showNotification('请选择关系', 'warning'); return; }

    if (confirm(`确定删除 ${valsToDelete.length} 条关系？`)) {
        if (!graph) return;
        const data = graph.graphData();
        const newLinks = data.links.filter(l => {
            const sId = typeof l.source === 'object' ? l.source.id : l.source;
            const tId = typeof l.target === 'object' ? l.target.id : l.target;
            const val = `${sId}__${tId}__${l.type}`;
            return !valsToDelete.includes(val);
        });

        if (originalGraphData) {
            originalGraphData.links = originalGraphData.links.filter(l => {
                const sId = typeof l.source === 'object' ? l.source.id : l.source;
                const tId = typeof l.target === 'object' ? l.target.id : l.target;
                const val = `${sId}__${tId}__${l.type}`;
                return !valsToDelete.includes(val);
            });
        }

        graph.graphData({ nodes: data.nodes, links: newLinks });
        updatePageWithGraphData({ ...data, links: newLinks });
        window.showNotification('删除成功', 'success');
        document.getElementById('deleteRelationModal').close();
    }
}

// Modals - Edit Node
function openEditNodeModal(nodeId) {
    if (!graph) return;
    const data = graph.graphData();
    const node = data.nodes.find(n => n.id === nodeId);
    if (!node) return;

    document.getElementById('editNodeId').value = node.id;
    document.getElementById('editNodeName').value = node.label;
    document.getElementById('editNodeType').value = node.type;
    document.getElementById('editNodeDescription').value = node.description || '';

    document.getElementById('editNodeModal').showModal();
}
function executeEditNode() {
    const id = document.getElementById('editNodeId').value;
    const name = document.getElementById('editNodeName').value;
    const type = document.getElementById('editNodeType').value;
    const desc = document.getElementById('editNodeDescription').value;

    if (!name) return;

    if (!graph) return;
    const data = graph.graphData();
    const node = data.nodes.find(n => n.id === id);
    if (node) {
        node.label = name;
        node.type = type;
        node.description = desc;
        
        if (originalGraphData) {
            const onode = originalGraphData.nodes.find(n => n.id === id);
            if (onode) { onode.label = name; onode.type = type; onode.description = desc; }
        }

        graph.graphData(data); // Re-render
        // If node details panel is open for this node, refresh it
        handleNodeClick(node);

        window.showNotification('已保存修改', 'success');
        document.getElementById('editNodeModal').close();
    }
}

function confirmDeleteNode(nodeId) {
    if (confirm('确定删除此节点？')) {
        const SetToDelete = new Set([nodeId]);
        
        if (!graph) return;
        const data = graph.graphData();
        const newNodes = data.nodes.filter(n => !SetToDelete.has(n.id));
        const newLinks = data.links.filter(l => !SetToDelete.has(l.source.id) && !SetToDelete.has(l.target.id));

        if (originalGraphData) {
            originalGraphData.nodes = originalGraphData.nodes.filter(n => !SetToDelete.has(n.id));
            originalGraphData.links = originalGraphData.links.filter(l => !SetToDelete.has(typeof l.source === 'object' ? l.source.id : l.source) && !SetToDelete.has(typeof l.target === 'object' ? l.target.id : l.target));
        }

        graph.graphData({ nodes: newNodes, links: newLinks });
        updatePageWithGraphData({ ...data, nodes: newNodes, links: newLinks });

        document.getElementById('nodeDetailContent').innerHTML = '<div class="text-center text-base-content/50 py-8">节点已删除</div>';
        window.showNotification('删除成功', 'success');
    }
}

// Feature Controls
function toggleFavorite() {
    const btn = document.getElementById('favoriteBtn');
    if (window.APP_GLOBALS && window.APP_GLOBALS.favorites) {
        window.APP_GLOBALS.favorites.toggle('g123', mockGraphData); // Demo ID

        if (window.APP_GLOBALS.favorites.isFavorited('g123')) {
            btn.classList.add('text-red-500');
            btn.querySelector('span').setAttribute('data-icon', 'heroicons:heart-solid');
        } else {
            btn.classList.remove('text-red-500');
            btn.querySelector('span').setAttribute('data-icon', 'heroicons:heart');
        }
    }
}

function shareGraph() {
    document.getElementById('shareModal').showModal();
    // Generate link
    const link = window.location.href;
    document.getElementById('shareLink').value = link;
}

function copyShareLink() {
    const input = document.getElementById('shareLink');
    input.select();
    document.execCommand('copy'); 
    window.showNotification('链接已复制', 'success');
}

function downloadGraph() {
    document.getElementById('downloadModal').showModal();
}

function downloadFormat(format) {
    if (format === 'json') {
        const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(graph ? graph.graphData() : {}));
        const downloadAnchorNode = document.createElement('a');
        downloadAnchorNode.setAttribute("href", dataStr);
        downloadAnchorNode.setAttribute("download", "graph.json");
        document.body.appendChild(downloadAnchorNode);
        downloadAnchorNode.click();
        downloadAnchorNode.remove();
    } else if (format === 'png') {
        // Canvas to PNG
        const canvas = document.querySelector('#graphCanvas canvas');
        if (canvas) {
            const link = document.createElement('a');
            link.download = 'graph.png';
            link.href = canvas.toDataURL();
            link.click();
        }
    } else {
        window.showNotification('此格式暂不支持导出', 'info');
    }
    document.getElementById('downloadModal').close();
}

function toggleFullscreen() {
    const elem = document.getElementById("graphCanvas");
    if (!document.fullscreenElement) {
        elem.requestFullscreen().catch(err => {
            window.showNotification(`Error: ${err.message}`, 'error');
        });
    } else {
        document.exitFullscreen();
    }
}

function toggleStatistics() {
    const content = document.getElementById('statisticsContent');
    const icon = document.getElementById('statsToggleIcon');
    if (content.classList.contains('hidden')) {
        content.classList.remove('hidden');
        icon.setAttribute('data-icon', 'heroicons:chevron-up');
    } else {
        content.classList.add('hidden');
        icon.setAttribute('data-icon', 'heroicons:chevron-down');
    }
}

function selectChart(type) {
    const label = {
        'nodeTypePie': '节点类型分布饼状图',
        'relationTypePie': '关系类型分布饼状图',
        'coreNodes': '核心节点排行榜',
        'stats': '图谱基础统计',
        'thumbnail': '图谱缩略图'
    }[type] || type;
    
    document.getElementById('selectedChartType').textContent = label;

    // Update Image Mock (Real app would render chart.js or echarts)
    const img = document.querySelector('#chartContainer img');
    if (img) img.src = `https://placehold.co/400x240.png?text=${type}+Chart`;
}

// Sidebar Logic
function toggleLeftSidebar() {
    const sidebar = document.getElementById('leftSidebar');
    sidebar.classList.toggle('collapsed');
    setTimeout(() => {
        const container = document.getElementById('graphCanvas');
        if (graph && container) graph.width(container.clientWidth);
    }, 300);
}

function toggleRightSidebar() {
    const sidebar = document.getElementById('rightSidebar');
    sidebar.classList.toggle('collapsed');
    setTimeout(() => {
        const container = document.getElementById('graphCanvas');
        if (graph && container) graph.width(container.clientWidth);
    }, 300);
}

// Resizing
function setupSidebarResizing() {
    const setupResize = (handleId, sidebarId, direction) => {
        const handle = document.getElementById(handleId);
        const sidebar = document.getElementById(sidebarId);
        let isResizing = false;

        if (!handle || !sidebar) return;

        handle.addEventListener('mousedown', (e) => {
            isResizing = true;
            handle.classList.add('active');
            document.body.style.cursor = 'col-resize';
            e.preventDefault();
        });

        document.addEventListener('mousemove', (e) => {
            if (!isResizing) return;

            let newWidth;
            if (direction === 'left') {
                newWidth = e.clientX - sidebar.getBoundingClientRect().left;
            } else {
                newWidth = sidebar.getBoundingClientRect().right - e.clientX;
            }

            if (newWidth > 150 && newWidth < 600) {
                sidebar.style.width = newWidth + 'px';
            }
        });

        document.addEventListener('mouseup', () => {
            if (isResizing) {
                isResizing = false;
                handle.classList.remove('active');
                document.body.style.cursor = '';
                // Trigger resize for graph
                const container = document.getElementById('graphCanvas');
                if (graph && container) graph.width(container.clientWidth);
            }
        });
    };

    setupResize('leftResizeHandle', 'leftSidebar', 'left');
    setupResize('rightResizeHandle', 'rightSidebar', 'right');
}

// Init on Load
document.addEventListener('DOMContentLoaded', function () {
    initGraph();
    setupSidebarResizing();
});
