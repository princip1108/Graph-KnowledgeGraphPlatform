/**
 * Graph bindao (Layout, Rendering, Interaction) Module
 * 图谱布局、渲染、交互模块
 */

(function() {
    'use strict';

    // ==================== 布局配置 ====================
    const LAYOUT_CONFIG = {
        nodeRadius: 14,
        minNodeSpacing: 80,
        minLayerGap: 100,
        staggerOffset: 18,
        maxFanAngle: Math.PI * 1.5,
        minAnglePerChild: 0.3,
        maxLevelDepth: 4,
        componentGap: 150,
        viewBoxPadding: 60,
        isolatedNodeSpacing: 60,
    };

    const NODE_RADIUS = 14;

    // ==================== 图结构构建 ====================
    function buildGraphStructure(nodes, edges) {
        const adjacencyList = {};
        const connectionCount = {};
        nodes.forEach(n => { adjacencyList[n.nodeId] = []; connectionCount[n.nodeId] = 0; });
        edges.forEach(edge => {
            if (adjacencyList[edge.sourceNodeId]) { adjacencyList[edge.sourceNodeId].push(edge.targetNodeId); connectionCount[edge.sourceNodeId]++; }
            if (adjacencyList[edge.targetNodeId]) { adjacencyList[edge.targetNodeId].push(edge.sourceNodeId); connectionCount[edge.targetNodeId]++; }
        });
        return { adjacencyList, connectionCount };
    }

    function findConnectedComponents(nodes, adjacencyList) {
        const visited = new Set();
        const components = [];
        nodes.forEach(n => {
            if (visited.has(n.nodeId)) return;
            const component = [];
            const queue = [n.nodeId];
            visited.add(n.nodeId);
            while (queue.length > 0) {
                const nodeId = queue.shift();
                component.push(nodeId);
                (adjacencyList[nodeId] || []).forEach(neighbor => {
                    if (!visited.has(neighbor)) { visited.add(neighbor); queue.push(neighbor); }
                });
            }
            components.push({ nodes: component, size: component.length });
        });
        components.sort((a, b) => b.size - a.size);
        return components;
    }

    function calculateGraphStats(componentNodes, connectionCount) {
        const degrees = componentNodes.map(id => connectionCount[id] || 0);
        const n = degrees.length;
        if (n === 0) return { mean: 0, stdDev: 0, threshold: 0 };
        const sum = degrees.reduce((a, b) => a + b, 0);
        const mean = sum / n;
        const variance = degrees.map(d => Math.pow(d - mean, 2)).reduce((a, b) => a + b, 0) / n;
        return { mean, stdDev: Math.sqrt(variance), threshold: mean + Math.sqrt(variance) * 0.5 };
    }

    function bfsDistance(startId, endId, adjacencyList) {
        if (startId === endId) return 0;
        const visited = new Set([startId]);
        const queue = [{ nodeId: startId, dist: 0 }];
        while (queue.length > 0) {
            const { nodeId, dist } = queue.shift();
            for (const neighbor of (adjacencyList[nodeId] || [])) {
                if (neighbor === endId) return dist + 1;
                if (!visited.has(neighbor)) { visited.add(neighbor); queue.push({ nodeId: neighbor, dist: dist + 1 }); }
            }
        }
        return Infinity;
    }

    function findNearestHighDegreeNode(startId, componentNodes, adjacencyList, connectionCount, threshold) {
        const visited = new Set([startId]);
        const queue = [startId];
        while (queue.length > 0) {
            const nodeId = queue.shift();
            for (const neighbor of (adjacencyList[nodeId] || [])) {
                if (!visited.has(neighbor) && componentNodes.includes(neighbor)) {
                    if (connectionCount[neighbor] >= threshold) return neighbor;
                    visited.add(neighbor); queue.push(neighbor);
                }
            }
        }
        return null;
    }

    function determineCenters(componentNodes, adjacencyList, connectionCount, specifiedCenter = null) {
        const n = componentNodes.length;
        const stats = calculateGraphStats(componentNodes, connectionCount);
        let centerCount = n <= 25 ? 1 : n <= 60 ? 2 : n <= 100 ? 3 : 4;
        const sortedNodes = [...componentNodes].sort((a, b) => (connectionCount[b] || 0) - (connectionCount[a] || 0));
        const centers = [];
        if (specifiedCenter && componentNodes.includes(specifiedCenter)) {
            centers.push(connectionCount[specifiedCenter] >= stats.threshold ? specifiedCenter : (findNearestHighDegreeNode(specifiedCenter, componentNodes, adjacencyList, connectionCount, stats.threshold) || sortedNodes[0]));
        } else { centers.push(sortedNodes[0]); }
        while (centers.length < centerCount) {
            let bestCandidate = null, bestScore = -1;
            for (const nodeId of sortedNodes) {
                if (centers.includes(nodeId) || connectionCount[nodeId] < stats.mean) continue;
                let minDist = Infinity;
                for (const centerId of centers) { minDist = Math.min(minDist, bfsDistance(nodeId, centerId, adjacencyList)); }
                const score = minDist * (1 + connectionCount[nodeId] / 10);
                if (score > bestScore) { bestScore = score; bestCandidate = nodeId; }
            }
            if (bestCandidate && bestScore > 2) { centers.push(bestCandidate); } else { break; }
        }
        const nodeToCenter = {};
        componentNodes.forEach(nodeId => {
            let minDist = Infinity, nearestCenter = centers[0];
            for (const centerId of centers) { const dist = bfsDistance(nodeId, centerId, adjacencyList); if (dist < minDist) { minDist = dist; nearestCenter = centerId; } }
            nodeToCenter[nodeId] = nearestCenter;
        });
        return { centers, nodeToCenter, stats };
    }

    function calculateLayerRadii(levelNodeCounts) {
        const radii = { 0: 0 };
        let prevRadius = 0;
        Object.keys(levelNodeCounts).map(Number).sort((a, b) => a - b).forEach(level => {
            if (level === 0) return;
            const nodeCount = levelNodeCounts[level];
            let radius = Math.max((nodeCount * LAYOUT_CONFIG.minNodeSpacing) / (2 * Math.PI), prevRadius + LAYOUT_CONFIG.minLayerGap);
            if (nodeCount > 12) radius *= 1 + (nodeCount - 12) * 0.05;
            radii[level] = radius; prevRadius = radius;
        });
        return radii;
    }

    function layoutSingleCenter(centerId, assignedNodes, nodes, edges, adjacencyList) {
        const positions = {};
        const nodesByLevel = {}, nodeLevels = {}, nodeParent = {};
        const visited = new Set();
        const queue = [{ nodeId: centerId, level: 0, parent: null }];
        visited.add(centerId);
        while (queue.length > 0) {
            const { nodeId, level, parent } = queue.shift();
            if (!nodesByLevel[level]) nodesByLevel[level] = [];
            nodesByLevel[level].push(nodeId); nodeLevels[nodeId] = level;
            if (parent) nodeParent[nodeId] = parent;
            (adjacencyList[nodeId] || []).forEach(neighbor => {
                if (!visited.has(neighbor) && assignedNodes.includes(neighbor)) { visited.add(neighbor); queue.push({ nodeId: neighbor, level: level + 1, parent: nodeId }); }
            });
        }
        const levelNodeCounts = {};
        Object.keys(nodesByLevel).forEach(l => { levelNodeCounts[l] = nodesByLevel[l].length; });
        const radii = calculateLayerRadii(levelNodeCounts);
        const centerX = 0, centerY = 0;
        Object.keys(nodesByLevel).sort((a, b) => Number(a) - Number(b)).forEach(levelStr => {
            const level = parseInt(levelStr), nodesAtLevel = nodesByLevel[level], radius = radii[level];
            if (level === 0) { positions[centerId] = { x: centerX, y: centerY, ...nodes.find(n => n.nodeId === centerId) }; }
            else if (level === 1) {
                const angleStep = (2 * Math.PI) / nodesAtLevel.length, startAngle = -Math.PI / 2;
                nodesAtLevel.forEach((nodeId, index) => {
                    const angle = startAngle + angleStep * index;
                    const r = index % 2 === 0 ? radius : radius + LAYOUT_CONFIG.staggerOffset;
                    positions[nodeId] = { x: centerX + r * Math.cos(angle), y: centerY + r * Math.sin(angle), ...nodes.find(n => n.nodeId === nodeId) };
                });
            } else {
                const nodesByParentMap = {};
                nodesAtLevel.forEach(nodeId => { const parent = nodeParent[nodeId] || '_root'; if (!nodesByParentMap[parent]) nodesByParentMap[parent] = []; nodesByParentMap[parent].push(nodeId); });
                let globalIndex = 0;
                Object.keys(nodesByParentMap).forEach(parentId => {
                    const children = nodesByParentMap[parentId], parentPos = positions[parentId];
                    if (!parentPos) { children.forEach(nodeId => { const angle = (globalIndex / nodesAtLevel.length) * 2 * Math.PI; const r = (globalIndex % 2 === 0) ? radius : radius + LAYOUT_CONFIG.staggerOffset; positions[nodeId] = { x: centerX + r * Math.cos(angle), y: centerY + r * Math.sin(angle), ...nodes.find(n => n.nodeId === nodeId) }; globalIndex++; }); return; }
                    const parentAngle = Math.atan2(parentPos.y - centerY, parentPos.x - centerX);
                    const totalAngle = Math.min(LAYOUT_CONFIG.maxFanAngle, children.length * LAYOUT_CONFIG.minAnglePerChild);
                    const startAngle = parentAngle - totalAngle / 2, step = children.length > 1 ? totalAngle / (children.length - 1) : 0;
                    children.forEach((nodeId, idx) => { const angle = children.length === 1 ? parentAngle : startAngle + step * idx; const r = (globalIndex % 2 === 0) ? radius : radius + LAYOUT_CONFIG.staggerOffset; positions[nodeId] = { x: centerX + r * Math.cos(angle), y: centerY + r * Math.sin(angle), ...nodes.find(n => n.nodeId === nodeId) }; globalIndex++; });
                });
            }
        });
        return positions;
    }

    function applyRepulsion(positions, iterations = 30) {
        const minDist = LAYOUT_CONFIG.nodeRadius * 2 + 10, repulsionStrength = 5, nodeIds = Object.keys(positions);
        for (let iter = 0; iter < iterations; iter++) {
            let maxOverlap = 0;
            for (let i = 0; i < nodeIds.length; i++) {
                for (let j = i + 1; j < nodeIds.length; j++) {
                    const posA = positions[nodeIds[i]], posB = positions[nodeIds[j]];
                    const dx = posB.x - posA.x, dy = posB.y - posA.y, dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < minDist && dist > 0) {
                        maxOverlap = Math.max(maxOverlap, minDist - dist);
                        const force = repulsionStrength * (minDist - dist) / dist, fx = force * dx * 0.5, fy = force * dy * 0.5;
                        posA.x -= fx; posA.y -= fy; posB.x += fx; posB.y += fy;
                    }
                }
            }
            if (maxOverlap < 1) break;
        }
    }

    function layoutIsolatedNodes(isolatedNodeIds, nodes, mainBounds) {
        const positions = {};
        if (isolatedNodeIds.length === 0) return positions;
        const arcCenterX = (mainBounds.minX + mainBounds.maxX) / 2, arcY = mainBounds.maxY + 80;
        const spacing = LAYOUT_CONFIG.isolatedNodeSpacing, totalWidth = isolatedNodeIds.length * spacing;
        const arcRadius = Math.max(totalWidth / Math.PI, 100), arcCenterY = arcY - arcRadius;
        const halfAngle = Math.min(Math.PI * 0.4, (isolatedNodeIds.length * spacing) / (2 * arcRadius));
        const startAngle = Math.PI / 2 - halfAngle, angleStep = isolatedNodeIds.length > 1 ? (2 * halfAngle) / (isolatedNodeIds.length - 1) : 0;
        isolatedNodeIds.forEach((nodeId, idx) => {
            const angle = isolatedNodeIds.length === 1 ? Math.PI / 2 : startAngle + angleStep * idx;
            positions[nodeId] = { x: arcCenterX + arcRadius * Math.cos(angle), y: arcCenterY + arcRadius * Math.sin(angle), ...nodes.find(n => n.nodeId === nodeId) };
        });
        return positions;
    }

    function calculateViewBox(positions) {
        const coords = Object.values(positions);
        if (coords.length === 0) return { x: 0, y: 0, width: 800, height: 600 };
        const nodePadding = LAYOUT_CONFIG.nodeRadius + 40, sidePadding = LAYOUT_CONFIG.viewBoxPadding;
        const minX = Math.min(...coords.map(p => p.x)) - sidePadding, maxX = Math.max(...coords.map(p => p.x)) + sidePadding;
        const minY = Math.min(...coords.map(p => p.y)) - sidePadding, maxY = Math.max(...coords.map(p => p.y)) + nodePadding;
        return { x: minX, y: minY, width: Math.max(maxX - minX, 400), height: Math.max(maxY - minY, 300) };
    }

    function getBoundingBox(positions) {
        const coords = Object.values(positions);
        if (coords.length === 0) return { minX: 0, maxX: 0, minY: 0, maxY: 0 };
        return { minX: Math.min(...coords.map(p => p.x)), maxX: Math.max(...coords.map(p => p.x)), minY: Math.min(...coords.map(p => p.y)), maxY: Math.max(...coords.map(p => p.y)) };
    }

    function getCenterPositions(centerCount) {
        const gap = 300, positions = [];
        if (centerCount === 1) { positions.push({ x: 0, y: 0 }); }
        else if (centerCount === 2) { positions.push({ x: -gap / 2, y: 0 }); positions.push({ x: gap / 2, y: 0 }); }
        else if (centerCount === 3) { positions.push({ x: 0, y: -gap * 0.4 }); positions.push({ x: -gap * 0.5, y: gap * 0.3 }); positions.push({ x: gap * 0.5, y: gap * 0.3 }); }
        else { const angleStep = (2 * Math.PI) / centerCount; for (let i = 0; i < centerCount; i++) { const angle = -Math.PI / 2 + angleStep * i; positions.push({ x: gap * 0.6 * Math.cos(angle), y: gap * 0.6 * Math.sin(angle) }); } }
        return positions;
    }

    // 主布局函数 - 导出到全局
    window.layoutGraph = function(nodes, edges, specifiedCenter = null) {
        if (nodes.length === 0) return { positions: {}, viewBox: { x: 0, y: 0, width: 800, height: 600 } };
        const { adjacencyList, connectionCount } = buildGraphStructure(nodes, edges);
        const components = findConnectedComponents(nodes, adjacencyList);
        const largeComps = components.filter(c => c.size >= 5), smallComps = components.filter(c => c.size < 5);
        const isolatedNodeIds = smallComps.flatMap(c => c.nodes);
        let allPositions = {}, offsetX = 0;
        largeComps.forEach(comp => {
            const compSpecifiedCenter = (specifiedCenter && comp.nodes.includes(specifiedCenter)) ? specifiedCenter : null;
            const { centers, nodeToCenter } = determineCenters(comp.nodes, adjacencyList, connectionCount, compSpecifiedCenter);
            const centerPositionsMap = getCenterPositions(centers.length);
            let compPositions = {};
            centers.forEach((centerId, centerIdx) => {
                const assignedNodes = comp.nodes.filter(nid => nodeToCenter[nid] === centerId);
                const centerPositions = layoutSingleCenter(centerId, assignedNodes, nodes, edges, adjacencyList);
                const targetPos = centerPositionsMap[centerIdx];
                Object.keys(centerPositions).forEach(nid => { centerPositions[nid].x += targetPos.x; centerPositions[nid].y += targetPos.y; compPositions[nid] = centerPositions[nid]; });
            });
            applyRepulsion(compPositions);
            const compBbox = getBoundingBox(compPositions), compShiftX = offsetX - compBbox.minX;
            Object.keys(compPositions).forEach(nid => { compPositions[nid].x += compShiftX; allPositions[nid] = compPositions[nid]; });
            offsetX = compBbox.maxX + compShiftX + LAYOUT_CONFIG.componentGap;
        });
        if (isolatedNodeIds.length > 0) {
            const mainBounds = Object.keys(allPositions).length === 0 ? { minX: 0, maxX: 400, minY: 0, maxY: 0 } : getBoundingBox(allPositions);
            Object.assign(allPositions, layoutIsolatedNodes(isolatedNodeIds, nodes, mainBounds));
        }
        return { positions: allPositions, viewBox: calculateViewBox(allPositions) };
    };

    // 导出配置和工具函数
    window.BINDAO_LAYOUT_CONFIG = LAYOUT_CONFIG;
    window.BINDAO_NODE_RADIUS = NODE_RADIUS;

})();
