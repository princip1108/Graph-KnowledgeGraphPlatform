/**
 * Graph Bindao Module - Part 2 (Rendering, Interaction)
 * 图谱渲染、交互模块
 */

(function() {
    'use strict';

    const NODE_RADIUS = 14;

    // 节点类型颜色映射
    const nodeTypeColors = {};
    const predefinedColors = {
        '概念': '#3B82F6', 'concept': '#3B82F6', '算法': '#8B5CF6', 'algorithm': '#8B5CF6',
        '应用': '#10B981', 'application': '#10B981', '工具': '#F59E0B', 'tool': '#F59E0B',
        '人物': '#EC4899', 'person': '#EC4899', '组织': '#6366F1', 'organization': '#6366F1',
        '地点': '#14B8A6', 'location': '#14B8A6', '事件': '#EF4444', 'event': '#EF4444',
        '技术': '#0EA5E9', 'technology': '#0EA5E9', '理论': '#A855F7', 'theory': '#A855F7',
        '方法': '#22C55E', 'method': '#22C55E', '默认': '#64748B', 'default': '#64748B'
    };
    const dynamicHues = [210, 280, 150, 30, 330, 180, 0, 45, 260, 120, 300, 60];
    let dynamicColorIndex = 0;

    function getNodeColor(type) {
        if (!type) return predefinedColors['default'];
        const typeLower = type.toLowerCase();
        if (predefinedColors[type]) return predefinedColors[type];
        if (predefinedColors[typeLower]) return predefinedColors[typeLower];
        if (nodeTypeColors[type]) return nodeTypeColors[type];
        const hue = dynamicHues[dynamicColorIndex % dynamicHues.length];
        dynamicColorIndex++;
        const color = `hsl(${hue}, 65%, 55%)`;
        nodeTypeColors[type] = color;
        return color;
    }

    // 拖动状态
    let dragging = { active: false, nodeId: null, offsetX: 0, offsetY: 0, moved: false, nodeEl: null, labelEl: null, edges: [], svg: null, viewBox: null, originalR: 14 };

    function screenToSVG(svg, clientX, clientY) {
        const pt = svg.createSVGPoint();
        pt.x = clientX; pt.y = clientY;
        const ctm = svg.getScreenCTM();
        if (!ctm) return { x: clientX, y: clientY };
        const svgP = pt.matrixTransform(ctm.inverse());
        return { x: svgP.x, y: svgP.y };
    }

    function startDrag(event, nodeId) {
        event.preventDefault(); event.stopPropagation();
        const canvas = document.getElementById('graphCanvas');
        const svg = canvas ? canvas.querySelector('svg') : null;
        const nodeEl = document.querySelector(`[data-node-id="${nodeId}"]`);
        if (!canvas || !svg || !nodeEl) return;
        const viewBoxStr = svg.getAttribute('viewBox');
        const vb = viewBoxStr ? viewBoxStr.split(' ').map(Number) : [0, 0, 800, 600];
        const svgPos = screenToSVG(svg, event.clientX, event.clientY);
        const nodeCX = parseFloat(nodeEl.getAttribute('cx')), nodeCY = parseFloat(nodeEl.getAttribute('cy'));
        const edges = [];
        document.querySelectorAll(`[data-edge-source="${nodeId}"]`).forEach(edge => {
            const targetId = edge.getAttribute('data-edge-target');
            const targetEl = document.querySelector(`[data-node-id="${targetId}"]`);
            if (targetEl) edges.push({ edge, targetX: parseFloat(targetEl.getAttribute('cx')), targetY: parseFloat(targetEl.getAttribute('cy')), isSource: true, labelEl: document.querySelector(`[data-label-source="${nodeId}"][data-label-target="${targetId}"]`) });
        });
        document.querySelectorAll(`[data-edge-target="${nodeId}"]`).forEach(edge => {
            const sourceId = edge.getAttribute('data-edge-source');
            const sourceEl = document.querySelector(`[data-node-id="${sourceId}"]`);
            if (sourceEl) edges.push({ edge, targetX: parseFloat(sourceEl.getAttribute('cx')), targetY: parseFloat(sourceEl.getAttribute('cy')), isSource: false, labelEl: document.querySelector(`[data-label-source="${sourceId}"][data-label-target="${nodeId}"]`) });
        });
        dragging = { active: true, nodeId, offsetX: svgPos.x - nodeCX, offsetY: svgPos.y - nodeCY, moved: false, nodeEl, labelEl: document.querySelector(`[data-label-for="${nodeId}"]`), edges, svg, viewBox: { x: vb[0], y: vb[1], width: vb[2], height: vb[3] }, originalR: 14 };
        nodeEl.setAttribute('r', 17);
        nodeEl.style.filter = 'drop-shadow(0 4px 8px rgba(0,0,0,0.3))';
        nodeEl.style.cursor = 'grabbing';
    }

    function onDrag(event) {
        if (!dragging.active || !dragging.svg) return;
        const svgPos = screenToSVG(dragging.svg, event.clientX, event.clientY);
        let x = svgPos.x - dragging.offsetX, y = svgPos.y - dragging.offsetY;
        const padding = NODE_RADIUS + 10, vb = dragging.viewBox;
        if (vb) { x = Math.max(vb.x + padding, Math.min(vb.x + vb.width - padding, x)); y = Math.max(vb.y + padding, Math.min(vb.y + vb.height - padding, y)); }
        dragging.moved = true;
        dragging.nodeEl.setAttribute('cx', x); dragging.nodeEl.setAttribute('cy', y);
        if (dragging.labelEl) { dragging.labelEl.setAttribute('x', x); dragging.labelEl.setAttribute('y', y + 28); }
        for (const e of dragging.edges) {
            let x1, y1, x2, y2;
            if (e.isSource) { x1 = x; y1 = y; x2 = e.targetX; y2 = e.targetY; } else { x1 = e.targetX; y1 = e.targetY; x2 = x; y2 = y; }
            const dx = x2 - x1, dy = y2 - y1, dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 0) {
                const nx = dx / dist, ny = dy / dist;
                e.edge.setAttribute('x1', x1 + nx * NODE_RADIUS); e.edge.setAttribute('y1', y1 + ny * NODE_RADIUS);
                e.edge.setAttribute('x2', x2 - nx * (NODE_RADIUS + 5)); e.edge.setAttribute('y2', y2 - ny * (NODE_RADIUS + 5));
                if (e.labelEl) { e.labelEl.setAttribute('x', (x1 + x2) / 2); e.labelEl.setAttribute('y', (y1 + y2) / 2 - 8); }
            }
        }
        if (window.currentGraphData && window.currentGraphData.nodePositions && window.currentGraphData.nodePositions[dragging.nodeId]) {
            window.currentGraphData.nodePositions[dragging.nodeId].x = x;
            window.currentGraphData.nodePositions[dragging.nodeId].y = y;
        }
    }

    function endDrag(event) {
        if (!dragging.active) return;
        if (dragging.nodeEl) { dragging.nodeEl.setAttribute('r', dragging.originalR); dragging.nodeEl.style.filter = ''; dragging.nodeEl.style.cursor = 'grab'; }
        if (!dragging.moved && dragging.nodeId) {
            const nodes = window.currentGraphData.nodes || [];
            const nodeData = nodes.find(n => n.nodeId === dragging.nodeId);
            if (nodeData) {
                const nodeInfo = { id: nodeData.nodeId, nodeId: nodeData.nodeId, label: nodeData.name, name: nodeData.name, type: nodeData.type || 'default', description: nodeData.description };
                selectNode(nodeInfo);
            }
        }
        dragging = { active: false, nodeId: null, offsetX: 0, offsetY: 0, moved: false, nodeEl: null, labelEl: null, edges: [], svg: null, viewBox: null, originalR: 14 };
    }

    // 画布拖动
    let panning = { active: false, startX: 0, startY: 0, viewBox: { x: 0, y: 0, width: 0, height: 0 } };
    let panningInitialized = false, panAnimationId = null;

    function initCanvasPanning() {
        const canvas = document.getElementById('graphCanvas');
        if (!canvas || panningInitialized) return;
        panningInitialized = true;
        canvas.addEventListener('mousedown', startPanning, false);
        canvas.addEventListener('mousemove', onPanning, false);
        canvas.addEventListener('mouseup', endPanning, false);
        canvas.addEventListener('mouseleave', endPanning, false);
    }

    function startPanning(event) {
        if (dragging.active) return;
        const tagName = event.target.tagName.toLowerCase();
        if (tagName === 'circle' || tagName === 'text') return;
        if (tagName !== 'svg' && tagName !== 'rect' && tagName !== 'line' && tagName !== 'div') return;
        const canvas = document.getElementById('graphCanvas'), svg = canvas.querySelector('svg');
        if (!svg) return;
        const viewBoxStr = svg.getAttribute('viewBox');
        if (!viewBoxStr) return;
        const parts = viewBoxStr.split(' ').map(Number);
        panning = { active: true, startX: event.clientX, startY: event.clientY, viewBox: { x: parts[0], y: parts[1], width: parts[2], height: parts[3] } };
        svg.style.cursor = 'grabbing';
    }

    function onPanning(event) {
        if (!panning.active || dragging.active) return;
        const canvas = document.getElementById('graphCanvas'), svg = canvas.querySelector('svg');
        if (!svg) return;
        const dx = event.clientX - panning.startX, dy = event.clientY - panning.startY;
        const rect = canvas.getBoundingClientRect();
        const scaleX = panning.viewBox.width / rect.width, scaleY = panning.viewBox.height / rect.height;
        const moveX = dx * scaleX * 1.5, moveY = dy * scaleY * 1.5;
        if (panAnimationId) cancelAnimationFrame(panAnimationId);
        panAnimationId = requestAnimationFrame(() => {
            const newX = panning.viewBox.x - moveX, newY = panning.viewBox.y - moveY;
            svg.setAttribute('viewBox', `${newX} ${newY} ${panning.viewBox.width} ${panning.viewBox.height}`);
            panning.startX = event.clientX; panning.startY = event.clientY;
            panning.viewBox.x = newX; panning.viewBox.y = newY;
        });
    }

    function endPanning(event) {
        if (!panning.active) return;
        const canvas = document.getElementById('graphCanvas'), svg = canvas ? canvas.querySelector('svg') : null;
        if (svg) svg.style.cursor = 'grab';
        panning.active = false;
    }

    // 节点选择
    function selectNode(node) {
        window.currentGraphData.selectedNode = node;
        if (typeof currentSelectedNodeId !== 'undefined') currentSelectedNodeId = node.id;
        document.querySelectorAll('.graph-node').forEach(n => { n.classList.remove('selected'); n.style.stroke = ''; n.style.strokeWidth = ''; n.style.filter = ''; });
        const nodeEl = document.querySelector(`[data-node-id="${node.id}"]`);
        if (nodeEl) { nodeEl.classList.add('selected'); nodeEl.style.stroke = 'var(--color-primary)'; nodeEl.style.strokeWidth = '3px'; nodeEl.style.filter = 'drop-shadow(0 2px 4px rgba(30, 58, 138, 0.3))'; }
        if (window.updateNodeDetailPanel) window.updateNodeDetailPanel(node);
    }

    function clearNodeDetails() {
        const detailPanel = document.getElementById('nodeDetailContent');
        if (detailPanel) detailPanel.innerHTML = '<div class="text-center text-base-content/50 py-8"><span class="iconify text-4xl mb-2" data-icon="heroicons:cursor-arrow-rays"></span><p>点击节点查看详情</p></div>';
    }

    // 渲染函数
    window.bindaoRenderGraph = function(specifiedCenter = null) {
        const canvas = document.getElementById('graphCanvas');
        const nodes = window.currentGraphData.nodes || [], edges = window.currentGraphData.edges || [];
        if (nodes.length === 0) { canvas.innerHTML = '<div class="absolute inset-0 flex items-center justify-center"><div class="text-center"><p class="text-base-content/70">该图谱暂无节点数据</p></div></div>'; return; }
        canvas.innerHTML = '';
        const { positions: nodePositions, viewBox } = window.layoutGraph(nodes, edges, specifiedCenter);
        window.currentGraphData.nodePositions = nodePositions;
        window.currentGraphData.initialViewBox = { ...viewBox };
        window.currentGraphData.initialPositions = JSON.parse(JSON.stringify(nodePositions));
        
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('width', '100%'); svg.setAttribute('height', '100%');
        svg.setAttribute('viewBox', `${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`);
        svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
        svg.style.position = 'absolute'; svg.style.top = '0'; svg.style.left = '0'; svg.style.cursor = 'grab';
        
        const bgRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        bgRect.setAttribute('x', viewBox.x - 1000); bgRect.setAttribute('y', viewBox.y - 1000);
        bgRect.setAttribute('width', viewBox.width + 2000); bgRect.setAttribute('height', viewBox.height + 2000);
        bgRect.setAttribute('fill', 'transparent'); bgRect.setAttribute('class', 'svg-background');
        svg.appendChild(bgRect);

        const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
        const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
        marker.setAttribute('id', 'arrowhead'); marker.setAttribute('markerWidth', '10'); marker.setAttribute('markerHeight', '7');
        marker.setAttribute('refX', '9'); marker.setAttribute('refY', '3.5'); marker.setAttribute('orient', 'auto');
        const polygon = document.createElementNS('http://www.w3.org/2000/svg', 'polygon');
        polygon.setAttribute('points', '0 0, 10 3.5, 0 7'); polygon.setAttribute('fill', '#1E3A8A');
        marker.appendChild(polygon); defs.appendChild(marker); svg.appendChild(defs);

        edges.forEach((edge, index) => {
            const fromNode = nodePositions[edge.sourceNodeId], toNode = nodePositions[edge.targetNodeId];
            if (fromNode && toNode) {
                const dx = toNode.x - fromNode.x, dy = toNode.y - fromNode.y, dist = Math.sqrt(dx * dx + dy * dy);
                const startX = fromNode.x + (dx / dist) * NODE_RADIUS, startY = fromNode.y + (dy / dist) * NODE_RADIUS;
                const endX = toNode.x - (dx / dist) * (NODE_RADIUS + 5), endY = toNode.y - (dy / dist) * (NODE_RADIUS + 5);
                const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
                line.setAttribute('x1', startX); line.setAttribute('y1', startY); line.setAttribute('x2', endX); line.setAttribute('y2', endY);
                line.setAttribute('class', 'graph-edge'); line.style.stroke = '#1E3A8A'; line.style.strokeWidth = '2px'; line.style.opacity = '0.7';
                line.setAttribute('marker-end', 'url(#arrowhead)');
                line.setAttribute('data-edge-type', edge.type || '关联'); line.setAttribute('data-edge-source', edge.sourceNodeId); line.setAttribute('data-edge-target', edge.targetNodeId);
                svg.appendChild(line);
                const midX = (fromNode.x + toNode.x) / 2, midY = (fromNode.y + toNode.y) / 2;
                const label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
                label.setAttribute('x', midX); label.setAttribute('y', midY - 8); label.setAttribute('text-anchor', 'middle');
                label.setAttribute('class', 'edge-label'); label.setAttribute('data-label-source', edge.sourceNodeId); label.setAttribute('data-label-target', edge.targetNodeId);
                label.style.fontSize = '11px'; label.style.fill = '#1E3A8A'; label.style.fontWeight = '500';
                label.textContent = edge.type || '';
                svg.appendChild(label);
            }
        });

        Object.values(nodePositions).forEach(node => {
            const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
            circle.setAttribute('cx', node.x); circle.setAttribute('cy', node.y); circle.setAttribute('r', 14);
            circle.setAttribute('class', 'graph-node'); circle.setAttribute('fill', getNodeColor(node.type || 'default'));
            circle.setAttribute('data-node-id', node.nodeId); circle.setAttribute('data-node-type', node.type || 'default');
            circle.style.cursor = 'grab';
            circle.addEventListener('mousedown', (e) => startDrag(e, node.nodeId));
            svg.appendChild(circle);
            const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            text.setAttribute('x', node.x); text.setAttribute('y', node.y + 28); text.setAttribute('text-anchor', 'middle');
            text.setAttribute('class', 'node-label text-xs fill-current text-base-content font-medium');
            text.setAttribute('data-label-for', node.nodeId);
            text.textContent = node.name || '未命名';
            svg.appendChild(text);
        });

        canvas.addEventListener('mousemove', onDrag);
        canvas.addEventListener('mouseup', endDrag);
        canvas.addEventListener('mouseleave', endDrag);
        canvas.appendChild(svg);
        window.currentGraphData.nodePositions = nodePositions;
        initCanvasPanning();
    };

    // 重置视图
    window.resetView = function() {
        const canvas = document.getElementById('graphCanvas'), svg = canvas ? canvas.querySelector('svg') : null;
        if (!svg || !window.currentGraphData || !window.currentGraphData.initialViewBox || !window.currentGraphData.initialPositions) return;
        window.graphZoom = 1;
        const vb = window.currentGraphData.initialViewBox;
        svg.setAttribute('viewBox', `${vb.x} ${vb.y} ${vb.width} ${vb.height}`);
        const initPos = window.currentGraphData.initialPositions;
        Object.keys(initPos).forEach(nodeId => {
            const pos = initPos[nodeId];
            const nodeEl = document.querySelector(`[data-node-id="${nodeId}"]`), labelEl = document.querySelector(`[data-label-for="${nodeId}"]`);
            if (nodeEl) { nodeEl.setAttribute('cx', pos.x); nodeEl.setAttribute('cy', pos.y); }
            if (labelEl) { labelEl.setAttribute('x', pos.x); labelEl.setAttribute('y', pos.y + 28); }
            if (window.currentGraphData.nodePositions[nodeId]) { window.currentGraphData.nodePositions[nodeId].x = pos.x; window.currentGraphData.nodePositions[nodeId].y = pos.y; }
        });
        document.querySelectorAll('.graph-edge').forEach(edge => {
            const sourceId = edge.getAttribute('data-edge-source'), targetId = edge.getAttribute('data-edge-target');
            const sourcePos = initPos[sourceId], targetPos = initPos[targetId];
            if (sourcePos && targetPos) {
                const dx = targetPos.x - sourcePos.x, dy = targetPos.y - sourcePos.y, dist = Math.sqrt(dx * dx + dy * dy);
                if (dist > 0) {
                    const nx = dx / dist, ny = dy / dist;
                    edge.setAttribute('x1', sourcePos.x + nx * NODE_RADIUS); edge.setAttribute('y1', sourcePos.y + ny * NODE_RADIUS);
                    edge.setAttribute('x2', targetPos.x - nx * (NODE_RADIUS + 5)); edge.setAttribute('y2', targetPos.y - ny * (NODE_RADIUS + 5));
                    const edgeLabelEl = document.querySelector(`[data-label-source="${sourceId}"][data-label-target="${targetId}"]`);
                    if (edgeLabelEl) { edgeLabelEl.setAttribute('x', (sourcePos.x + targetPos.x) / 2); edgeLabelEl.setAttribute('y', (sourcePos.y + targetPos.y) / 2 - 8); }
                }
            }
        });
        if (typeof selectedNode !== 'undefined') selectedNode = null;
        document.querySelectorAll('.graph-node').forEach(node => { node.classList.remove('selected'); node.style.stroke = ''; node.style.strokeWidth = ''; });
        clearNodeDetails();
    };

    // 缩放
    function applyViewBoxZoom() {
        const svg = document.querySelector('#graphCanvas svg');
        if (!svg || !window.currentGraphData || !window.currentGraphData.initialViewBox) return;
        const initVB = window.currentGraphData.initialViewBox;
        const newWidth = initVB.width / window.graphZoom, newHeight = initVB.height / window.graphZoom;
        const centerX = initVB.x + initVB.width / 2, centerY = initVB.y + initVB.height / 2;
        svg.setAttribute('viewBox', `${centerX - newWidth / 2} ${centerY - newHeight / 2} ${newWidth} ${newHeight}`);
    }

    window.zoomIn = function() { window.graphZoom = Math.min((window.graphZoom || 1) * 1.2, 3); applyViewBoxZoom(); };
    window.zoomOut = function() { window.graphZoom = Math.max((window.graphZoom || 1) / 1.2, 0.3); applyViewBoxZoom(); };

    // 滚轮缩放 - 延迟绑定
    document.addEventListener('DOMContentLoaded', function() {
        const graphCanvas = document.getElementById('graphCanvas');
        if (graphCanvas) {
            graphCanvas.addEventListener('wheel', function(e) {
                e.preventDefault();
                if (e.deltaY < 0) window.zoomIn(); else window.zoomOut();
            }, { passive: false });
        }
    });

    // 导出工具函数（使用原始函数名以便 HTML 中调用）
    window.getNodeColor = getNodeColor;
    window.selectNode = selectNode;
    window.clearNodeDetails = clearNodeDetails;
    window.startDrag = startDrag;
    window.onDrag = onDrag;
    window.endDrag = endDrag;
    window.initCanvasPanning = initCanvasPanning;
    window.screenToSVG = screenToSVG;

})();
