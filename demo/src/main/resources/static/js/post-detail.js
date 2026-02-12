// Post Detail Page JavaScript
let postId = new URLSearchParams(window.location.search).get('id');
let currentPost = null;
let currentUser = null;
let authorId = null;
let replyToCommentId = null;

document.addEventListener('DOMContentLoaded', async function () {
    marked.setOptions({
        gfm: true,
        breaks: true,
        highlight: function (code, lang) {
            if (hljs.getLanguage(lang)) {
                return hljs.highlight(code, { language: lang }).value;
            }
            return hljs.highlightAuto(code).value;
        },
        langPrefix: 'hljs language-'
    });

    await initGuestCheck();
    await checkLogin();
    if (postId) {
        loadPost();
    } else {
        loadMockData();
    }
    initScrollSpy();
});

// ========== Auth ==========
async function checkLogin() {
    try {
        const res = await fetch('/user/api/check-auth', { credentials: 'include' });
        const data = await res.json();
        if (data.authenticated && data.user) {
            currentUser = data.user;
            window._isLoggedIn = true;
            window._currentUser = data.user;
            const loggedInEl = document.querySelector('[data-user-control="logged-in"]');
            const loggedOutEl = document.querySelector('[data-user-control="logged-out"]');
            if (loggedInEl) loggedInEl.style.display = 'block';
            if (loggedOutEl) loggedOutEl.style.display = 'none';
            const initial = currentUser.userName ? currentUser.userName.charAt(0) : '我';
            const navAvatarEl = document.getElementById('nav-avatar-text');
            if (navAvatarEl) navAvatarEl.textContent = initial;
            const currentUserAvatarEl = document.getElementById('currentUserAvatar');
            if (currentUserAvatarEl) currentUserAvatarEl.textContent = initial;
        } else {
            const commentInputArea = document.getElementById('commentInputArea');
            const loginPrompt = document.getElementById('loginPrompt');
            if (commentInputArea) commentInputArea.classList.add('hidden');
            if (loginPrompt) loginPrompt.classList.remove('hidden');
        }
    } catch (e) { }
}

async function logout() {
    try {
        await fetch('/user/api/logout', { method: 'POST', credentials: 'include' });
        window.location.href = '/user/login_register.html';
    } catch (e) {
        window.location.href = '/user/login_register.html';
    }
}

// ========== Load Post ==========
async function loadPost() {
    try {
        const res = await fetch('/api/posts/' + postId, { credentials: 'include' });
        const data = await res.json();
        if (data.success) {
            renderPost(data);
            loadComments();
            checkFavoriteStatus();
            checkFollowStatus(data.post.authorId);
        } else {
            showToast('加载失败: ' + data.error, 'error');
            const postLoading = document.getElementById('postLoading');
            const postContent = document.getElementById('postContent');
            if (postLoading) postLoading.classList.add('hidden');
            if (postContent) {
                postContent.innerHTML = '<div class="text-center py-20"><p class="text-base-content/60 mb-4">' + (data.error || '帖子加载失败') + '</p><a href="/community/forum_list.html" class="btn btn-primary">返回论坛</a></div>';
                postContent.classList.remove('hidden');
            }
        }
    } catch (e) {
        loadMockData();
    }
}

function loadMockData() {
    const mockData = {
        post: {
            postId: 0, postTitle: "知识图谱构建指南", authorId: 999, uploadTime: new Date().toISOString(), likeCount: 42, viewCount: 128, graphId: null,
            postText: "# 知识图谱简介\n\n知识图谱是一种基于图的数据结构。\n\n## 核心技术\n\n1. **实体识别**\n2. **关系抽取**\n\n### 代码示例\n\n```python\nimport networkx as nx\nG = nx.Graph()\nG.add_node('AI')\nprint(G.nodes())\n```\n\n## 总结\n\n构建知识图谱需要多方面技术支撑。"
        },
        authorName: "图谱专家", tags: [{ tagName: "知识图谱" }, { tagName: "AI" }], commentCount: 3, liked: false
    };
    renderPost(mockData);
}

// ========== Render Post ==========
function renderPost(data) {
    currentPost = data.post;
    authorId = data.post.authorId;

    var postTitleEl = document.getElementById('postTitle');
    if (postTitleEl) postTitleEl.textContent = data.post.postTitle;
    var headerTitleEl = document.getElementById('headerTitle');
    if (headerTitleEl) headerTitleEl.textContent = data.post.postTitle;
    var postTimeEl = document.getElementById('postTime');
    if (postTimeEl) postTimeEl.textContent = new Date(data.post.uploadTime).toLocaleString();
    var likeCountEl = document.getElementById('likeCount');
    if (likeCountEl) likeCountEl.textContent = data.post.likeCount || 0;
    var commentCountEl = document.getElementById('commentCount');
    if (commentCountEl) commentCountEl.textContent = data.commentCount || 0;
    var totalCommentsEl = document.getElementById('totalComments');
    if (totalCommentsEl) totalCommentsEl.textContent = data.commentCount || 0;

    // Stats
    var statLikesEl = document.getElementById('statLikes');
    if (statLikesEl) statLikesEl.textContent = data.post.likeCount || 0;
    var statCommentsEl = document.getElementById('statComments');
    if (statCommentsEl) statCommentsEl.textContent = data.commentCount || 0;
    var statViewsEl = document.getElementById('statViews');
    if (statViewsEl) statViewsEl.textContent = data.post.viewCount || 0;
    var postViewCountEl = document.getElementById('postViewCount');
    if (postViewCountEl) postViewCountEl.textContent = (data.post.viewCount || 0) + ' 阅读';

    // Author
    const authorName = data.authorName || '匿名';
    const authorInitial = authorName.charAt(0);
    var authorNameEl = document.getElementById('authorName');
    if (authorNameEl) authorNameEl.textContent = authorName;
    var sidebarAuthorNameEl = document.getElementById('sidebarAuthorName');
    if (sidebarAuthorNameEl) sidebarAuthorNameEl.textContent = authorName;
    var authorLinkEl = document.getElementById('authorLink');
    var sidebarAuthorLinkEl = document.getElementById('sidebarAuthorLink');
    if (authorName === '已注销用户') {
        if (authorLinkEl) { authorLinkEl.removeAttribute('href'); authorLinkEl.style.pointerEvents = 'none'; authorLinkEl.style.cursor = 'default'; }
        if (sidebarAuthorLinkEl) { sidebarAuthorLinkEl.removeAttribute('href'); sidebarAuthorLinkEl.style.pointerEvents = 'none'; sidebarAuthorLinkEl.style.cursor = 'default'; }
    } else {
        if (authorLinkEl) authorLinkEl.href = '/user/profile.html?id=' + data.post.authorId;
        if (sidebarAuthorLinkEl) sidebarAuthorLinkEl.href = '/user/profile.html?id=' + data.post.authorId;
    }

    // Avatar with image support
    var authorAvatarEl = document.getElementById('authorAvatar');
    var sidebarAvatarEl = document.getElementById('sidebarAuthorAvatar');
    if (data.authorAvatar) {
        if (authorAvatarEl) authorAvatarEl.innerHTML = '<img src="' + data.authorAvatar + '" class="w-full h-full rounded-full object-cover" alt="" />';
        if (sidebarAvatarEl) sidebarAvatarEl.innerHTML = '<img src="' + data.authorAvatar + '" class="w-full h-full rounded-full object-cover" alt="" />';
    } else {
        if (authorAvatarEl) authorAvatarEl.textContent = authorInitial;
        if (sidebarAvatarEl) sidebarAvatarEl.textContent = authorInitial;
    }

    // Tags
    const tagsContainer = document.getElementById('postTags');
    if (tagsContainer) {
        tagsContainer.innerHTML = '';
        if (data.tags) {
            data.tags.forEach(function (tag) {
                const span = document.createElement('span');
                span.className = 'badge badge-outline';
                span.textContent = tag.tagName;
                tagsContainer.appendChild(span);
            });
        }
    }

    // Category (领域分类)
    var postCategoryEl = document.getElementById('postCategory');
    if (postCategoryEl) {
        if (data.categoryName) {
            postCategoryEl.textContent = data.categoryName;
            postCategoryEl.style.display = 'inline-flex';
        } else if (data.post.category) {
            // Fallback to category code
            var categoryMap = {
                'ai': '人工智能',
                'medical': '医疗健康',
                'finance': '金融经济',
                'law': '法律法规',
                'education': '教育学习',
                'tech': '科技技术',
                'other': '其他领域'
            };
            postCategoryEl.textContent = categoryMap[data.post.category] || data.post.category;
            postCategoryEl.style.display = 'inline-flex';
        } else {
            postCategoryEl.style.display = 'none';
        }
    }

    // Related Graph
    if (data.post.graphId) {
        var relatedGraphCardEl = document.getElementById('relatedGraphCard');
        if (relatedGraphCardEl) relatedGraphCardEl.style.display = 'block';
        var relatedGraphLinkEl = document.getElementById('relatedGraphLink');
        if (relatedGraphLinkEl) relatedGraphLinkEl.href = '/graph/graph_detail.html?id=' + data.post.graphId;
        var relatedGraphNameEl = document.getElementById('relatedGraphName');
        if (data.graphName && relatedGraphNameEl) relatedGraphNameEl.textContent = data.graphName;
    }

    // Permissions
    if (currentUser) {
        if (currentUser.userId === data.post.authorId) {
            var editBtnEl = document.getElementById('editBtn');
            if (editBtnEl) editBtnEl.style.display = 'inline-flex';
            var deleteBtnEl = document.getElementById('deleteBtn');
            if (deleteBtnEl) deleteBtnEl.style.display = 'inline-flex';
        }
        if (currentUser.role === 'ADMIN') {
            var unpublishBtnEl = document.getElementById('unpublishBtn');
            if (unpublishBtnEl) unpublishBtnEl.style.display = 'inline-flex';
            var pinBtnEl = document.getElementById('pinBtn');
            if (pinBtnEl) pinBtnEl.style.display = 'inline-flex';
            updatePinButton(data.post.isPinned);
        }
    }

    // Render Markdown
    const rawContent = data.post.postText || '';
    const htmlContent = DOMPurify.sanitize(marked.parse(rawContent));
    var postBodyEl = document.getElementById('postBody');
    if (postBodyEl) {
        postBodyEl.innerHTML = htmlContent;
        hljs.highlightAll();
        addCodeCopyButtons();
        generateTOC();
    }

    var postLoadingEl = document.getElementById('postLoading');
    if (postLoadingEl) postLoadingEl.classList.add('hidden');
    var postContentEl = document.getElementById('postContent');
    if (postContentEl) postContentEl.classList.remove('hidden');

    if (data.liked) {
        var likeBtnEl = document.getElementById('likeBtn');
        if (likeBtnEl) likeBtnEl.classList.add('btn-active', 'btn-primary');
    }
}

// ========== Code Copy Buttons ==========
function addCodeCopyButtons() {
    document.querySelectorAll('.markdown-body pre').forEach(function (pre) {
        const btn = document.createElement('button');
        btn.className = 'code-copy-btn';
        btn.textContent = '复制';
        btn.onclick = function () {
            const code = pre.querySelector('code');
            navigator.clipboard.writeText(code.textContent).then(function () {
                btn.textContent = '已复制!';
                setTimeout(function () { btn.textContent = '复制'; }, 2000);
            });
        };
        pre.appendChild(btn);
    });
}

// ========== TOC ==========
function generateTOC() {
    const headers = document.querySelectorAll('#postBody h1, #postBody h2, #postBody h3');
    const tocContainer = document.getElementById('toc');
    if (headers.length === 0) {
        tocContainer.innerHTML = '<p class="text-base-content/40 italic text-sm p-3">暂无目录</p>';
        return;
    }
    tocContainer.innerHTML = '';
    headers.forEach(function (header, index) {
        const id = 'header-' + index;
        header.id = id;
        const link = document.createElement('a');
        link.href = '#' + id;
        link.textContent = header.textContent;
        link.className = 'toc-link';
        if (header.tagName === 'H2') link.classList.add('level-2');
        if (header.tagName === 'H3') link.classList.add('level-3');
        link.onclick = function (e) {
            e.preventDefault();
            document.getElementById(id).scrollIntoView({ behavior: 'smooth', block: 'start' });
        };
        tocContainer.appendChild(link);
    });
}

// ========== Scroll Spy ==========
function initScrollSpy() {
    const mainContent = document.getElementById('mainContent');
    if (!mainContent) return;
    mainContent.addEventListener('scroll', function () {
        const headers = document.querySelectorAll('#postBody h1, #postBody h2, #postBody h3');
        let current = '';
        headers.forEach(function (header) {
            const rect = header.getBoundingClientRect();
            if (rect.top <= 100) current = header.id;
        });
        document.querySelectorAll('.toc-link').forEach(function (link) {
            link.classList.remove('active');
            if (link.getAttribute('href') === '#' + current) link.classList.add('active');
        });
    });
}

// ========== Comments ==========
async function loadComments() {
    if (!postId) return;
    try {
        const res = await fetch('/api/posts/' + postId + '/comments');
        const data = await res.json();
        if (data.success) {
            const list = document.getElementById('commentsList');
            list.innerHTML = '';
            if (!data.comments || data.comments.length === 0) {
                list.innerHTML = '<p class="text-center text-base-content/40 py-8">暂无评论，快来抢沙发</p>';
                updateCommentCount(0);
                return;
            }
            var total = 0;
            data.comments.forEach(function (c) {
                list.innerHTML += createCommentHTML(c, false);
                total++;
                if (c.replies) {
                    c.replies.forEach(function (r) { list.innerHTML += createCommentHTML(r, true, c.username); total++; });
                }
            });
            updateCommentCount(total);
        }
    } catch (e) { }
}

function updateCommentCount(count) {
    var el1 = document.getElementById('commentCount');
    var el2 = document.getElementById('totalComments');
    var el3 = document.getElementById('statComments');
    if (el1) el1.textContent = count;
    if (el2) el2.textContent = count;
    if (el3) el3.textContent = count;
}

function createCommentHTML(comment, isReply, parentName) {
    var name = comment.username || '匿名';
    var initial = name.charAt(0);
    var replyPrefix = isReply && parentName ? '<span class="text-primary">@' + parentName + '</span> ' : '';
    var replyClass = isReply ? 'reply-item level-2' : '';
    var canDelete = currentUser && (currentUser.userId === comment.userId || currentUser.role === 'ADMIN');
    var avatarHtml = comment.avatar
        ? '<img src="' + comment.avatar + '" class="w-8 h-8 rounded-full object-cover" alt="" />'
        : initial;

    var html = '<div class="flex gap-3 p-3 rounded-lg ' + replyClass + '">';
    html += '<div class="w-8 h-8 rounded-full bg-secondary text-secondary-content flex items-center justify-center text-xs font-bold flex-shrink-0">' + avatarHtml + '</div>';
    html += '<div class="flex-1">';
    html += '<div class="flex justify-between items-center mb-1">';
    html += '<span class="font-bold text-sm">' + name + '</span>';
    html += '<div class="flex items-center gap-2">';
    html += '<span class="text-xs text-base-content/50">' + new Date(comment.commentTime).toLocaleString() + '</span>';
    if (canDelete) {
        html += '<button class="btn btn-ghost btn-xs text-error" onclick="deleteComment(' + comment.commentId + ')">删除</button>';
    }
    html += '</div></div>';
    html += '<p class="text-sm text-base-content/80">' + replyPrefix + comment.commentText + '</p>';
    html += '<button class="btn btn-ghost btn-xs mt-1" onclick="replyTo(' + comment.commentId + ', \'' + name.replace(/'/g, "\\'") + '\')">回复</button>';
    html += '</div></div>';
    return html;
}

function replyTo(commentId, name) {
    replyToCommentId = commentId;
    document.getElementById('replyingTo').classList.remove('hidden');
    document.getElementById('replyingToName').textContent = name;
    document.getElementById('commentText').focus();
    document.getElementById('commentText').placeholder = '回复 ' + name + '...';
}

function cancelReply() {
    replyToCommentId = null;
    document.getElementById('replyingTo').classList.add('hidden');
    document.getElementById('commentText').placeholder = '写下你的想法...';
}

async function submitComment() {
    if (!requireLogin('评论')) return;
    const text = document.getElementById('commentText').value;
    if (!text.trim()) { showToast('请输入评论内容', 'warning'); return; }
    if (!postId) { showToast('模拟模式无法评论', 'info'); return; }
    try {
        const body = { text: text };
        if (replyToCommentId) body.parentCommentId = replyToCommentId;
        const res = await fetch('/api/posts/' + postId + '/comments', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body), credentials: 'include'
        });
        const data = await res.json();
        if (data.success) {
            showToast('评论成功', 'success');
            document.getElementById('commentText').value = '';
            cancelReply();
            loadComments();
        } else { showToast('评论失败: ' + data.error, 'error'); }
    } catch (e) { showToast('网络错误', 'error'); }
}

async function deleteComment(commentId) {
    if (!confirm('确定删除此评论？')) return;
    try {
        const res = await fetch('/api/comments/' + commentId, { method: 'DELETE', credentials: 'include' });
        const data = await res.json();
        if (data.success) { showToast('删除成功', 'success'); loadComments(); }
        else { showToast(data.error, 'error'); }
    } catch (e) { showToast('删除失败', 'error'); }
}

// ========== Like/Favorite/Follow ==========
async function toggleLike() {
    if (!requireLogin('点赞')) return;
    if (!postId) {
        const btn = document.getElementById('likeBtn');
        const count = document.getElementById('likeCount');
        if (btn.classList.contains('btn-primary')) {
            btn.classList.remove('btn-active', 'btn-primary');
            count.textContent = parseInt(count.textContent) - 1;
        } else {
            btn.classList.add('btn-active', 'btn-primary');
            count.textContent = parseInt(count.textContent) + 1;
        }
        return;
    }
    try {
        const res = await fetch('/api/posts/' + postId + '/like', { method: 'POST', credentials: 'include' });
        if (checkNeedLogin(res)) return;
        const data = await res.json();
        if (data.success) {
            const btn = document.getElementById('likeBtn');
            const count = document.getElementById('likeCount');
            if (data.liked) { btn.classList.add('btn-active', 'btn-primary'); count.textContent = parseInt(count.textContent) + 1; }
            else { btn.classList.remove('btn-active', 'btn-primary'); count.textContent = parseInt(count.textContent) - 1; }
        } else { showToast(data.error, 'error'); }
    } catch (e) { showToast('操作失败', 'error'); }
}

async function toggleFavorite() {
    if (!requireLogin('收藏')) return;
    if (!postId) { showToast('收藏成功', 'success'); return; }
    try {
        const res = await fetch('/api/posts/' + postId + '/favorite', { method: 'POST', credentials: 'include' });
        if (checkNeedLogin(res)) return;
        const data = await res.json();
        if (data.success) {
            const icon = document.getElementById('favoriteIcon');
            if (data.favorited) {
                icon.setAttribute('data-icon', 'heroicons:bookmark-solid');
                let favorites = JSON.parse(localStorage.getItem('post_favorites') || '[]');
                if (!favorites.find(p => p.id == postId)) {
                    favorites.push({ id: postId, title: currentPost ? currentPost.postTitle : '帖子', createdAt: new Date().toISOString() });
                    localStorage.setItem('post_favorites', JSON.stringify(favorites));
                }
                showToast('已收藏', 'success');
            } else {
                icon.setAttribute('data-icon', 'heroicons:bookmark');
                let favorites = JSON.parse(localStorage.getItem('post_favorites') || '[]');
                favorites = favorites.filter(p => p.id != postId);
                localStorage.setItem('post_favorites', JSON.stringify(favorites));
                showToast('已取消收藏', 'info');
            }
        } else { showToast(data.error, 'error'); }
    } catch (e) { showToast('操作失败', 'error'); }
}

async function checkFavoriteStatus() {
    if (!postId || !currentUser) return;
    try {
        const res = await fetch('/api/posts/' + postId + '/favorite/status', { credentials: 'include' });
        const data = await res.json();
        if (data.success && data.favorited) {
            document.getElementById('favoriteIcon').setAttribute('data-icon', 'heroicons:bookmark-solid');
        }
    } catch (e) { }
}

async function toggleFollow() {
    if (!requireLogin('关注')) return;
    if (!authorId) return;
    var sidebarName = document.getElementById('sidebarAuthorName');
    if (sidebarName && sidebarName.textContent === '已注销用户') { showToast('该用户已注销，无法关注', 'warning'); return; }
    try {
        const res = await fetch('/api/users/' + authorId + '/follow', { method: 'POST', credentials: 'include' });
        if (checkNeedLogin(res)) return;
        const data = await res.json();
        if (data.success) {
            const btn = document.getElementById('followBtn');
            if (data.following) { btn.innerHTML = '<span class="iconify" data-icon="heroicons:user-minus" data-width="16"></span> 已关注'; btn.classList.add('btn-primary'); }
            else { btn.innerHTML = '<span class="iconify" data-icon="heroicons:user-plus" data-width="16"></span> 关注作者'; btn.classList.remove('btn-primary'); }
        } else { showToast(data.error, 'error'); }
    } catch (e) { showToast('操作失败', 'error'); }
}

window.sendPrivateMessage = function () {
    if (!requireLogin('发送私信')) return;
    if (!authorId) return;
    const authorName = document.getElementById('sidebarAuthorName').textContent;
    if (authorName === '已注销用户') { showToast('该用户已注销，无法发送私信', 'warning'); return; }
    window.location.href = `/user/profile.html?section=messages&targetUserId=${authorId}&targetUserName=${encodeURIComponent(authorName)}`;
}

async function checkFollowStatus(userId) {
    if (!userId || !currentUser) return;

    // Show Message Button if not self
    if (currentUser.userId !== userId) {
        const msgBtn = document.getElementById('messageBtn');
        if (msgBtn) msgBtn.style.display = '';
    }

    try {
        const res = await fetch('/api/users/' + userId + '/follow/status', { credentials: 'include' });
        const data = await res.json();
        if (data.success && data.following) {
            const btn = document.getElementById('followBtn');
            btn.innerHTML = '<span class="iconify" data-icon="heroicons:user-minus" data-width="16"></span> 已关注';
            btn.classList.add('btn-primary');
        }
    } catch (e) { }
}

// ========== Pin (Admin) ==========
function updatePinButton(isPinned) {
    var btn = document.getElementById('pinBtn');
    var icon = document.getElementById('pinIcon');
    if (!btn || !icon) return;
    if (isPinned) {
        btn.classList.add('text-primary');
        btn.setAttribute('data-tip', '取消置顶');
        icon.setAttribute('data-icon', 'heroicons:arrow-up-circle-solid');
    } else {
        btn.classList.remove('text-primary');
        btn.setAttribute('data-tip', '置顶');
        icon.setAttribute('data-icon', 'heroicons:arrow-up-circle');
    }
}

async function togglePin() {
    if (!postId) return;
    try {
        var res = await fetch('/api/posts/' + postId + '/pin', { method: 'POST', credentials: 'include' });
        var data = await res.json();
        if (data.success) {
            updatePinButton(data.pinned);
            showToast(data.pinned ? '已置顶' : '已取消置顶', 'success');
        } else {
            showToast(data.error || '操作失败', 'error');
        }
    } catch (e) { showToast('操作失败', 'error'); }
}

// ========== Post Actions ==========
function sharePost() { navigator.clipboard.writeText(window.location.href); showToast('链接已复制', 'success'); }
function scrollToComments() { document.getElementById('commentsSection').scrollIntoView({ behavior: 'smooth' }); }
function editPost() { window.location.href = '/community/post_edit.html?id=' + postId; }

async function deletePost() {
    if (!confirm('确定要删除这篇帖子吗？')) return;
    try {
        const res = await fetch('/api/posts/' + postId, { method: 'DELETE', credentials: 'include' });
        const data = await res.json();
        if (data.success) { showToast('删除成功', 'success'); setTimeout(function () { window.location.href = '/community/forum_list.html'; }, 1000); }
        else { showToast(data.error || '删除失败', 'error'); }
    } catch (e) { showToast('删除失败', 'error'); }
}

async function unpublishPost() {
    if (!confirm('确定要下架这篇帖子吗？')) return;
    showToast('下架功能开发中', 'info');
}

function showToast(msg, type) {
    type = type || 'info';
    const t = document.getElementById('toast');
    if (!t) return;
    document.getElementById('toastMessage').textContent = msg;
    const alert = t.querySelector('.alert');
    alert.className = 'alert';
    if (type === 'success') alert.classList.add('alert-success');
    if (type === 'error') alert.classList.add('alert-error');
    if (type === 'warning') alert.classList.add('alert-warning');

    t.classList.remove('hidden');
    setTimeout(function () { t.classList.add('hidden'); }, 3000);
}
