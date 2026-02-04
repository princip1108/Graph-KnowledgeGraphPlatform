/**
 * Dashboard Page JavaScript
 * 管理中心页面 JavaScript 模块
 */

(function () {
    'use strict';

    // State
    let allGraphs = [], filteredGraphs = [], allPosts = [], filteredPosts = [];
    let currentPage = 1, postCurrentPage = 1, pageSize = 10, postPageSize = 10;
    let statusFilter = 'all', postStatusFilter = 'all';
    let searchQuery = '', postSearchQuery = '';

    document.addEventListener('DOMContentLoaded', function () {
        checkAdminAccess();
        showSection('graph-batch');
        loadUserGraphs();
    });

    function checkAdminAccess() {
        fetch('/user/api/check-auth', { credentials: 'include' })
            .then(res => res.json())
            .then(data => {
                if (data.authenticated && data.user) {
                    if (data.user.role === 'ADMIN') {
                        document.getElementById('admin-section').style.display = '';
                    }
                }
            });
    }

    window.showSection = function (sectionName) {
        document.querySelectorAll('.content-section').forEach(s => s.classList.remove('active'));
        document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));

        const section = document.getElementById(sectionName + '-section');
        if (section) section.classList.add('active');

        const navItem = document.querySelector(`[data-section="${sectionName}"]`);
        if (navItem) navItem.classList.add('active');

        if (sectionName === 'graph-batch') loadUserGraphs();
        else if (sectionName === 'post-batch') loadUserPosts();
        else if (sectionName === 'message-management') loadAllMessages();
    };

    // Graph Management
    async function loadUserGraphs() {
        try {
            const response = await fetch('/api/graph/my?page=0&size=100', { credentials: 'include' });
            if (!response.ok) throw new Error('Load failed');
            const data = await response.json();
            allGraphs = data.graphs || data.content || [];
            applyFilters();
        } catch (e) {
            document.getElementById('graphTableBody').innerHTML = '<tr><td colspan="6" class="text-center py-8 text-error">加载失败</td></tr>';
        }
    }

    function applyFilters() {
        filteredGraphs = allGraphs.filter(g => {
            if (statusFilter !== 'all' && g.status !== statusFilter) return false;
            if (searchQuery && !(g.name || '').toLowerCase().includes(searchQuery.toLowerCase())) return false;
            return true;
        });
        currentPage = 1;
        renderGraphTable();
    }

    window.performSearch = function () {
        searchQuery = document.getElementById('searchInput').value.trim();
        applyFilters();
    };

    window.filterByStatus = function (status) {
        statusFilter = status;
        applyFilters();
    };

    function renderGraphTable() {
        const tbody = document.getElementById('graphTableBody');
        const total = filteredGraphs.length;
        const totalPages = Math.ceil(total / pageSize);
        const start = (currentPage - 1) * pageSize;
        const pageData = filteredGraphs.slice(start, start + pageSize);

        if (pageData.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center py-8 text-base-content/60">暂无图谱数据</td></tr>';
            document.getElementById('paginationInfo').textContent = '暂无记录';
            document.getElementById('paginationButtons').innerHTML = '';
            return;
        }

        tbody.innerHTML = pageData.map(g => {
            const statusMap = { 'PUBLISHED': { text: '已上线', badge: 'badge-success' }, 'DRAFT': { text: '草稿', badge: 'badge-info' }, 'PRIVATE': { text: '私有', badge: 'badge-warning' } };
            const status = statusMap[g.status] || { text: g.status || '未知', badge: 'badge-ghost' };
            return `<tr data-graph-id="${g.graphId}">
                <th><input type="checkbox" class="checkbox checkbox-primary graph-checkbox" data-id="${g.graphId}"></th>
                <td><div class="cursor-pointer" onclick="viewGraph(${g.graphId})"><div class="font-semibold text-primary hover:underline">${g.name || '未命名'}</div><div class="text-sm text-base-content/70 truncate max-w-xs">${g.description || '暂无描述'}</div></div></td>
                <td><span class="badge ${status.badge} badge-outline">${status.text}</span></td>
                <td>${g.uploadDate ? g.uploadDate.substring(0, 10) : '-'}</td>
                <td>${g.modifiedDate ? g.modifiedDate.substring(0, 10) : '-'}</td>
                <td><div class="flex gap-1"><button class="btn btn-ghost btn-xs" onclick="editGraph(${g.graphId})"><span class="iconify" data-icon="heroicons:pencil" data-width="16"></span></button><button class="btn btn-ghost btn-xs text-error" onclick="deleteGraph(${g.graphId}, '${(g.name || '').replace(/'/g, "\\'")}')"><span class="iconify" data-icon="heroicons:trash" data-width="16"></span></button></div></td>
            </tr>`;
        }).join('');

        document.getElementById('paginationInfo').textContent = `显示 ${start + 1}-${Math.min(start + pageSize, total)} 条，共 ${total} 条`;
        renderPagination(totalPages);
    }

    function renderPagination(totalPages) {
        const container = document.getElementById('paginationButtons');
        if (totalPages <= 1) { container.innerHTML = ''; return; }
        let html = `<button class="join-item btn btn-sm" onclick="goToPage(${currentPage - 1})" ${currentPage === 1 ? 'disabled' : ''}>«</button>`;
        for (let i = 1; i <= totalPages; i++) {
            html += `<button class="join-item btn btn-sm ${i === currentPage ? 'btn-active' : ''}" onclick="goToPage(${i})">${i}</button>`;
        }
        html += `<button class="join-item btn btn-sm" onclick="goToPage(${currentPage + 1})" ${currentPage === totalPages ? 'disabled' : ''}>»</button>`;
        container.innerHTML = html;
    }

    window.goToPage = function (page) {
        const totalPages = Math.ceil(filteredGraphs.length / pageSize);
        if (page < 1 || page > totalPages) return;
        currentPage = page;
        renderGraphTable();
    };

    window.toggleSelectAll = function (checkbox) {
        document.querySelectorAll('.graph-checkbox').forEach(cb => cb.checked = checkbox.checked);
    };

    function getSelectedIds() {
        return Array.from(document.querySelectorAll('.graph-checkbox:checked')).map(cb => parseInt(cb.dataset.id)).filter(id => !isNaN(id));
    }

    window.viewGraph = function (graphId) {
        window.location.href = '/graph/graph_detail.html?id=' + graphId;
    };

    window.editGraph = async function (graphId) {
        try {
            const response = await fetch('/api/graph/' + graphId, { credentials: 'include' });
            if (!response.ok) throw new Error('获取失败');
            const data = await response.json();
            const graph = data.graph || data;

            document.getElementById('editGraphId').value = graph.graphId;
            document.getElementById('editGraphName').value = graph.name || '';
            document.getElementById('editGraphDescription').value = graph.description || '';
            document.getElementById('editGraphStatus').value = graph.status || 'DRAFT';

            // 显示当前封面
            const coverImg = document.getElementById('editCoverPreviewImg');
            if (graph.coverImage) {
                coverImg.src = graph.coverImage;
                coverImg.style.display = 'block';
            } else {
                coverImg.src = 'https://placehold.co/400x225/eee/999?text=暂无封面';
                coverImg.style.display = 'block';
            }
            // 清空文件选择
            document.getElementById('editGraphCover').value = '';

            document.getElementById('editModal').showModal();
        } catch (e) {
            showNotification('获取图谱信息失败', 'error');
        }
    };

    window.previewEditCover = function (input) {
        if (input.files && input.files[0]) {
            const reader = new FileReader();
            reader.onload = function (e) {
                const img = document.getElementById('editCoverPreviewImg');
                img.src = e.target.result;
                img.style.display = 'block';
            };
            reader.readAsDataURL(input.files[0]);
        }
    };

    window.closeEditModal = function () {
        document.getElementById('editModal').close();
        document.getElementById('editGraphCover').value = '';
    };

    window.submitEditForm = async function () {
        const graphId = document.getElementById('editGraphId').value;
        const data = {
            name: document.getElementById('editGraphName').value.trim(),
            description: document.getElementById('editGraphDescription').value.trim(),
            status: document.getElementById('editGraphStatus').value
        };

        if (!data.name) { showNotification('请输入图谱名称', 'warning'); return; }

        try {
            // 1. 更新基本信息
            const response = await fetch('/api/graph/' + graphId, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify(data)
            });

            if (!response.ok) {
                const err = await response.json();
                showNotification(err.error || '更新失败', 'error');
                return;
            }

            // 2. 如果选择了新封面，上传封面
            const coverInput = document.getElementById('editGraphCover');
            if (coverInput.files && coverInput.files[0]) {
                const formData = new FormData();
                formData.append('file', coverInput.files[0]);
                formData.append('isCustom', 'true'); // 标记为用户自定义封面

                const coverResponse = await fetch('/api/graph/' + graphId + '/cover', {
                    method: 'PUT',
                    credentials: 'include',
                    body: formData
                });

                if (!coverResponse.ok) {
                    showNotification('基本信息已更新，但封面上传失败', 'warning');
                    closeEditModal();
                    loadUserGraphs();
                    return;
                }
            }

            showNotification('更新成功', 'success');
            closeEditModal();
            loadUserGraphs();
        } catch (e) {
            showNotification('更新失败', 'error');
        }
    };

    window.deleteGraph = async function (graphId, graphName) {
        if (!confirm('确定删除"' + graphName + '"吗？')) return;
        try {
            const response = await fetch('/api/graph/' + graphId, { method: 'DELETE', credentials: 'include' });
            if (response.ok) {
                showNotification('已删除', 'success');
                loadUserGraphs();
            } else {
                showNotification('删除失败', 'error');
            }
        } catch (e) {
            showNotification('删除失败', 'error');
        }
    };

    window.handleBatchOperation = async function (operation) {
        const ids = getSelectedIds();
        if (ids.length === 0) { showNotification('请先选择图谱', 'warning'); return; }

        const opNames = { online: '上线', offline: '下线', delete: '删除' };
        if (!confirm('确定' + opNames[operation] + ' ' + ids.length + ' 个图谱吗？')) return;

        const endpoints = { online: '/api/graph/batch/publish', offline: '/api/graph/batch/offline', delete: '/api/graph/batch/delete' };
        try {
            const response = await fetch(endpoints[operation], {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ graphIds: ids })
            });
            const result = await response.json();
            if (result.success) {
                showNotification('操作成功', 'success');
                document.getElementById('selectAll').checked = false;
                loadUserGraphs();
            } else {
                showNotification(result.error || '操作失败', 'error');
            }
        } catch (e) {
            showNotification('操作失败', 'error');
        }
    };

    // Upload
    window.openUploadModal = function () {
        document.getElementById('uploadModal').showModal();
    };

    window.closeUploadModal = function () {
        document.getElementById('uploadModal').close();
        document.getElementById('uploadForm').reset();
    };

    window.previewCover = function (input) {
        if (input.files && input.files[0]) {
            const reader = new FileReader();
            reader.onload = e => {
                document.getElementById('coverPreviewImg').src = e.target.result;
                document.getElementById('coverPreview').classList.remove('hidden');
            };
            reader.readAsDataURL(input.files[0]);
        }
    };

    window.submitUploadForm = async function () {
        const fileInput = document.getElementById('graphFile');
        if (!fileInput.files || !fileInput.files[0]) { showNotification('请选择文件', 'warning'); return; }

        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        formData.append('name', document.getElementById('graphName').value.trim());
        formData.append('description', document.getElementById('graphDescription').value.trim());
        formData.append('status', document.getElementById('graphStatus').value);

        const coverInput = document.getElementById('graphCover');
        if (coverInput.files && coverInput.files[0]) {
            formData.append('cover', coverInput.files[0]);
        }

        try {
            showNotification('上传中...', 'info');
            const response = await fetch('/api/upload/graph', { method: 'POST', credentials: 'include', body: formData });
            const data = await response.json();
            if (response.ok) {
                showNotification('上传成功', 'success');
                closeUploadModal();
                loadUserGraphs();
            } else {
                showNotification(data.error || '上传失败', 'error');
            }
        } catch (e) {
            showNotification('上传失败', 'error');
        }
    };

    // Post Management
    async function loadUserPosts() {
        try {
            const authRes = await fetch('/user/api/check-auth', { credentials: 'include' });
            const authData = await authRes.json();
            if (!authData.authenticated) return;

            const response = await fetch('/api/posts/user/' + authData.user.userId + '?page=0&size=100', { credentials: 'include' });
            const data = await response.json();
            allPosts = data.posts || data.content || [];
            applyPostFilters();
        } catch (e) {
            document.getElementById('postTableBody').innerHTML = '<tr><td colspan="6" class="text-center py-8 text-error">加载失败</td></tr>';
        }
    }

    function applyPostFilters() {
        filteredPosts = allPosts.filter(p => {
            if (postStatusFilter !== 'all' && p.postStatus !== postStatusFilter) return false;
            if (postSearchQuery && !(p.postTitle || '').toLowerCase().includes(postSearchQuery.toLowerCase())) return false;
            return true;
        });
        postCurrentPage = 1;
        renderPostTable();
    }

    window.performPostSearch = function () {
        postSearchQuery = document.getElementById('postSearchInput').value.trim();
        applyPostFilters();
    };

    window.filterPostsByStatus = function (status) {
        postStatusFilter = status;
        applyPostFilters();
    };

    function renderPostTable() {
        const tbody = document.getElementById('postTableBody');
        if (!tbody) return;
        const total = filteredPosts.length;
        const start = (postCurrentPage - 1) * postPageSize;
        const pageData = filteredPosts.slice(start, start + postPageSize);

        if (pageData.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center py-8 text-base-content/60">暂无帖子</td></tr>';
            return;
        }

        tbody.innerHTML = pageData.map(p => {
            const statusMap = { '已发布': 'badge-success', '草稿': 'badge-info' };
            const safeTitle = (p.postTitle || '').replace(/'/g, "\\'");
            return `<tr data-post-id="${p.postId}">
                <th><input type="checkbox" class="checkbox checkbox-primary post-checkbox" data-id="${p.postId}"></th>
                <td><div class="cursor-pointer" onclick="viewPost(${p.postId})"><div class="font-semibold text-primary hover:underline">${p.postTitle || '无标题'}</div><div class="text-sm text-base-content/70 truncate max-w-xs">${p.postAbstract || '暂无摘要'}</div></div></td>
                <td><span class="badge ${statusMap[p.postStatus] || 'badge-ghost'} badge-outline">${p.postStatus || '未知'}</span></td>
                <td>${p.uploadTime ? p.uploadTime.substring(0, 10) : '-'}</td>
                <td>${p.likeCount || 0}</td>
                <td><div class="flex gap-1"><button class="btn btn-ghost btn-xs" onclick="editPost(${p.postId})" title="编辑"><span class="iconify" data-icon="heroicons:pencil" data-width="16"></span></button><button class="btn btn-ghost btn-xs text-error" onclick="deletePost(${p.postId}, '${safeTitle}')"><span class="iconify" data-icon="heroicons:trash" data-width="16"></span></button></div></td>
            </tr>`;
        }).join('');
    }

    window.viewPost = function (postId) {
        window.location.href = '/community/post_detail.html?id=' + postId;
    };

    window.deletePost = async function (postId, postTitle) {
        if (!confirm('确定删除"' + (postTitle || '') + '"吗？')) return;
        try {
            await fetch('/api/posts/' + postId, { method: 'DELETE', credentials: 'include' });
            showNotification('已删除', 'success');
            loadUserPosts();
        } catch (e) {
            showNotification('删除失败', 'error');
        }
    };

    window.editPost = async function (postId) {
        try {
            const response = await fetch('/api/posts/' + postId, { credentials: 'include' });
            if (!response.ok) { showNotification('获取帖子信息失败', 'error'); return; }
            const data = await response.json();
            const post = data.post || data;

            document.getElementById('editPostId').value = post.postId;
            document.getElementById('editPostTitle').value = post.postTitle || '';
            document.getElementById('editPostAbstract').value = post.postAbstract || '';
            document.getElementById('editPostStatus').value = post.postStatus || '草稿';

            document.getElementById('editPostModal').showModal();
        } catch (e) {
            showNotification('获取帖子信息失败', 'error');
        }
    };

    window.closeEditPostModal = function () {
        document.getElementById('editPostModal').close();
    };

    window.submitEditPost = async function () {
        const postId = document.getElementById('editPostId').value;
        const data = {
            title: document.getElementById('editPostTitle').value.trim(),
            abstract: document.getElementById('editPostAbstract').value.trim(),
            status: document.getElementById('editPostStatus').value
        };

        if (!data.title) { showNotification('请输入帖子标题', 'warning'); return; }

        try {
            const response = await fetch('/api/posts/' + postId, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify(data)
            });
            if (response.ok) {
                showNotification('更新成功', 'success');
                closeEditPostModal();
                loadUserPosts();
            } else {
                const err = await response.json();
                showNotification(err.error || '更新失败', 'error');
            }
        } catch (e) {
            showNotification('更新失败', 'error');
        }
    };

    window.goToCreatePost = function () {
        window.location.href = '/community/post_edit.html';
    };

    window.togglePostSelectAll = function (checkbox) {
        document.querySelectorAll('.post-checkbox').forEach(cb => cb.checked = checkbox.checked);
    };

    function getSelectedPostIds() {
        return Array.from(document.querySelectorAll('.post-checkbox:checked')).map(cb => parseInt(cb.dataset.id)).filter(id => !isNaN(id));
    }

    window.handlePostBatchOnline = async function () {
        const ids = getSelectedPostIds();
        if (ids.length === 0) { showNotification('请先选择要上线的帖子', 'warning'); return; }
        if (!confirm('确定要上线 ' + ids.length + ' 个帖子吗？')) return;
        showNotification('正在上线...', 'info');
        try {
            const response = await fetch('/api/posts/batch/online', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ postIds: ids })
            });
            const result = await response.json();
            if (response.ok && result.success) {
                showNotification(result.message || '成功上线 ' + ids.length + ' 个帖子', 'success');
                document.getElementById('postSelectAll').checked = false;
                loadUserPosts();
            } else { showNotification(result.error || '批量上线失败', 'error'); }
        } catch (e) { showNotification('操作失败，请检查网络连接', 'error'); }
    };

    window.handlePostBatchOffline = async function () {
        const ids = getSelectedPostIds();
        if (ids.length === 0) { showNotification('请先选择要下线的帖子', 'warning'); return; }
        if (!confirm('确定要下线 ' + ids.length + ' 个帖子吗？\n下线后帖子将变为仅自己可见')) return;
        showNotification('正在下线...', 'info');
        try {
            const response = await fetch('/api/posts/batch/offline', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ postIds: ids })
            });
            const result = await response.json();
            if (response.ok && result.success) {
                showNotification(result.message || '成功下线 ' + ids.length + ' 个帖子', 'success');
                document.getElementById('postSelectAll').checked = false;
                loadUserPosts();
            } else { showNotification(result.error || '批量下线失败', 'error'); }
        } catch (e) { showNotification('操作失败，请检查网络连接', 'error'); }
    };

    window.handlePostBatchDelete = async function () {
        const ids = getSelectedPostIds();
        if (ids.length === 0) { showNotification('请先选择要删除的帖子', 'warning'); return; }
        if (!confirm('确定要删除 ' + ids.length + ' 个帖子吗？\n\n警告：此操作不可恢复！')) return;
        showNotification('正在删除...', 'info');
        try {
            const response = await fetch('/api/posts/batch/delete', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ postIds: ids })
            });
            const result = await response.json();
            if (response.ok && result.success) {
                showNotification(result.message || '成功删除 ' + ids.length + ' 个帖子', 'success');
                document.getElementById('postSelectAll').checked = false;
                loadUserPosts();
            } else { showNotification(result.error || '批量删除失败', 'error'); }
        } catch (e) { showNotification('操作失败，请检查网络连接', 'error'); }
    };

    function showNotification(msg, type) {
        if (window.showNotification) window.showNotification(msg, type);
    }

})();
