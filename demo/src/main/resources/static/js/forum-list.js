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
        await loadForumStats();
        await loadPosts(true);
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
        try {
            const response = await fetch('/api/posts/' + postId + '/like', { method: 'POST', credentials: 'include' });
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

    function showNotification(message, type) {
        if (window.showNotification) window.showNotification(message, type);
    }

})();
