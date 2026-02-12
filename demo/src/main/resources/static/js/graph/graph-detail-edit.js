/**
 * Graph Detail Edit Module
 * 图谱详情页编辑模块 - 节点/关系的导入、删除、编辑
 */

(function () {
    'use strict';

    // 当前导入模式
    let currentImportMode = 'single';
    let deleteRelationData = []; // 存储关系数据用于删除

    // 检查编辑权限并显示编辑区域
    window.checkAndShowEditSection = async function () {
        const graphId = window.getCurrentGraphId ? window.getCurrentGraphId() : null;
        if (!graphId) return;

        try {
            const response = await fetch(`/api/graph/${graphId}/can-edit`, {
                credentials: 'include'
            });

            if (response.ok) {
                const result = await response.json();
                if (result.canEdit) {
                    const section = document.getElementById('nodeManagementSection');
                    if (section) section.style.display = 'block';
                }
            }
        } catch (error) {
            // 权限检查失败，默认不显示编辑区域
        }
    };

    // ==================== 节点导入功能 ====================

    // 切换导入标签页
    window.switchImportTab = function (mode) {
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
    window.openBatchImportModal = function () {
        document.getElementById('singleNodeName').value = '';
        document.getElementById('singleNodeType').value = '';
        document.getElementById('singleNodeDescription').value = '';
        document.getElementById('batchImportInput').value = '';
        switchImportTab('single');
        document.getElementById('batchImportModal').showModal();
    };

    // 执行导入节点（支持单个和批量）
    window.executeImportNodes = async function () {
        const graphId = window.getCurrentGraphId ? window.getCurrentGraphId() : null;
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

                if (window.loadGraphData && window.updatePageWithGraphData) {
                    const data = await window.loadGraphData(graphId);
                    if (data) {
                        window.updatePageWithGraphData(data);
                        if (window.bindaoRenderGraph) window.bindaoRenderGraph();
                        // 图表自动更新（防抖优化）
                        if (window.scheduleChartUpdate) window.scheduleChartUpdate();
                        setTimeout(function () {
                            if (window.updateGraphCover) window.updateGraphCover();
                        }, 500);
                    }
                }
            } else {
                window.showNotification(result.error || '操作失败', 'error');
            }
        } catch (error) {
            window.showNotification('请求失败: ' + error.message, 'error');
        }
    };

    // ==================== 节点删除功能 ====================

    // 打开删除节点模态框
    window.openBatchDeleteModal = function () {
        populateDeleteModal();
        document.getElementById('batchDeleteModal').showModal();
    };

    // 填充删除模态框数据
    function populateDeleteModal() {
        const nodes = window.currentGraphData?.nodes || [];
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

    window.filterDeleteNodes = function () {
        const typeFilter = document.getElementById('deleteTypeFilter').value;
        const searchKeyword = document.getElementById('deleteSearchInput').value.toLowerCase().trim();

        let filtered = window.currentGraphData?.nodes || [];
        if (typeFilter) {
            filtered = filtered.filter(n => (n.type || '未分类') === typeFilter);
        }
        if (searchKeyword) {
            filtered = filtered.filter(n => (n.name || '').toLowerCase().includes(searchKeyword));
        }

        renderDeleteNodeList(filtered);
    };

    window.selectAllDeleteNodes = function () {
        document.querySelectorAll('.delete-node-checkbox').forEach(cb => cb.checked = true);
        updateSelectedDeleteCount();
    };

    window.deselectAllDeleteNodes = function () {
        document.querySelectorAll('.delete-node-checkbox').forEach(cb => cb.checked = false);
        updateSelectedDeleteCount();
    };

    function updateSelectedDeleteCount() {
        const count = document.querySelectorAll('.delete-node-checkbox:checked').length;
        document.getElementById('selectedDeleteCount').textContent = `已选择 ${count} 个节点`;
    }

    window.executeBatchDelete = async function () {
        const graphId = window.getCurrentGraphId ? window.getCurrentGraphId() : null;
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

                if (window.loadGraphData && window.updatePageWithGraphData) {
                    const data = await window.loadGraphData(graphId);
                    if (data) {
                        window.updatePageWithGraphData(data);
                        if (window.bindaoRenderGraph) window.bindaoRenderGraph();
                        // 图表自动更新（防抖优化）
                        if (window.scheduleChartUpdate) window.scheduleChartUpdate();
                        setTimeout(function () { if (window.updateGraphCover) window.updateGraphCover(); }, 500);
                    }
                }
            } else {
                window.showNotification(result.error || '删除失败', 'error');
            }
        } catch (error) {
            window.showNotification('删除请求失败: ' + error.message, 'error');
        }
    };

    // ==================== 关系添加功能 ====================

    // 打开添加关系模态框
    window.openAddRelationModal = function () {
        const nodes = window.currentGraphData?.nodes || [];
        const sortedNodes = [...nodes].sort((a, b) => {
            const nameA = (a.name || '').toLowerCase();
            const nameB = (b.name || '').toLowerCase();
            return nameA.localeCompare(nameB, 'zh-CN');
        });

        const datalistOptions = sortedNodes.map(n => `<option value="${n.name || '未命名'}">`).join('');

        document.getElementById('newRelationSourceList').innerHTML = datalistOptions;
        document.getElementById('newRelationTargetList').innerHTML = datalistOptions;
        document.getElementById('newRelationSourceInput').value = '';
        document.getElementById('newRelationTargetInput').value = '';
        document.getElementById('newRelationType').value = '关联';

        window._addRelationNodes = sortedNodes;

        document.getElementById('addRelationModal').showModal();
    };

    window.executeAddRelation = async function () {
        const graphId = window.getCurrentGraphId ? window.getCurrentGraphId() : null;
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

        const nodes = window._addRelationNodes || window.currentGraphData?.nodes || [];
        const sourceNode = nodes.find(n => n.name === sourceName);
        const targetNode = nodes.find(n => n.name === targetName);

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

                if (window.loadGraphData && window.updatePageWithGraphData) {
                    const data = await window.loadGraphData(graphId);
                    if (data) {
                        window.updatePageWithGraphData(data);
                        if (window.bindaoRenderGraph) window.bindaoRenderGraph();
                        // 图表自动更新（防抖优化）
                        if (window.scheduleChartUpdate) window.scheduleChartUpdate();
                        setTimeout(function () { if (window.updateGraphCover) window.updateGraphCover(); }, 500);
                    }
                }
            } else {
                window.showNotification(result.error || '创建失败', 'error');
            }
        } catch (error) {
            window.showNotification('创建请求失败: ' + error.message, 'error');
        }
    };

    // 切换添加关系Tab
    window.switchRelationTab = function (mode) {
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
    window.previewBatchRelations = function () {
        const input = document.getElementById('batchRelationInput').value.trim();
        const previewContainer = document.getElementById('batchRelationPreview');
        const previewList = document.getElementById('batchRelationPreviewList');
        const nodes = window.currentGraphData?.nodes || [];

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
    window.executeBatchAddRelations = async function () {
        const graphId = window.getCurrentGraphId ? window.getCurrentGraphId() : null;
        if (!graphId) {
            window.showNotification('请先选择图谱', 'error');
            return;
        }

        const input = document.getElementById('batchRelationInput').value.trim();
        if (!input) {
            window.showNotification('请输入关系数据', 'warning');
            return;
        }

        const nodes = window.currentGraphData?.nodes || [];
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

            if (window.loadGraphData && window.updatePageWithGraphData) {
                const data = await window.loadGraphData(graphId);
                if (data) {
                    window.updatePageWithGraphData(data);
                    if (window.bindaoRenderGraph) window.bindaoRenderGraph();
                    // 图表自动更新（防抖优化）
                    if (window.scheduleChartUpdate) window.scheduleChartUpdate();
                    setTimeout(function () { if (window.updateGraphCover) window.updateGraphCover(); }, 500);
                }
            }
        } else {
            window.showNotification('批量添加失败', 'error');
        }
    };

    // ==================== 关系删除功能 ====================

    // 打开删除关系模态框
    window.openDeleteRelationModal = function () {
        const edges = window.currentGraphData?.edges || [];
        const nodes = window.currentGraphData?.nodes || [];

        const types = [...new Set(edges.map(e => e.type || '关联'))];
        const typeSelect = document.getElementById('deleteRelationTypeFilter');
        typeSelect.innerHTML = '<option value="">全部类型</option>' +
            types.map(t => `<option value="${t}">${t}</option>`).join('');

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

    window.filterDeleteRelations = function () {
        renderDeleteRelationList();
    };

    window.toggleDeleteRelation = function (idx) {
        deleteRelationData[idx].selected = !deleteRelationData[idx].selected;
        updateDeleteRelationCount();
    };

    window.selectAllDeleteRelations = function () {
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

    window.deselectAllDeleteRelations = function () {
        deleteRelationData.forEach(r => r.selected = false);
        renderDeleteRelationList();
    };

    function updateDeleteRelationCount() {
        const count = deleteRelationData.filter(r => r.selected).length;
        document.getElementById('selectedDeleteRelationCount').textContent = `已选择 ${count} 个关系`;
    }

    window.executeBatchDeleteRelations = async function () {
        const graphId = window.getCurrentGraphId ? window.getCurrentGraphId() : null;
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

            if (window.loadGraphData && window.updatePageWithGraphData) {
                const data = await window.loadGraphData(graphId);
                if (data) {
                    window.updatePageWithGraphData(data);
                    if (window.bindaoRenderGraph) window.bindaoRenderGraph();
                    // 图表自动更新（防抖优化）
                    if (window.scheduleChartUpdate) window.scheduleChartUpdate();
                    setTimeout(function () { if (window.updateGraphCover) window.updateGraphCover(); }, 500);
                }
            }
        } else {
            window.showNotification('删除失败', 'error');
        }
    };

    // ==================== 节点编辑功能 ====================

    // 打开编辑节点模态框
    window.openEditNodeModal = async function (nodeId) {
        const nodes = window.currentGraphData?.nodes || [];
        const node = nodes.find(n => n.nodeId === nodeId);

        if (!node) {
            window.showNotification('找不到该节点', 'error');
            return;
        }

        document.getElementById('editNodeId').value = nodeId;
        document.getElementById('editNodeName').value = node.name || '';
        document.getElementById('editNodeType').value = node.type || '';

        const descInput = document.getElementById('editNodeDescription');
        descInput.value = node.description || '';

        document.getElementById('editNodeModal').showModal();

        // If description is missing, fetch it
        if (typeof node.description === 'undefined') {
            descInput.setAttribute('placeholder', '正在加载描述...');
            const graphId = window.getCurrentGraphId ? window.getCurrentGraphId() : null;
            if (graphId) {
                try {
                    const res = await fetch(`/api/graph/${graphId}/nodes/${nodeId}`);
                    if (res.ok) {
                        const data = await res.json();
                        if (data.description) {
                            descInput.value = data.description;
                            node.description = data.description; // Update cache
                        }
                    }
                } catch (e) {
                    console.error('Failed to fetch node details for edit', e);
                } finally {
                    descInput.setAttribute('placeholder', '请输入节点简介');
                }
            }
        }
    };

    // 执行编辑节点
    window.executeEditNode = async function () {
        const graphId = window.getCurrentGraphId ? window.getCurrentGraphId() : null;
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

                if (window.loadGraphData && window.updatePageWithGraphData) {
                    const data = await window.loadGraphData(graphId);
                    if (data) {
                        window.updatePageWithGraphData(data);
                        if (window.bindaoRenderGraph) window.bindaoRenderGraph();
                        // 图表自动更新（防抖优化）
                        if (window.scheduleChartUpdate) window.scheduleChartUpdate();

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
                        setTimeout(function () { if (window.updateGraphCover) window.updateGraphCover(); }, 500);
                    }
                }
            } else {
                window.showNotification(result.error || '更新失败', 'error');
            }
        } catch (error) {
            window.showNotification('更新请求失败: ' + error.message, 'error');
        }
    };

})();

