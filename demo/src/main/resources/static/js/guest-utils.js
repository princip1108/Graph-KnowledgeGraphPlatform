/**
 * 游客工具函数 - 统一处理未登录用户的写操作拦截
 */
(function () {
    'use strict';

    // 全局登录状态
    window._isLoggedIn = false;
    window._currentUser = null;

    /**
     * 初始化登录状态检查（各页面在 DOMContentLoaded 时调用）
     */
    window.initGuestCheck = async function () {
        try {
            var res = await fetch('/user/api/check-auth', { credentials: 'include' });
            var data = await res.json();
            if (data.authenticated && data.user) {
                window._isLoggedIn = true;
                window._currentUser = data.user;
            }
        } catch (e) { /* ignore */ }
    };

    /**
     * 检查是否已登录，未登录则弹出提示。返回 true 表示已登录，false 表示未登录。
     */
    window.requireLogin = function (action) {
        if (window._isLoggedIn) return true;
        showLoginModal(action);
        return false;
    };

    /**
     * 检查 fetch 响应是否为 401，是则弹出登录提示。返回 true 表示需要登录（调用方应中止）。
     */
    window.checkNeedLogin = function (response) {
        if (response.status === 401) {
            showLoginModal();
            return true;
        }
        return false;
    };

    /**
     * 显示登录提示 Modal
     */
    function showLoginModal(action) {
        var modal = document.getElementById('guestLoginModal');
        if (modal) {
            var msgEl = document.getElementById('guestLoginMessage');
            if (msgEl && action) {
                msgEl.textContent = '请先登录后再' + action;
            } else if (msgEl) {
                msgEl.textContent = '请先登录后再进行此操作';
            }
            modal.showModal();
        } else {
            if (confirm('请先登录后再操作，是否前往登录？')) {
                window.location.href = '/user/login_register.html?redirect=' + encodeURIComponent(window.location.href);
            }
        }
    }

    /**
     * 前往登录页（Modal 中的按钮调用）
     */
    window.goToLogin = function () {
        window.location.href = '/user/login_register.html?redirect=' + encodeURIComponent(window.location.href);
    };

    /**
     * 关闭登录提示 Modal
     */
    window.closeLoginModal = function () {
        var modal = document.getElementById('guestLoginModal');
        if (modal) modal.close();
    };

    /**
     * 注入登录提示 Modal 到页面（如果页面没有手动添加）
     */
    window.injectLoginModal = function () {
        if (document.getElementById('guestLoginModal')) return;
        var div = document.createElement('div');
        div.innerHTML =
            '<dialog id="guestLoginModal" class="modal">' +
            '<div class="modal-box max-w-sm text-center">' +
            '<div class="text-5xl mb-4">🔒</div>' +
            '<h3 class="font-bold text-lg mb-2">需要登录</h3>' +
            '<p id="guestLoginMessage" class="text-base-content/70 mb-6">请先登录后再进行此操作</p>' +
            '<div class="flex gap-3 justify-center">' +
            '<button class="btn btn-ghost" onclick="closeLoginModal()">取消</button>' +
            '<button class="btn btn-primary" onclick="goToLogin()">前往登录</button>' +
            '</div>' +
            '</div>' +
            '<form method="dialog" class="modal-backdrop"><button>close</button></form>' +
            '</dialog>';
        document.body.appendChild(div.firstChild);
    };

    // 页面加载后自动注入 Modal
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { window.injectLoginModal(); });
    } else {
        window.injectLoginModal();
    }
})();
