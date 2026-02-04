/**
 * Profile Page JavaScript
 * 个人中心页面 JavaScript 模块
 */

(function () {
    'use strict';

    let currentSection = 'profile';
    let confirmAction = null;
    let currentSearchType = 'graph';
    let currentBrowseType = 'graph';

    document.addEventListener('DOMContentLoaded', function () {
        const urlParams = new URLSearchParams(window.location.search);
        const section = urlParams.get('section');
        if (section && document.getElementById(section + '-section')) {
            showSection(section);
        } else {
            showSection('profile');
        }

        // 自动打开私信
        const targetUserId = urlParams.get('targetUserId');
        const targetUserName = urlParams.get('targetUserName');
        if (targetUserId && section === 'messages') {
            setTimeout(() => {
                if (window.openMessage) window.openMessage(Number(targetUserId), targetUserName);
            }, 500);
        }

        loadUserProfile();
        initializeFormSubmit();
    });

    window.showSection = function (sectionName) {
        document.querySelectorAll('.content-section').forEach(s => s.classList.add('hidden'));
        document.querySelectorAll('.menu li a').forEach(item => item.classList.remove('active'));

        const targetSection = document.getElementById(sectionName + '-section');
        if (targetSection) targetSection.classList.remove('hidden');

        const activeNav = document.getElementById('nav-' + sectionName);
        if (activeNav) activeNav.classList.add('active');

        currentSection = sectionName;

        if (sectionName === 'favorites') loadFavorites();
        else if (sectionName === 'search-history') loadSearchHistory(currentSearchType);
        else if (sectionName === 'browse-history') loadBrowseHistory(currentBrowseType);
        else if (sectionName === 'messages') loadInbox();
    };

    // ==================== 私信系统逻辑 ====================
    let currentChatUserId = null;

    function loadInbox() {
        const container = document.getElementById('inbox-container');
        if (!container) return;

        container.innerHTML = '<div class="text-center py-8 text-base-content/70"><span class="loading loading-spinner loading-md"></span><p class="mt-2">加载私信...</p></div>';

        fetch('/api/messages/conversations')
            .then(res => res.json())
            .then(data => {
                if (data.success && data.conversations && data.conversations.length > 0) {
                    container.innerHTML = data.conversations.map(conv => `
                        <div class="flex items-start gap-4 p-4 bg-base-200 rounded-lg hover-lift cursor-pointer" onclick="openMessage(${conv.otherUserId}, '${conv.otherUserName}')">
                            <div class="avatar placeholder">
                                <div class="w-12 h-12 rounded-full bg-neutral-focus text-neutral-content">
                                    <span class="text-xl">${(conv.otherUserName || '?').substring(0, 1)}</span>
                                </div>
                            </div>
                            <div class="flex-1">
                                <div class="flex justify-between items-start mb-1">
                                    <h4 class="font-semibold">${conv.otherUserName}</h4>
                                    <div class="flex items-center gap-2">
                                        ${conv.unreadCount > 0 ? `<span class="badge badge-error badge-sm">${conv.unreadCount}</span>` : ''}
                                        <span class="text-xs text-base-content/60">${new Date(conv.lastMessageTime).toLocaleString()}</span>
                                    </div>
                                </div>
                                <p class="text-sm text-base-content/70 line-clamp-1 mb-2">${conv.lastMessage || ''}</p>
                                <div class="flex gap-2">
                                    <button class="btn btn-ghost btn-xs" onclick="event.stopPropagation(); replyMessage(${conv.otherUserId}, '${conv.otherUserName}')">
                                        <span class="iconify" data-icon="heroicons:arrow-uturn-left" data-width="14"></span>回复
                                    </button>
                                    <button class="btn btn-ghost btn-xs text-error" onclick="event.stopPropagation(); deleteConversationMessage(${conv.lastMessageId})">
                                        <span class="iconify" data-icon="heroicons:trash" data-width="14"></span>删除
                                    </button>
                                </div>
                            </div>
                        </div>
                    `).join('');
                } else {
                    container.innerHTML = '<div class="text-center py-8 text-base-content/70">暂无私信</div>';
                }
            })
            .catch(() => container.innerHTML = '<div class="text-center py-8 text-error">加载失败</div>');
    }

    window.openMessage = function (userId, userName) {
        currentChatUserId = userId;
        document.getElementById('chatTargetName').textContent = userName || '聊天';
        const modal = document.getElementById('chatModal');
        const msgContainer = document.getElementById('chatMessages');
        msgContainer.innerHTML = '<div class="text-center py-4"><span class="loading loading-spinner"></span></div>';

        modal.showModal();

        fetch(`/api/messages/history?otherUserId=${userId}`)
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    const messages = data.messages || [];
                    renderChatMessages(messages, userId);
                    scrollToBottom();
                    // 标记已读后刷新列表（延迟一下以免视觉跳变）
                    setTimeout(loadInbox, 2000);
                }
            });
    };

    function renderChatMessages(messages, otherUserId) {
        const container = document.getElementById('chatMessages');
        if (messages.length === 0) {
            container.innerHTML = '<div class="text-center text-sm text-base-content/50 py-4">暂无历史消息</div>';
            return;
        }

        // 倒序排列，旧的消息在前
        const sortedMsgs = [...messages].sort((a, b) => new Date(a.sendTime) - new Date(b.sendTime));

        container.innerHTML = sortedMsgs.map(msg => {
            const isMe = msg.senderId !== otherUserId; // 假设如果不等于对方ID，就是我发的（更严谨是判断 senderId === currentUserId）
            // 注意：前端没有直接存储 currentUserId，这里简化判断：如果 senderId == otherUserId 则是对方发的
            const isOther = msg.senderId === otherUserId;

            return `
                <div class="chat ${isOther ? 'chat-start' : 'chat-end'}">
                    <div class="chat-header text-xs opacity-50 mb-1">
                        ${new Date(msg.sendTime).toLocaleTimeString()}
                    </div>
                    <div class="chat-bubble ${isOther ? 'chat-bubble-secondary' : 'chat-bubble-primary'}">
                        ${msg.messageText}
                    </div>
                </div>
            `;
        }).join('');
    }

    window.sendChatMessage = function () {
        const input = document.getElementById('chatInput');
        const content = input.value.trim();
        if (!content || !currentChatUserId) return;

        fetch('/api/messages', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ receiverId: currentChatUserId, content: content })
        })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    input.value = '';
                    // Append message locally
                    const container = document.getElementById('chatMessages');
                    const div = document.createElement('div');
                    div.innerHTML = `
                <div class="chat chat-end">
                    <div class="chat-header text-xs opacity-50 mb-1">刚刚</div>
                    <div class="chat-bubble chat-bubble-primary">${content}</div>
                </div>`;
                    container.appendChild(div.firstElementChild);
                    scrollToBottom();
                } else {
                    showNotification('发送失败', 'error');
                }
            });
    };

    window.replyMessage = function (userId, userName) {
        openMessage(userId, userName);
    };

    window.deleteConversationMessage = function (msgId) {
        // 暂未实现删除具体某条，这里只是占位，实际应该调用API
        // 由于 MVP 设计，ConversationDto 没有传 messageId, 需要后端补充
        showNotification('暂不支持从列表直接删除', 'info');
    };

    function scrollToBottom() {
        const container = document.getElementById('chatMessages');
        container.scrollTop = container.scrollHeight;
    }

    function loadUserProfile() {
        // Check for 'id' parameter to view other user's profile
        const urlParams = new URLSearchParams(window.location.search);
        const targetUserId = urlParams.get('id');

        let api = '/user/api/profile?t=' + Date.now();
        if (targetUserId) {
            api = '/user/api/users/' + targetUserId;
        }

        fetch(api, { credentials: 'include', cache: 'no-store' })
            .then(res => {
                if (res.status === 401) {
                    window.location.href = '/user/login_register.html';
                    return null;
                }
                if (!res.ok) throw new Error('Failed to load profile');
                return res.json();
            })
            .then(data => {
                if (!data) return;
                const user = data.user || data; // /api/users/{id} returns direct object, /api/profile returns {user: ...}
                if (user && (user.userName || user.email)) {
                    populateUserData(user);
                    if (targetUserId) {
                        setReadOnlyMode(user);
                    }
                }
            })
            .catch(err => console.error('Failed to load profile:', err));
    }
    window.loadUserProfile = loadUserProfile;

    function setReadOnlyMode(user) {
        // Hide private menu items
        const privateMenus = ['nav-settings', 'nav-messages', 'nav-favorites', 'nav-search-history', 'nav-browse-history'];
        privateMenus.forEach(id => {
            const el = document.getElementById(id);
            if (el) el.parentElement.style.display = 'none';
        });

        // Update Header
        const headerTitle = document.querySelector('.navbar-center .text-xl');
        if (headerTitle) headerTitle.textContent = user.userName + ' 的个人主页';

        // Disable Form Inputs
        const formInputs = document.querySelectorAll('#profile-form input, #profile-form textarea, #profile-form select');
        formInputs.forEach(input => input.disabled = true);

        // Hide Save Button
        const saveBtn = document.querySelector('#profile-form button[type="submit"]');
        if (saveBtn) saveBtn.style.display = 'none';

        // Hide Avatar Upload
        const avatarUpload = document.querySelector('.group-hover\\:opacity-100');
        if (avatarUpload) avatarUpload.parentElement.style.pointerEvents = 'none';

        // Hide explicit avatar upload button
        const avatarBtn = document.querySelector('button[onclick*="avatar-input"]');
        if (avatarBtn) avatarBtn.style.display = 'none';

        // Hide self-only buttons in sidebar
        const logoutBtn = document.querySelector('a[href="/user/api/logout"]');
        if (logoutBtn) logoutBtn.style.display = 'none';

        // Show Message Button if not already there
        const sidebar = document.querySelector('.bg-base-100 .p-6');
        if (sidebar && !document.getElementById('profileMsgBtn') && user.userId) {
            const btn = document.createElement('button');
            btn.id = 'profileMsgBtn';
            btn.className = 'btn btn-primary w-full mt-4';
            btn.innerHTML = '<span class="iconify" data-icon="heroicons:paper-airplane" data-width="16"></span> 发送私信';
            btn.onclick = function () {
                if (window.openMessage) window.openMessage(user.userId, user.userName);
            };
            sidebar.appendChild(btn);
        }

        // Show Message Button if not already there (Optional, could be added to sidebar)
    }

    function populateUserData(user) {
        const el = (id) => document.getElementById(id);
        if (el('sidebar-username')) el('sidebar-username').textContent = user.userName || '用户';
        if (el('sidebar-email')) el('sidebar-email').textContent = user.email || '';

        if (user.avatar && el('sidebar-avatar-img')) {
            el('sidebar-avatar-text')?.classList.add('hidden');
            el('sidebar-avatar-img').src = user.avatar;
            el('sidebar-avatar-img').classList.remove('hidden');
        } else if (el('sidebar-avatar-text')) {
            el('sidebar-avatar-text').textContent = (user.userName || '用').substring(0, 1);
        }

        const fields = { 'form-username': user.userName, 'form-email': user.email, 'form-phone': user.phone, 'form-gender': user.gender, 'form-birthday': user.birthday, 'form-institution': user.institution, 'form-bio': user.bio };
        Object.entries(fields).forEach(([id, val]) => { if (el(id)) el(id).value = val || ''; });

        if (user.avatar && el('form-avatar-img')) {
            el('form-avatar-text')?.classList.add('hidden');
            el('form-avatar-img').src = user.avatar;
            el('form-avatar-img').classList.remove('hidden');
        } else if (el('form-avatar-text')) {
            el('form-avatar-text').textContent = (user.userName || '用').substring(0, 1);
        }
    }

    window.handleAvatarUpload = function (input) {
        // Check read-only
        const urlParams = new URLSearchParams(window.location.search);
        if (urlParams.get('id')) {
            input.value = ''; // Reset input
            return;
        }

        if (input.files && input.files[0]) {
            const formData = new FormData();
            formData.append('avatar', input.files[0]);
            fetch('/user/api/upload-avatar', { method: 'POST', body: formData, credentials: 'include' })
                .then(res => res.json())
                .then(data => {
                    if (data.success) { showNotification('头像上传成功', 'success'); loadUserProfile(); }
                    else showNotification(data.message || '上传失败', 'error');
                })
                .catch(() => showNotification('上传失败', 'error'));
        }
    };

    // ==================== 搜索历史 ====================

    window.switchSearchHistoryTab = function (type) {
        // ... (existing code)
        currentSearchType = type;
        const graphTab = document.getElementById('tab-search-graph');
        const forumTab = document.getElementById('tab-search-forum');
        const graphContainer = document.getElementById('search-history-graph-container');
        const forumContainer = document.getElementById('search-history-forum-container');

        if (type === 'graph') {
            graphTab?.classList.add('tab-active');
            forumTab?.classList.remove('tab-active');
            graphContainer?.classList.remove('hidden');
            forumContainer?.classList.add('hidden');
        } else {
            forumTab?.classList.add('tab-active');
            graphTab?.classList.remove('tab-active');
            forumContainer?.classList.remove('hidden');
            graphContainer?.classList.add('hidden');
        }

        loadSearchHistory(type);
    };

    function loadSearchHistory(type) {
        // ... (existing code)
        const containerId = type === 'graph' ? 'search-history-graph-container' : 'search-history-forum-container';
        const container = document.getElementById(containerId);
        if (!container) return;

        container.innerHTML = '<div class="text-center py-8 text-base-content/70"><span class="loading loading-spinner loading-md"></span><p class="mt-2">加载中...</p></div>';

        fetch(`/api/history/search?type=${type}`)
            .then(res => res.json())
            .then(data => {
                const history = data.content || [];
                if (history.length > 0) {
                    container.innerHTML = history.map(item => `
                        <div class="flex items-center justify-between p-4 bg-base-200 rounded-lg hover-lift">
                            <div class="flex items-center gap-3">
                                <span class="iconify text-base-content/70" data-icon="heroicons:magnifying-glass" data-width="20"></span>
                                <div>
                                    <p class="font-medium">${item.queryText}</p>
                                    <p class="text-sm text-base-content/70">${item.searchTime ? new Date(item.searchTime).toLocaleString() : ''}</p>
                                </div>
                            </div>
                            <div class="flex gap-2">
                                <button class="btn btn-ghost btn-sm" onclick="searchAgain('${item.queryText}', '${type}')">
                                    <span class="iconify" data-icon="heroicons:arrow-path" data-width="16"></span>再次搜索
                                </button>
                                <button class="btn btn-ghost btn-sm text-error" onclick="deleteSearchItem('${item.queryText}', '${type}')">
                                    <span class="iconify" data-icon="heroicons:x-mark" data-width="16"></span>
                                </button>
                            </div>
                        </div>
                    `).join('');
                } else {
                    container.innerHTML = '<div class="text-center py-8 text-base-content/70">暂无搜索历史</div>';
                }
            })
            .catch(err => {
                console.error(err);
                container.innerHTML = '<div class="text-center py-8 text-error">加载失败</div>';
            });
    }

    window.clearAllSearchHistory = function () {
        showConfirmDialog(`确定清空${currentSearchType === 'graph' ? '图谱' : '论坛'}搜索历史？`, () => {
            fetch(`/api/history/search?type=${currentSearchType}`, { method: 'DELETE' })
                .then(res => res.json())
                .then(data => {
                    if (data.success) {
                        showNotification('已清空', 'success');
                        loadSearchHistory(currentSearchType);
                    } else {
                        showNotification('清空失败', 'error');
                    }
                });
        });
    };

    window.deleteSearchItem = function (keyword, type) {
        showConfirmDialog('确定删除该条记录？', () => {
            fetch(`/api/history/search/item?type=${type}&keyword=${encodeURIComponent(keyword)}`, { method: 'DELETE' })
                .then(res => res.json())
                .then(data => {
                    if (data.success) {
                        showNotification('已删除', 'success');
                        loadSearchHistory(type);
                    }
                });
        });
    };

    window.searchAgain = function (query, type) {
        if (type === 'graph') {
            window.location.href = '/graph/graph_list.html?q=' + encodeURIComponent(query);
        } else {
            window.location.href = '/community/forum_list.html?q=' + encodeURIComponent(query);
        }
    };

    // ==================== 浏览历史 ====================

    window.switchBrowseHistoryTab = function (type) {
        // ... (existing code)
        currentBrowseType = type;
        const graphTab = document.getElementById('tab-browse-graph');
        const postTab = document.getElementById('tab-browse-post');
        const graphContainer = document.getElementById('browse-history-graph-container');
        const postContainer = document.getElementById('browse-history-post-container');

        if (type === 'graph') {
            graphTab?.classList.add('tab-active');
            postTab?.classList.remove('tab-active');
            graphContainer?.classList.remove('hidden');
            postContainer?.classList.add('hidden');
        } else {
            postTab?.classList.add('tab-active');
            graphTab?.classList.remove('tab-active');
            postContainer?.classList.remove('hidden');
            graphContainer?.classList.add('hidden');
        }

        loadBrowseHistory(type);
    };

    function loadBrowseHistory(type) {
        // ... (existing code, unchanged logic but included for safety)
        const containerId = type === 'graph' ? 'browse-history-graph-container' : 'browse-history-post-container';
        const container = document.getElementById(containerId);
        if (!container) return;

        container.innerHTML = '<div class="text-center py-8 text-base-content/70"><span class="loading loading-spinner loading-md"></span><p class="mt-2">加载中...</p></div>';

        fetch(`/api/history/browsing?type=${type}`)
            .then(res => res.json())
            .then(data => {
                const history = data.content || [];
                if (history.length > 0) {
                    container.innerHTML = history.map(item => {
                        const resourceId = item.resourceId;
                        const resourceName = item.resourceName || (type === 'graph' ? '未知图谱' : '未知帖子');
                        const viewUrl = type === 'graph' ? `/graph/graph_detail.html?id=${resourceId}` : `/community/post_detail.html?id=${resourceId}`;

                        return `
                        <div data-repeatable="true" class="flex items-center justify-between p-4 bg-base-200 rounded-lg hover-lift">
                            <div class="flex items-center gap-3">
                                <span class="iconify text-base-content/70" data-icon="${type === 'graph' ? 'heroicons:squares-2x2' : 'heroicons:document-text'}" data-width="20"></span>
                                <div>
                                    <p class="font-medium">${resourceName}</p>
                                    <p class="text-sm text-base-content/70">${item.viewTime ? new Date(item.viewTime).toLocaleString() : ''}</p>
                                </div>
                            </div>
                            <div class="flex gap-2">
                                <a href="${viewUrl}" class="btn btn-ghost btn-sm">
                                    <span class="iconify" data-icon="heroicons:eye" data-width="16"></span>查看详情
                                </a>
                                <button class="btn btn-ghost btn-sm text-error" onclick="deleteHistoryItem('${type}', ${item.id})">
                                    <span class="iconify" data-icon="heroicons:x-mark" data-width="16"></span>
                                </button>
                            </div>
                        </div>`;
                    }).join('');
                } else {
                    container.innerHTML = '<div class="text-center py-8 text-base-content/70">暂无浏览历史</div>';
                }
            })
            .catch(err => {
                console.error(err);
                container.innerHTML = '<div class="text-center py-8 text-error">加载失败</div>';
            });
    }

    // 新增：删除单条浏览历史
    window.deleteHistoryItem = function (type, id) {
        showConfirmDialog('确定删除该条记录？', () => {
            fetch(`/api/history/browsing/${id}`, { method: 'DELETE' })
                .then(res => res.json())
                .then(data => {
                    if (data.success) {
                        showNotification('已删除', 'success');
                        loadBrowseHistory(type);
                    } else {
                        showNotification('删除失败', 'error');
                    }
                })
                .catch(() => showNotification('删除失败', 'error'));
        });
    };

    window.clearAllBrowseHistory = function () {
        showConfirmDialog(`确定清空${currentBrowseType === 'graph' ? '图谱' : '帖子'}浏览历史？`, () => {
            fetch(`/api/history/browsing?type=${currentBrowseType}`, { method: 'DELETE' })
                .then(res => res.json())
                .then(data => {
                    if (data.success) {
                        showNotification('已清空', 'success');
                        loadBrowseHistory(currentBrowseType);
                    } else {
                        showNotification('清空失败', 'error');
                    }
                });
        });
    };

    // ... (favorites logic unchanged) ...

    window.switchFavoriteTab = function (tabType) {
        // (unchanged)
        const postsTab = document.getElementById('tab-posts'), graphsTab = document.getElementById('tab-graphs');
        const postsC = document.getElementById('post-favorites-container'), graphsC = document.getElementById('graph-favorites-container');
        if (tabType === 'posts') { postsTab?.classList.add('tab-active'); graphsTab?.classList.remove('tab-active'); postsC?.classList.remove('hidden'); graphsC?.classList.add('hidden'); }
        else { graphsTab?.classList.add('tab-active'); postsTab?.classList.remove('tab-active'); graphsC?.classList.remove('hidden'); postsC?.classList.add('hidden'); }
    };

    // Replace with real API calls
    window.unfavoritePost = function (postId) {
        showConfirmDialog('确定要取消收藏吗？', () => {
            fetch(`/api/posts/${postId}/favorite`, { method: 'POST' })
                .then(() => {
                    showNotification('已取消收藏', 'success');
                    loadPostFavorites();
                });
        });
    };

    window.unfavoriteGraph = function (graphId) {
        showConfirmDialog('确定要取消收藏吗？', () => {
            fetch(`/api/graph/${graphId}/favorite`, { method: 'POST' })
                .then(() => {
                    showNotification('已取消收藏', 'success');
                    loadGraphFavorites();
                });
        });
    };

    window.changePassword = function () {
        const cur = document.querySelector('input[placeholder="请输入当前密码"]'), np = document.querySelector('input[placeholder="请输入新密码"]'), cp = document.querySelector('input[placeholder="请再次输入新密码"]');
        if (!cur?.value || !np?.value || !cp?.value) { showNotification('请填写完整', 'error'); return; }
        if (np.value !== cp.value) { showNotification('两次密码不一致', 'error'); return; }
        if (np.value.length < 8) { showNotification('密码至少8位', 'error'); return; }
        showConfirmDialog('确定修改密码？', () => { showNotification('密码修改成功', 'success'); cur.value = ''; np.value = ''; cp.value = ''; });
    };

    window.confirmAccountDeletion = function () { showConfirmDialog('警告：账户注销后无法恢复。确定继续？', () => showNotification('注销申请已提交', 'warning')); };
    // Stub functions for openMessage etc removed to avoid overriding real logic

    window.showConfirmDialog = function (msg, action) { const m = document.getElementById('confirmModal'), e = document.getElementById('confirmMessage'); if (e) e.textContent = msg; confirmAction = action; m?.showModal(); };
    window.closeConfirmModal = function () { document.getElementById('confirmModal')?.close(); confirmAction = null; };
    window.executeConfirmedAction = function () { if (confirmAction) confirmAction(); closeConfirmModal(); };

    function initializeFormSubmit() {
        const form = document.getElementById('profile-form');
        form?.addEventListener('submit', function (e) {
            e.preventDefault();
            const data = { userName: document.getElementById('form-username')?.value, email: document.getElementById('form-email')?.value, phone: document.getElementById('form-phone')?.value, gender: document.getElementById('form-gender')?.value, birthday: document.getElementById('form-birthday')?.value, institution: document.getElementById('form-institution')?.value, bio: document.getElementById('form-bio')?.value };
            fetch('/user/api/profile', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data), credentials: 'include' })
                .then(res => res.json())
                .then(d => { if (d.success) { showNotification('保存成功', 'success'); loadUserProfile(); } else showNotification(d.message || '保存失败', 'error'); })
                .catch(() => showNotification('保存失败', 'error'));
        });
    }

    function showNotification(msg, type) { if (window.showNotification) window.showNotification(msg, type); }
})();
