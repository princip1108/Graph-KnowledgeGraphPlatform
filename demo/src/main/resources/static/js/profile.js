/**
 * Profile Page JavaScript
 * 个人中心页面 JavaScript 模块
 */

(function() {
    'use strict';

    let currentSection = 'profile';
    let confirmAction = null;

    document.addEventListener('DOMContentLoaded', function() {
        const urlParams = new URLSearchParams(window.location.search);
        const section = urlParams.get('section');
        if (section && document.getElementById(section + '-section')) {
            showSection(section);
        } else {
            showSection('profile');
        }
        
        loadUserProfile();
        initializeFormSubmit();
    });

    window.showSection = function(sectionName) {
        document.querySelectorAll('.content-section').forEach(s => s.classList.add('hidden'));
        document.querySelectorAll('.menu li a').forEach(item => item.classList.remove('active'));
        
        const targetSection = document.getElementById(sectionName + '-section');
        if (targetSection) targetSection.classList.remove('hidden');
        
        const activeNav = document.getElementById('nav-' + sectionName);
        if (activeNav) activeNav.classList.add('active');
        
        currentSection = sectionName;
        if (sectionName === 'favorites') loadFavorites();
    };

    function loadUserProfile() {
        fetch('/user/api/profile?t=' + Date.now(), { credentials: 'include', cache: 'no-store' })
            .then(res => {
                if (res.status === 401) {
                    window.location.href = '/user/login_register.html';
                    return null;
                }
                return res.json();
            })
            .then(data => {
                if (!data) return;
                // 支持两种响应格式: { success, user } 或直接返回 user 对象
                const user = data.user || data;
                if (user && (user.userName || user.email)) {
                    populateUserData(user);
                }
            })
            .catch(err => console.error('Failed to load profile:', err));
    }
    window.loadUserProfile = loadUserProfile;

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
        Object.entries(fields).forEach(([id, val]) => { if (el(id) && val) el(id).value = val; });
        
        if (user.avatar && el('form-avatar-img')) {
            el('form-avatar-text')?.classList.add('hidden');
            el('form-avatar-img').src = user.avatar;
            el('form-avatar-img').classList.remove('hidden');
        } else if (el('form-avatar-text')) {
            el('form-avatar-text').textContent = (user.userName || '用').substring(0, 1);
        }
    }

    window.handleAvatarUpload = function(input) {
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

    function loadFavorites() {
        loadPostFavorites();
        loadGraphFavorites();
    }

    function loadPostFavorites() {
        const container = document.getElementById('post-favorites-container');
        if (!container) return;
        // 使用 localStorage 存储帖子收藏
        const favorites = JSON.parse(localStorage.getItem('post_favorites') || '[]');
        if (favorites.length > 0) {
            container.innerHTML = favorites.map(p => `<div class="flex items-center justify-between p-4 bg-base-200 rounded-lg hover-lift"><div class="flex items-center gap-3"><span class="iconify" data-icon="heroicons:document-text" data-width="20"></span><div><p class="font-medium">${p.title || '帖子'}</p><p class="text-sm text-base-content/70">${p.createdAt || ''}</p></div></div><div class="flex gap-2"><a href="/community/post_detail.html?id=${p.id}" class="btn btn-ghost btn-sm">查看</a><button class="btn btn-ghost btn-sm text-error" onclick="unfavoritePost('${p.id}')">取消</button></div></div>`).join('');
        } else {
            container.innerHTML = '<div class="text-center py-8 text-base-content/70">暂无收藏的帖子</div>';
        }
    }

    function loadGraphFavorites() {
        const container = document.getElementById('graph-favorites-container');
        if (!container) return;
        // 统一使用 favorites key
        let favorites = JSON.parse(localStorage.getItem('favorites') || '[]');
        if (favorites.length > 0) {
            container.innerHTML = favorites.map(item => {
                const name = item.name || (item.data && item.data.name) || '图谱';
                const graphId = item.id || item.graphId;
                return `<div class="flex items-center justify-between p-4 bg-base-200 rounded-lg hover-lift">
                    <div class="flex items-center gap-3">
                        <span class="iconify text-primary" data-icon="heroicons:squares-2x2" data-width="20"></span>
                        <div>
                            <p class="font-medium">${name}</p>
                            <p class="text-sm text-base-content/60">${item.addedAt ? new Date(item.addedAt).toLocaleDateString() : ''}</p>
                        </div>
                    </div>
                    <div class="flex gap-2">
                        <a href="/graph/graph_detail.html?id=${graphId}" class="btn btn-ghost btn-sm">
                            <span class="iconify" data-icon="heroicons:eye" data-width="16"></span>查看
                        </a>
                        <button class="btn btn-ghost btn-sm text-error" onclick="unfavoriteGraph('${graphId}')">
                            <span class="iconify" data-icon="heroicons:heart" data-width="16"></span>取消
                        </button>
                    </div>
                </div>`;
            }).join('');
        } else {
            container.innerHTML = '<div class="text-center py-8 text-base-content/70">暂无收藏的图谱</div>';
        }
    }

    window.switchFavoriteTab = function(tabType) {
        const postsTab = document.getElementById('tab-posts'), graphsTab = document.getElementById('tab-graphs');
        const postsC = document.getElementById('post-favorites-container'), graphsC = document.getElementById('graph-favorites-container');
        if (tabType === 'posts') { postsTab?.classList.add('tab-active'); graphsTab?.classList.remove('tab-active'); postsC?.classList.remove('hidden'); graphsC?.classList.add('hidden'); }
        else { graphsTab?.classList.add('tab-active'); postsTab?.classList.remove('tab-active'); graphsC?.classList.remove('hidden'); postsC?.classList.add('hidden'); }
    };

    window.unfavoritePost = function(postId) { showConfirmDialog('确定要取消收藏吗？', () => { let favorites = JSON.parse(localStorage.getItem('post_favorites') || '[]'); favorites = favorites.filter(p => p.id !== postId); localStorage.setItem('post_favorites', JSON.stringify(favorites)); showNotification('已取消收藏', 'success'); loadPostFavorites(); }); };
    window.unfavoriteGraph = function(graphId) { showConfirmDialog('确定要取消收藏吗？', () => { let favorites = JSON.parse(localStorage.getItem('favorites') || '[]'); favorites = favorites.filter(item => String(item.id) !== String(graphId)); localStorage.setItem('favorites', JSON.stringify(favorites)); showNotification('已取消收藏', 'success'); loadGraphFavorites(); }); };

    window.searchAgain = function(query) { window.location.href = '/graph/graph_list.html?q=' + encodeURIComponent(query); };
    window.deleteSearchItem = function(btn) { showConfirmDialog('确定删除？', () => { btn.closest('[data-repeatable]')?.remove(); showNotification('已删除', 'success'); }); };
    window.clearAllSearchHistory = function() { showConfirmDialog('确定清空？', () => { localStorage.removeItem('searchHistory'); const c = document.querySelector('#search-history-section .space-y-4'); if(c) c.innerHTML = '<div class="text-center py-8 text-base-content/70">暂无搜索历史</div>'; showNotification('已清空', 'success'); }); };
    window.deleteBrowseItem = function(btn) { showConfirmDialog('确定删除？', () => { btn.closest('[data-repeatable]')?.remove(); showNotification('已删除', 'success'); }); };
    window.clearAllBrowseHistory = function() { showConfirmDialog('确定清空？', () => { const c = document.querySelector('#browse-history-section .space-y-4'); if(c) c.innerHTML = '<div class="text-center py-8 text-base-content/70">暂无浏览历史</div>'; showNotification('已清空', 'success'); }); };

    window.changePassword = function() {
        const cur = document.querySelector('input[placeholder="请输入当前密码"]'), np = document.querySelector('input[placeholder="请输入新密码"]'), cp = document.querySelector('input[placeholder="请再次输入新密码"]');
        if (!cur?.value || !np?.value || !cp?.value) { showNotification('请填写完整', 'error'); return; }
        if (np.value !== cp.value) { showNotification('两次密码不一致', 'error'); return; }
        if (np.value.length < 8) { showNotification('密码至少8位', 'error'); return; }
        showConfirmDialog('确定修改密码？', () => { showNotification('密码修改成功', 'success'); cur.value = ''; np.value = ''; cp.value = ''; });
    };

    window.confirmAccountDeletion = function() { showConfirmDialog('警告：账户注销后无法恢复。确定继续？', () => showNotification('注销申请已提交', 'warning')); };
    window.openMessage = function(id) { showNotification('正在打开私信...', 'info'); };
    window.replyMessage = function(id) { showNotification('正在准备回复...', 'info'); };
    window.deleteMessage = function(id) { showConfirmDialog('确定删除私信？', () => showNotification('已删除', 'success')); };

    window.showConfirmDialog = function(msg, action) { const m = document.getElementById('confirmModal'), e = document.getElementById('confirmMessage'); if(e) e.textContent = msg; confirmAction = action; m?.showModal(); };
    window.closeConfirmModal = function() { document.getElementById('confirmModal')?.close(); confirmAction = null; };
    window.executeConfirmedAction = function() { if(confirmAction) confirmAction(); closeConfirmModal(); };

    function initializeFormSubmit() {
        const form = document.getElementById('profile-form');
        form?.addEventListener('submit', function(e) {
            e.preventDefault();
            const data = { userName: document.getElementById('form-username')?.value, email: document.getElementById('form-email')?.value, phone: document.getElementById('form-phone')?.value, gender: document.getElementById('form-gender')?.value, birthday: document.getElementById('form-birthday')?.value, institution: document.getElementById('form-institution')?.value, bio: document.getElementById('form-bio')?.value };
            fetch('/user/api/profile', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data), credentials: 'include' })
                .then(res => res.json())
                .then(d => { if(d.success) { showNotification('保存成功', 'success'); loadUserProfile(); } else showNotification(d.message || '保存失败', 'error'); })
                .catch(() => showNotification('保存失败', 'error'));
        });
    }

    function showNotification(msg, type) { if(window.showNotification) window.showNotification(msg, type); }
})();
