/**
 * Forum List Page JavaScript
 * 论坛列表页面 JavaScript 模块
 */

(function () {
    'use strict';

    let currentPage = 0;
    let currentSort = 'latest';
    let currentKeyword = '';
    let hasMorePosts = true;
    let isLoading = false;
    let currentPostId = null;

    document.addEventListener('DOMContentLoaded', function () {
        initForum();
        bindEvents();
        handleUrlParams();
    });

    function handleUrlParams() {
        const params = new URLSearchParams(window.location.search);
        const q = params.get('q');
        if (q) {
            const searchInput = document.getElementById('searchInput');
            if (searchInput) searchInput.value = q;
            performSearch();
        }
    }

    async function initForum() {
        await initGuestCheck();
        await loadCategories();
        await loadForumStats();
        await loadPosts(true);
        await loadAnnouncements();
        await loadHotTopics();
        await checkAdminStatus();
    }

    // Load categories from API
    async function loadCategories() {
        try {
            const res = await fetch('/api/categories');
            const categories = await res.json();
            const select = document.getElementById('domainFilter');
            if (select && categories.length > 0) {
                categories.forEach(c => {
                    const option = document.createElement('option');
                    option.value = c.code;
                    option.textContent = c.name;
                    select.appendChild(option);
                });
            }
        } catch (e) {
            console.error('Failed to load categories:', e);
        }
    }

    function bindEvents() {
        const sortFilter = document.getElementById('sortFilter');
        if (sortFilter) {
            sortFilter.addEventListener('change', function () {
                currentSort = this.value;
                currentPage = 0;
                loadPosts(true);
            });
        }

        const searchInput = document.getElementById('searchInput');
        if (searchInput) {
            searchInput.addEventListener('keypress', function (e) {
                if (e.key === 'Enter') performSearch();
            });
        }

        // Modal character counters
        const messageContent = document.getElementById('messageContent');
        if (messageContent) {
            messageContent.addEventListener('input', function () {
                document.getElementById('charCount').textContent = this.value.length;
            });
        }

        const quickCommentContent = document.getElementById('quickCommentContent');
        if (quickCommentContent) {
            quickCommentContent.addEventListener('input', function () {
                document.getElementById('quickCommentCharCount').textContent = this.value.length;
            });
        }
    }

    async function loadForumStats() {
        try {
            const response = await fetch('/api/posts/stats');
            const data = await response.json();
            if (data.success) {
                document.getElementById('statTotalPosts').textContent = data.totalPosts || 0;
                document.getElementById('statTotalComments').textContent = data.totalComments || 0;
                document.getElementById('statTodayPosts').textContent = data.todayPosts || 0;
            }
        } catch (e) { /* Silently fail */ }
    }

    async function loadPosts(reset) {
        if (isLoading) return;
        isLoading = true;

        if (reset) {
            currentPage = 0;
            document.getElementById('postsList').innerHTML = '';
            document.getElementById('postsLoading').classList.remove('hidden');
            document.getElementById('postsEmpty').classList.add('hidden');
            if (!currentKeyword) await loadPinnedPosts();
        }

        try {
            let url = '/api/posts?page=' + currentPage + '&size=10&sort=' + currentSort;
            if (currentKeyword) url += '&keyword=' + encodeURIComponent(currentKeyword);

            const response = await fetch(url);
            const data = await response.json();

            document.getElementById('postsLoading').classList.add('hidden');

            if (data.success && data.posts && data.posts.length > 0) {
                renderPosts(data.posts);
                hasMorePosts = data.hasNext;
                document.getElementById('loadMoreSection').classList.toggle('hidden', !hasMorePosts);
            } else if (reset) {
                document.getElementById('postsEmpty').classList.remove('hidden');
                document.getElementById('loadMoreSection').classList.add('hidden');
            }
        } catch (e) {
            document.getElementById('postsLoading').classList.add('hidden');
            if (reset) document.getElementById('postsEmpty').classList.remove('hidden');
        }

        isLoading = false;
    }

    async function loadPinnedPosts() {
        try {
            const response = await fetch('/api/posts/pinned');
            const data = await response.json();
            if (data.success && data.posts && data.posts.length > 0) {
                const container = document.getElementById('postsList');
                data.posts.forEach(post => container.appendChild(createPostElement(post, true)));
            }
        } catch (e) { /* Silently fail */ }
    }

    function renderPosts(posts) {
        const container = document.getElementById('postsList');
        posts.forEach(post => container.appendChild(createPostElement(post, false)));
    }

    function createPostElement(post, isPinned) {
        const div = document.createElement('div');
        div.className = 'card bg-base-100 shadow-soft academic-post-card hover-lift';
        div.innerHTML = `
            <div class="card-body p-6">
                <div class="flex items-start gap-4">
                    <div class="avatar ${post.authorAvatar ? '' : 'placeholder'}">
                        <div class="w-12 h-12 rounded-full ${post.authorAvatar ? '' : 'bg-primary text-primary-content'}">
                            ${post.authorAvatar ? '<img src="' + post.authorAvatar + '" alt="头像" class="w-full h-full object-cover rounded-full">' : '<span>' + (post.authorName || '用').substring(0, 1) + '</span>'}
                        </div>
                    </div>
                    <div class="flex-1">
                        <div class="flex items-center gap-2 mb-2">
                            ${isPinned ? '<span class="badge badge-primary badge-sm">置顶</span>' : ''}
                            <span class="font-medium">${post.authorName || '匿名'}</span>
                            <span class="text-xs text-base-content/50">${post.createdAt || ''}</span>
                        </div>
                        <h3 class="text-lg font-semibold mb-2 cursor-pointer hover:text-primary" onclick="window.location.href='/community/post_detail.html?id=${post.postId}'">${post.postTitle || '无标题'}</h3>
                        <p class="text-base-content/70 line-clamp-3 mb-4">${post.postAbstract || ''}</p>
                        <div class="flex items-center gap-4 text-sm text-base-content/60">
                            <button class="like-btn flex items-center gap-1 hover:text-error ${post.liked ? 'liked' : ''}" onclick="toggleLike(${post.postId}, this)">
                                <span class="iconify" data-icon="heroicons:heart" data-width="16"></span>
                                <span>${post.likeCount || 0}</span>
                            </button>
                            <button class="flex items-center gap-1 hover:text-primary" onclick="openQuickComment(${post.postId})">
                                <span class="iconify" data-icon="heroicons:chat-bubble-left" data-width="16"></span>
                                <span>${post.commentCount || 0}</span>
                            </button>
                            <button class="flex items-center gap-1 hover:text-primary" onclick="openShareModal(${post.postId})">
                                <span class="iconify" data-icon="heroicons:share" data-width="16"></span>
                                <span>分享</span>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        return div;
    }

    // Global functions
    window.loadMorePosts = function () {
        currentPage++;
        loadPosts(false);
    };

    window.performSearch = function () {
        currentKeyword = document.getElementById('searchInput').value.trim();
        if (currentKeyword) {
            fetch('/api/history/search?type=forum&keyword=' + encodeURIComponent(currentKeyword), { method: 'POST' });
        }
        currentPage = 0;
        loadPosts(true);
    };

    window.resetFilters = function () {
        document.getElementById('searchInput').value = '';
        document.getElementById('sortFilter').value = 'latest';
        document.getElementById('domainFilter').value = '';
        currentKeyword = '';
        currentSort = 'latest';
        currentPage = 0;
        loadPosts(true);
    };

    window.toggleLike = async function (postId, btn) {
        if (!requireLogin('点赞')) return;
        try {
            const response = await fetch('/api/posts/' + postId + '/like', { method: 'POST', credentials: 'include' });
            if (checkNeedLogin(response)) return;
            const data = await response.json();
            if (data.success) {
                btn.classList.toggle('liked');
                const countSpan = btn.querySelector('span:last-child');
                countSpan.textContent = data.likeCount || parseInt(countSpan.textContent) + (btn.classList.contains('liked') ? 1 : -1);
            }
        } catch (e) {
            showNotification('操作失败', 'error');
        }
    };

    window.openQuickComment = function (postId) {
        if (!requireLogin('评论')) return;
        currentPostId = postId;
        document.getElementById('quickCommentContent').value = '';
        document.getElementById('quickCommentCharCount').textContent = '0';
        document.getElementById('quickCommentModal').showModal();
    };

    window.closeQuickCommentModal = function () {
        document.getElementById('quickCommentModal').close();
    };

    window.submitQuickComment = async function () {
        const content = document.getElementById('quickCommentContent').value.trim();
        if (!content) {
            showNotification('请输入评论内容', 'warning');
            return;
        }

        try {
            const response = await fetch('/api/posts/' + currentPostId + '/comments', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: content }),
                credentials: 'include'
            });
            const data = await response.json();
            if (data.success) {
                showNotification('评论发布成功', 'success');
                closeQuickCommentModal();
                loadPosts(true);
            } else {
                showNotification(data.error || '评论失败', 'error');
            }
        } catch (e) {
            showNotification('评论失败', 'error');
        }
    };

    window.openShareModal = function (postId) {
        currentPostId = postId;
        document.getElementById('shareLink').value = window.location.origin + '/community/post_detail.html?id=' + postId;
        document.getElementById('shareModal').showModal();
    };

    window.closeShareModal = function () {
        document.getElementById('shareModal').close();
    };

    window.copyShareLink = function () {
        const input = document.getElementById('shareLink');
        input.select();
        document.execCommand('copy');
        const btn = document.getElementById('copyButton');
        btn.classList.add('copied');
        btn.innerHTML = '<span class="iconify" data-icon="heroicons:check" data-width="16"></span> 已复制';
        setTimeout(() => {
            btn.classList.remove('copied');
            btn.innerHTML = '<span class="iconify" data-icon="heroicons:clipboard-document" data-width="16"></span> 复制';
        }, 2000);
    };

    window.shareToWeChat = function () { showNotification('请使用微信扫一扫分享', 'info'); };
    window.shareToWeibo = function () { window.open('https://service.weibo.com/share/share.php?url=' + encodeURIComponent(document.getElementById('shareLink').value)); };
    window.shareToQQ = function () { showNotification('请使用QQ分享', 'info'); };

    window.openPrivateMessage = function (userName) {
        document.getElementById('recipientName').value = userName;
        document.getElementById('messageContent').value = '';
        document.getElementById('charCount').textContent = '0';
        document.getElementById('privateMessageModal').showModal();
    };

    window.closePrivateMessageModal = function () {
        document.getElementById('privateMessageModal').close();
    };

    window.sendPrivateMessage = async function () {
        const content = document.getElementById('messageContent').value.trim();
        const recipient = document.getElementById('recipientName').value;
        if (!content) {
            showNotification('请输入消息内容', 'warning');
            return;
        }
        showNotification('私信发送成功', 'success');
        closePrivateMessageModal();
    };

    // ========== 热门话题 ==========

    async function loadHotTopics() {
        var container = document.getElementById('hotTopicsList');
        if (!container) return;
        try {
            var res = await fetch('/api/posts/hot?size=5');
            var data = await res.json();
            if (data.success && data.posts && data.posts.length > 0) {
                container.innerHTML = data.posts.map(function(post) {
                    var badgeClass = post.badge === 'new' ? 'badge-warning' : 'badge-error';
                    var badgeText = post.badge === 'new' ? '新' : '热';
                    return '<div class="p-3 rounded-lg bg-base-200 hover:bg-base-300 transition-colors cursor-pointer" onclick="window.location.href=\'/community/post_detail.html?id=' + post.postId + '\'">'
                        + '<div class="flex items-center justify-between mb-1">'
                        + '<span class="font-medium text-sm line-clamp-1">' + post.postTitle + '</span>'
                        + '<div class="badge ' + badgeClass + ' badge-xs">' + badgeText + '</div>'
                        + '</div>'
                        + '<div class="text-xs text-base-content/70">'
                        + post.commentCount + ' 讨论 \u2022 ' + post.viewCount + ' 浏览 \u2022 ' + post.likeCount + ' 赞'
                        + '</div></div>';
                }).join('');
            } else {
                container.innerHTML = '<div class="text-center text-sm text-base-content/50 py-2">暂无热门话题</div>';
            }
        } catch (e) {
            container.innerHTML = '<div class="text-center text-sm text-base-content/50 py-2">加载失败</div>';
        }
    }

    // ========== 公告功能 ==========

    async function loadAnnouncements() {
        try {
            const response = await fetch('/api/announcements');
            const data = await response.json();
            const container = document.getElementById('announcementList');
            if (!container) return;

            if (data.success && data.announcements && data.announcements.length > 0) {
                container.innerHTML = '';
                data.announcements.forEach(function (a) {
                    var alertClass = 'alert-info';
                    var iconName = 'heroicons:information-circle';
                    if (a.type === 'SUCCESS') { alertClass = 'alert-success'; iconName = 'heroicons:gift'; }
                    else if (a.type === 'WARNING') { alertClass = 'alert-warning'; iconName = 'heroicons:exclamation-triangle'; }
                    else if (a.type === 'ERROR') { alertClass = 'alert-error'; iconName = 'heroicons:exclamation-circle'; }

                    var pinned = a.isPinned === true;
                    var pinnedTag = pinned ? '<span class="badge badge-warning badge-xs">置顶</span>' : '';
                    var adminBtns = '';
                    if (window._isAdmin) {
                        var pinIcon = pinned ? 'heroicons:arrow-down-on-square' : 'heroicons:arrow-up-on-square';
                        var pinTitle = pinned ? '取消置顶' : '置顶';
                        adminBtns = '<div class="flex gap-0.5">'
                            + '<button class="btn btn-ghost btn-xs' + (pinned ? ' text-warning' : '') + '" onclick="toggleAnnouncementPin(' + a.id + ')" title="' + pinTitle + '">'
                            + '<span class="iconify" data-icon="' + pinIcon + '" data-width="14"></span></button>'
                            + '<button class="btn btn-ghost btn-xs" onclick="deleteAnnouncement(' + a.id + ')" title="删除">'
                            + '<span class="iconify" data-icon="heroicons:x-mark" data-width="14"></span></button>'
                            + '</div>';
                    }

                    var div = document.createElement('div');
                    div.className = 'alert ' + alertClass + ' p-3';
                    div.setAttribute('data-id', a.id);
                    div.innerHTML = '<span class="iconify" data-icon="' + iconName + '" data-width="16"></span>'
                        + '<div class="text-sm flex-1">'
                        + '<div class="font-medium flex items-center gap-1">' + (a.title || '') + pinnedTag + '</div>'
                        + '<div class="text-xs opacity-70">' + (a.content || '') + '</div>'
                        + '</div>'
                        + adminBtns;
                    container.appendChild(div);
                });
            } else {
                container.innerHTML = '<div class="text-center text-sm text-base-content/50 py-2">暂无公告</div>';
            }
        } catch (e) {
            var container = document.getElementById('announcementList');
            if (container) container.innerHTML = '<div class="text-center text-sm text-base-content/50 py-2">暂无公告</div>';
        }
    }

    async function checkAdminStatus() {
        try {
            const response = await fetch('/user/api/check-auth', { credentials: 'include' });
            const data = await response.json();
            if (data.authenticated && data.role === 'ADMIN') {
                window._isAdmin = true;
                var btn = document.getElementById('addAnnouncementBtn');
                if (btn) btn.classList.remove('hidden');
                loadAnnouncements();
            } else {
                window._isAdmin = false;
            }
        } catch (e) {
            window._isAdmin = false;
        }
    }

    window.openAnnouncementModal = function () {
        document.getElementById('announcementTitle').value = '';
        document.getElementById('announcementContent').value = '';
        document.getElementById('announcementType').value = 'INFO';
        document.getElementById('announcementModal').showModal();
    };

    window.closeAnnouncementModal = function () {
        document.getElementById('announcementModal').close();
    };

    window.submitAnnouncement = async function () {
        var title = document.getElementById('announcementTitle').value.trim();
        var content = document.getElementById('announcementContent').value.trim();
        var type = document.getElementById('announcementType').value;

        if (!title) {
            showNotification('请输入公告标题', 'warning');
            return;
        }

        try {
            var response = await fetch('/api/announcements', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ title: title, content: content, type: type }),
                credentials: 'include'
            });
            var data = await response.json();
            if (data.success) {
                showNotification('公告发布成功', 'success');
                closeAnnouncementModal();
                loadAnnouncements();
            } else {
                showNotification(data.error || '发布失败', 'error');
            }
        } catch (e) {
            showNotification('发布失败', 'error');
        }
    };

    window.toggleAnnouncementPin = async function (id) {
        try {
            var response = await fetch('/api/announcements/' + id + '/pin', {
                method: 'POST',
                credentials: 'include'
            });
            var data = await response.json();
            if (data.success) {
                showNotification(data.message || '操作成功', 'success');
                loadAnnouncements();
            } else {
                showNotification(data.error || '操作失败', 'error');
            }
        } catch (e) {
            showNotification('操作失败', 'error');
        }
    };

    window.deleteAnnouncement = async function (id) {
        if (!confirm('确定删除该公告？')) return;
        try {
            var response = await fetch('/api/announcements/' + id, {
                method: 'DELETE',
                credentials: 'include'
            });
            var data = await response.json();
            if (data.success) {
                showNotification('公告已删除', 'success');
                loadAnnouncements();
            } else {
                showNotification(data.error || '删除失败', 'error');
            }
        } catch (e) {
            showNotification('删除失败', 'error');
        }
    };

    function showNotification(message, type) {
        if (window.showNotification) window.showNotification(message, type);
    }

})();
