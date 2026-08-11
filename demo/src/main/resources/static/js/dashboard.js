/**
 * Dashboard Page JavaScript
 * 管理中心页面 JavaScript 模块
 */

(function() {
    'use strict';

    // State
    let allGraphs = [], filteredGraphs = [], allPosts = [], filteredPosts = [];
    let currentPage = 1, postCurrentPage = 1, pageSize = 10, postPageSize = 10;
    let statusFilter = 'all', postStatusFilter = 'all';
    let searchQuery = '', postSearchQuery = '';
    let graphTotalElements = 0, graphTotalPages = 0, postTotalElements = 0, postTotalPages = 0;
    let currentUserId = null;

    function escapeHtml(value) {
        return String(value ?? '').replace(/[&<>"']/g, function(ch) {
            return {
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#39;'
            }[ch];
        });
    }

    function escapeJsString(value) {
        return String(value ?? '')
            .replace(/\\/g, '\\\\')
            .replace(/'/g, "\\'")
            .replace(/\r/g, '\\r')
            .replace(/\n/g, '\\n')
            .replace(/</g, '\\x3C')
            .replace(/>/g, '\\x3E');
    }

    function formatDate(value) {
        return value ? String(value).substring(0, 10) : '-';
    }

    function buildPaginationParams(page, size) {
        const params = new URLSearchParams();
        params.set('page', String(Math.max(0, page - 1)));
        params.set('size', String(size));
        return params;
    }

    function getPageItems(data, alias) {
        if (data && Array.isArray(data[alias])) return data[alias];
        if (data && Array.isArray(data.content)) return data.content;
        return [];
    }

    function toNumber(value, fallback) {
        const number = Number(value);
        return Number.isFinite(number) ? number : fallback;
    }

    function getOneBasedPage(data, requestedPage) {
        const zeroBased = data && data.page !== undefined ? data.page : data && data.number;
        return toNumber(zeroBased, requestedPage - 1) + 1;
    }

    function getGraphDomainFilter() {
        const input = document.getElementById('graphDomainFilter');
        const value = input ? input.value.trim() : '';
        return value && value !== 'all' ? value : '';
    }

    function getPaginationPages(current, total) {
        if (total <= 7) {
            return Array.from({ length: total }, (_, index) => index + 1);
        }

        const pages = [1];
        const start = Math.max(2, current - 2);
        const end = Math.min(total - 1, current + 2);

        if (start > 2) pages.push('ellipsis-start');
        for (let i = start; i <= end; i++) pages.push(i);
        if (end < total - 1) pages.push('ellipsis-end');
        pages.push(total);

        return pages;
    }

    document.addEventListener('DOMContentLoaded', function() {
        checkAdminAccess();
        loadGraphDomains();
        showSection('graph-batch');
    });

    function checkAdminAccess() {
        fetch('/user/api/check-auth', { credentials: 'include' })
            .then(res => res.json())
            .then(data => {
                if (data.authenticated && data.user) {
                    currentUserId = data.user.userId;
                    if (data.user.role === 'ADMIN') {
                        const adminSection = document.getElementById('admin-section');
                        if (adminSection) adminSection.classList.remove('hidden');
                    }
                }
            });
    }

    window.showSection = function(sectionName) {
        document.querySelectorAll('.content-section').forEach(s => s.classList.remove('active'));
        document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));

        const section = document.getElementById(sectionName + '-section');
        if (section) section.classList.add('active');

        const navItem = document.querySelector(`[data-section="${sectionName}"]`);
        if (navItem) navItem.classList.add('active');

        if (sectionName === 'graph-batch') loadUserGraphs();
        else if (sectionName === 'post-batch') loadUserPosts();
    };

    // Graph Management
    async function loadUserGraphs(page = currentPage) {
        const tbody = document.getElementById('graphTableBody');
        const paginationInfo = document.getElementById('paginationInfo');
        const requestedPage = Math.max(1, Number(page) || 1);
        currentPage = requestedPage;
        if (tbody) tbody.innerHTML = '<tr><td colspan="6" class="text-center py-8 text-base-content/60">加载中...</td></tr>';
        if (paginationInfo) paginationInfo.textContent = '正在加载图谱...';

        try {
            const params = buildPaginationParams(requestedPage, pageSize);
            params.set('sortBy', 'lastModified');
            if (statusFilter !== 'all') params.set('status', statusFilter);
            if (searchQuery) params.set('keyword', searchQuery);
            const domain = getGraphDomainFilter();
            if (domain) params.set('domain', domain);

            const response = await fetch('/api/graph/my?' + params.toString(), { credentials: 'include' });
            if (!response.ok) throw new Error('Load failed');

            const data = await response.json();
            allGraphs = getPageItems(data, 'graphs');
            filteredGraphs = allGraphs;
            graphTotalElements = toNumber(data.totalElements, allGraphs.length);
            graphTotalPages = toNumber(data.totalPages, Math.ceil(graphTotalElements / pageSize));
            currentPage = getOneBasedPage(data, requestedPage);

            if (allGraphs.length === 0 && graphTotalElements > 0 && requestedPage > 1) {
                return loadUserGraphs(Math.max(1, Math.min(requestedPage - 1, graphTotalPages || 1)));
            }

            renderGraphTable();
        } catch (e) {
            document.getElementById('graphTableBody').innerHTML = '<tr><td colspan="6" class="text-center py-8 text-error">加载失败</td></tr>';
            if (paginationInfo) paginationInfo.textContent = '加载失败';
        }
    }

    function applyFilters() {
        currentPage = 1;
        loadUserGraphs(1);
    }

    window.performSearch = function() {
        const input = document.getElementById('searchInput');
        searchQuery = input ? input.value.trim() : '';
        applyFilters();
    };

    window.filterByStatus = function(status) {
        statusFilter = status;
        applyFilters();
    };

    window.filterGraphDomain = function() {
        applyFilters();
    };

    function renderGraphTable() {
        const tbody = document.getElementById('graphTableBody');
        const total = graphTotalElements;
        const totalPages = graphTotalPages;
        const start = (currentPage - 1) * pageSize;
        const pageData = filteredGraphs;

        if (pageData.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center py-8 text-base-content/60">暂无图谱数据</td></tr>';
            document.getElementById('paginationInfo').textContent = '暂无记录';
            document.getElementById('paginationButtons').innerHTML = '';
            return;
        }

        tbody.innerHTML = pageData.map(g => {
            const statusMap = { 'PUBLISHED': { text: '已上线', badge: 'badge-success' }, 'DRAFT': { text: '草稿', badge: 'badge-info' }, 'PRIVATE': { text: '私有', badge: 'badge-warning' } };
            const status = statusMap[g.status] || { text: g.status || '未知', badge: 'badge-ghost' };
            const graphName = g.name || '未命名';
            const graphDescription = g.description || '暂无描述';
            const modifiedDate = g.lastModified || g.modifiedDate;
            return `<tr data-graph-id="${g.graphId}">
                <th><input type="checkbox" class="checkbox checkbox-primary graph-checkbox" data-id="${g.graphId}"></th>
                <td><div class="cursor-pointer" onclick="viewGraph(${g.graphId})"><div class="font-semibold text-primary hover:underline">${escapeHtml(graphName)}</div><div class="text-sm text-base-content/70 truncate max-w-xs">${escapeHtml(graphDescription)}</div></div></td>
                <td><span class="badge ${status.badge} badge-outline">${escapeHtml(status.text)}</span></td>
                <td>${formatDate(g.uploadDate)}</td>
                <td>${formatDate(modifiedDate)}</td>
                <td><div class="flex gap-1"><button class="btn btn-ghost btn-xs" onclick="editGraph(${g.graphId})"><span class="iconify" data-icon="heroicons:pencil" data-width="16"></span></button><button class="btn btn-ghost btn-xs text-error" onclick="deleteGraph(${g.graphId}, '${escapeJsString(graphName)}')"><span class="iconify" data-icon="heroicons:trash" data-width="16"></span></button></div></td>
            </tr>`;
        }).join('');

        const selectAll = document.getElementById('selectAll');
        if (selectAll) selectAll.checked = false;
        document.getElementById('paginationInfo').textContent = `显示 ${start + 1}-${Math.min(start + pageSize, total)} 条，共 ${total} 条`;
        renderPagination(totalPages);
    }

    function renderPagination(totalPages) {
        const container = document.getElementById('paginationButtons');
        if (!container) return;
        if (totalPages <= 1) { container.innerHTML = ''; return; }
        let html = `<button class="join-item btn btn-sm" onclick="goToPage(${currentPage - 1})" ${currentPage === 1 ? 'disabled' : ''}>«</button>`;
        getPaginationPages(currentPage, totalPages).forEach(item => {
            if (typeof item === 'string') {
                html += '<button class="join-item btn btn-sm" disabled>...</button>';
            } else {
                html += `<button class="join-item btn btn-sm ${item === currentPage ? 'btn-active' : ''}" onclick="goToPage(${item})">${item}</button>`;
            }
        });
        html += `<button class="join-item btn btn-sm" onclick="goToPage(${currentPage + 1})" ${currentPage === totalPages ? 'disabled' : ''}>»</button>`;
        container.innerHTML = html;
    }

    window.goToPage = function(page) {
        const totalPages = graphTotalPages;
        if (page < 1 || page > totalPages) return;
        currentPage = page;
        loadUserGraphs(page);
    };

    window.toggleSelectAll = function(checkbox) {
        document.querySelectorAll('.graph-checkbox').forEach(cb => cb.checked = checkbox.checked);
    };

    function getSelectedIds() {
        return Array.from(document.querySelectorAll('.graph-checkbox:checked')).map(cb => parseInt(cb.dataset.id)).filter(id => !isNaN(id));
    }

    window.viewGraph = function(graphId) {
        window.location.href = '/graph/graph_detail.html?id=' + graphId;
    };

    window.editGraph = async function(graphId) {
        try {
            const response = await fetch('/api/graph/' + graphId, { credentials: 'include' });
            if (!response.ok) throw new Error('获取失败');
            const data = await response.json();
            const graph = data.graph || data;

            document.getElementById('editGraphId').value = graph.graphId;
            document.getElementById('editGraphName').value = graph.name || '';
            document.getElementById('editGraphDescription').value = graph.description || '';
            document.getElementById('editGraphStatus').value = graph.status || 'DRAFT';

            document.getElementById('editModal').showModal();
        } catch (e) {
            showNotification('获取图谱信息失败', 'error');
        }
    };

    window.closeEditModal = function() {
        document.getElementById('editModal').close();
    };

    window.submitEditForm = async function() {
        const graphId = document.getElementById('editGraphId').value;
        const data = {
            name: document.getElementById('editGraphName').value.trim(),
            description: document.getElementById('editGraphDescription').value.trim(),
            status: document.getElementById('editGraphStatus').value
        };

        if (!data.name) { showNotification('请输入图谱名称', 'warning'); return; }

        try {
            const response = await fetch('/api/graph/' + graphId, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify(data)
            });

            if (response.ok) {
                showNotification('更新成功', 'success');
                closeEditModal();
                loadUserGraphs();
            } else {
                const err = await response.json();
                showNotification(err.error || '更新失败', 'error');
            }
        } catch (e) {
            showNotification('更新失败', 'error');
        }
    };

    window.deleteGraph = async function(graphId, graphName) {
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

    window.handleBatchOperation = async function(operation) {
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
    window.openUploadModal = function() {
        loadGraphDomains();
        document.getElementById('uploadModal').showModal();
    };

    window.closeUploadModal = function() {
        document.getElementById('uploadModal').close();
        document.getElementById('uploadForm').reset();
    };

    window.previewCover = function(input) {
        if (input.files && input.files[0]) {
            const reader = new FileReader();
            reader.onload = e => {
                document.getElementById('coverPreviewImg').src = e.target.result;
                document.getElementById('coverPreview').classList.remove('hidden');
            };
            reader.readAsDataURL(input.files[0]);
        }
    };

    window.submitUploadForm = async function() {
        const fileInput = document.getElementById('graphFile');
        if (!fileInput.files || !fileInput.files[0]) { showNotification('请选择文件', 'warning'); return; }

        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        formData.append('name', document.getElementById('graphName').value.trim());
        formData.append('description', document.getElementById('graphDescription').value.trim());
        formData.append('domain', document.getElementById('graphDomain').value || 'other');
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
    async function loadUserPosts(page = postCurrentPage) {
        const requestedPage = Math.max(1, Number(page) || 1);
        postCurrentPage = requestedPage;
        try {
            if (!currentUserId) {
                const authRes = await fetch('/user/api/check-auth', { credentials: 'include' });
                const authData = await authRes.json();
                if (!authData.authenticated) return;
                currentUserId = authData.user.userId;
            }

            const params = buildPaginationParams(requestedPage, postPageSize);
            if (postStatusFilter !== 'all') params.set('status', postStatusFilter);
            if (postSearchQuery) params.set('keyword', postSearchQuery);

            const response = await fetch('/api/posts/user/' + currentUserId + '?' + params.toString(), { credentials: 'include' });
            if (!response.ok) throw new Error('Load failed');

            const data = await response.json();
            allPosts = getPageItems(data, 'posts');
            filteredPosts = allPosts;
            postTotalElements = toNumber(data.totalElements, allPosts.length);
            postTotalPages = toNumber(data.totalPages, Math.ceil(postTotalElements / postPageSize));
            postCurrentPage = getOneBasedPage(data, requestedPage);

            if (allPosts.length === 0 && postTotalElements > 0 && requestedPage > 1) {
                return loadUserPosts(Math.max(1, Math.min(requestedPage - 1, postTotalPages || 1)));
            }

            renderPostTable();
        } catch (e) {
            document.getElementById('postTableBody').innerHTML = '<tr><td colspan="6" class="text-center py-8 text-error">加载失败</td></tr>';
        }
    }

    function applyPostFilters() {
        postCurrentPage = 1;
        loadUserPosts(1);
    }

    window.performPostSearch = function() {
        const input = document.getElementById('postSearchInput');
        postSearchQuery = input ? input.value.trim() : '';
        applyPostFilters();
    };

    window.filterPostsByStatus = function(status) {
        postStatusFilter = status;
        applyPostFilters();
    };

    function renderPostTable() {
        const tbody = document.getElementById('postTableBody');
        if (!tbody) return;
        const total = postTotalElements;
        const start = (postCurrentPage - 1) * postPageSize;
        const pageData = filteredPosts;

        if (pageData.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center py-8 text-base-content/60">暂无帖子</td></tr>';
            const info = document.getElementById('postPaginationInfo');
            const buttons = document.getElementById('postPaginationButtons');
            if (info) info.textContent = '暂无记录';
            if (buttons) buttons.innerHTML = '';
            return;
        }

        tbody.innerHTML = pageData.map(p => {
            const statusMap = { '已发布': 'badge-success', '草稿': 'badge-info' };
            const postTitle = p.postTitle || '无标题';
            const postAbstract = p.postAbstract || '暂无摘要';
            const postStatus = p.postStatus || '未知';
            const safeTitle = escapeJsString(postTitle);
            return `<tr data-post-id="${p.postId}">
                <th><input type="checkbox" class="checkbox checkbox-primary post-checkbox" data-id="${p.postId}"></th>
                <td><div class="cursor-pointer" onclick="viewPost(${p.postId})"><div class="font-semibold text-primary hover:underline">${escapeHtml(postTitle)}</div><div class="text-sm text-base-content/70 truncate max-w-xs">${escapeHtml(postAbstract)}</div></div></td>
                <td><span class="badge ${statusMap[p.postStatus] || 'badge-ghost'} badge-outline">${escapeHtml(postStatus)}</span></td>
                <td>${p.uploadTime ? p.uploadTime.substring(0, 10) : '-'}</td>
                <td>${p.likeCount || 0}</td>
                <td><div class="flex gap-1"><button class="btn btn-ghost btn-xs" onclick="editPost(${p.postId})" title="编辑"><span class="iconify" data-icon="heroicons:pencil" data-width="16"></span></button><button class="btn btn-ghost btn-xs text-error" onclick="deletePost(${p.postId}, '${safeTitle}')"><span class="iconify" data-icon="heroicons:trash" data-width="16"></span></button></div></td>
            </tr>`;
        }).join('');

        const selectAll = document.getElementById('postSelectAll');
        if (selectAll) selectAll.checked = false;
        const info = document.getElementById('postPaginationInfo');
        if (info) {
            info.textContent = `显示 ${start + 1}-${Math.min(start + postPageSize, total)} 条，共 ${total} 条`;
        }
        renderPostPagination(postTotalPages);
    }

    function renderPostPagination(totalPages) {
        const container = document.getElementById('postPaginationButtons');
        if (!container) return;
        if (totalPages <= 1) { container.innerHTML = ''; return; }
        let html = `<button class="join-item btn btn-sm" onclick="goToPostPage(${postCurrentPage - 1})" ${postCurrentPage === 1 ? 'disabled' : ''}>«</button>`;
        getPaginationPages(postCurrentPage, totalPages).forEach(item => {
            if (typeof item === 'string') {
                html += '<button class="join-item btn btn-sm" disabled>...</button>';
            } else {
                html += `<button class="join-item btn btn-sm ${item === postCurrentPage ? 'btn-active' : ''}" onclick="goToPostPage(${item})">${item}</button>`;
            }
        });
        html += `<button class="join-item btn btn-sm" onclick="goToPostPage(${postCurrentPage + 1})" ${postCurrentPage === totalPages ? 'disabled' : ''}>»</button>`;
        container.innerHTML = html;
    }

    window.goToPostPage = function(page) {
        const totalPages = postTotalPages;
        if (page < 1 || page > totalPages) return;
        postCurrentPage = page;
        loadUserPosts(page);
    };

    async function loadGraphDomains() {
        const uploadSelect = document.getElementById('graphDomain');
        const filterSelect = document.getElementById('graphDomainFilter');
        if ((!uploadSelect || uploadSelect.dataset.loaded === 'true')
            && (!filterSelect || filterSelect.dataset.loaded === 'true')) return;

        try {
            const response = await fetch('/api/categories');
            if (!response.ok) throw new Error('Load categories failed');
            const categories = await response.json();
            if (!Array.isArray(categories) || categories.length === 0) return;

            const options = categories.map(category => {
                const code = category.code || category.name || 'other';
                const name = category.name || code;
                return `<option value="${escapeHtml(code)}">${escapeHtml(name)}</option>`;
            }).join('');

            if (uploadSelect) {
                uploadSelect.innerHTML = options;
                uploadSelect.value = categories.some(category => category.code === 'other') ? 'other' : (categories[0].code || categories[0].name || 'other');
                uploadSelect.dataset.loaded = 'true';
            }

            if (filterSelect) {
                filterSelect.innerHTML = '<option value="all">全部领域</option>' + options;
                filterSelect.dataset.loaded = 'true';
            }
        } catch (e) {
            // Keep the static fallback option.
        }
    }

    window.viewPost = function(postId) {
        window.location.href = '/community/post_detail.html?id=' + postId;
    };

    window.deletePost = async function(postId, postTitle) {
        if (!confirm('确定删除"' + (postTitle || '') + '"吗？')) return;
        try {
            await fetch('/api/posts/' + postId, { method: 'DELETE', credentials: 'include' });
            showNotification('已删除', 'success');
            loadUserPosts();
        } catch (e) {
            showNotification('删除失败', 'error');
        }
    };

    window.editPost = async function(postId) {
        try {
            const response = await fetch('/api/posts/' + postId, { credentials: 'include' });
            if (!response.ok) { showNotification('获取帖子信息失败', 'error'); return; }
            const data = await response.json();
            const post = data.post || data;

            document.getElementById('editPostId').value = post.postId;
            document.getElementById('editPostTitle').value = post.postTitle || '';
            document.getElementById('editPostAbstract').value = post.postAbstract || '';
            document.getElementById('editPostContent').value = post.postText || '';
            document.getElementById('editPostStatus').value = post.postStatus || '草稿';

            document.getElementById('editPostModal').showModal();
        } catch (e) {
            showNotification('获取帖子信息失败', 'error');
        }
    };

    window.closeEditPostModal = function() {
        document.getElementById('editPostModal').close();
    };

    window.submitEditPost = async function() {
        const postId = document.getElementById('editPostId').value;
        const data = {
            title: document.getElementById('editPostTitle').value.trim(),
            content: document.getElementById('editPostContent').value,
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

    window.goToCreatePost = function() {
        window.location.href = '/community/post_edit.html';
    };

    window.togglePostSelectAll = function(checkbox) {
        document.querySelectorAll('.post-checkbox').forEach(cb => cb.checked = checkbox.checked);
    };

    function getSelectedPostIds() {
        return Array.from(document.querySelectorAll('.post-checkbox:checked')).map(cb => parseInt(cb.dataset.id)).filter(id => !isNaN(id));
    }

    window.handlePostBatchOnline = async function() {
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

    window.handlePostBatchOffline = async function() {
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

    window.handlePostBatchDelete = async function() {
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
