/**
 * Login/Register Page JavaScript
 * 登录注册页面 JavaScript 模块
 */

(function() {
    'use strict';

    document.addEventListener('DOMContentLoaded', function() {
        initializeFormValidation();
    });

    // Tab switching
    window.switchTab = function(tabType) {
        const loginTab = document.getElementById('loginTab');
        const registerTab = document.getElementById('registerTab');
        const loginForm = document.getElementById('loginForm');
        const registerForm = document.getElementById('registerForm');
        
        if (tabType === 'login') {
            loginTab.classList.add('tab-active');
            registerTab.classList.remove('tab-active');
            loginForm.classList.remove('hidden');
            registerForm.classList.add('hidden');
        } else {
            registerTab.classList.add('tab-active');
            loginTab.classList.remove('tab-active');
            registerForm.classList.remove('hidden');
            loginForm.classList.add('hidden');
        }
    };

    // Login handler
    window.handleLogin = function(event) {
        event.preventDefault();
        
        const account = document.getElementById('login-account').value.trim();
        const password = document.getElementById('login-password').value;
        
        if (!account || !password) {
            showNotification('请输入邮箱/手机号和密码', 'error');
            return;
        }
        
        showNotification('正在登录...', 'info');
        
        fetch('/api/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                username: account,
                password: password
            }),
            redirect: 'manual'
        })
        .then(response => {
            if (response.ok || response.type === 'opaqueredirect' || response.status === 302 || response.redirected) {
                showNotification('登录成功！', 'success');
                setTimeout(() => {
                    window.location.href = '/graph/home.html';
                }, 500);
            } else if (response.status === 401) {
                return response.json().then(data => {
                    showNotification(data.error || '邮箱/手机号或密码错误', 'error');
                });
            } else {
                showNotification('登录失败，请稍后重试', 'error');
            }
        })
        .catch(error => {
            console.error('登录错误:', error);
            showNotification('登录失败，请检查网络', 'error');
        });
    };

    // Register handler
    window.handleRegister = function(event) {
        event.preventDefault();
        
        const username = document.getElementById('reg-username').value.trim();
        const email = document.getElementById('reg-email').value.trim();
        const phone = document.getElementById('reg-phone').value.trim();
        const password = document.getElementById('reg-password').value;
        const confirmPassword = document.getElementById('reg-confirm-password').value;
        
        if (!username) {
            showNotification('请输入用户名', 'error');
            return;
        }
        
        if (!email && !phone) {
            showNotification('邮箱和手机号至少填一个', 'error');
            return;
        }
        
        if (password !== confirmPassword) {
            showNotification('两次输入的密码不一致', 'error');
            return;
        }
        
        if (password.length < 8) {
            showNotification('密码长度至少8位', 'error');
            return;
        }
        
        showNotification('正在注册...', 'info');
        
        fetch('/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                userName: username,
                email: email,
                phone: phone,
                password: password
            })
        })
        .then(response => {
            if (response.ok || response.redirected) {
                showNotification('注册成功！', 'success');
                setTimeout(() => {
                    window.location.href = '/graph/home.html';
                }, 500);
            } else {
                return response.text().then(text => {
                    throw new Error(text || '注册失败');
                });
            }
        })
        .catch(error => {
            console.error('注册错误:', error);
            showNotification(error.message || '注册失败，请稍后重试', 'error');
        });
    };

    // Social login handler
    window.handleSocialLogin = function(provider) {
        showNotification(`正在跳转到${provider === 'wechat' ? '微信' : 'GitHub'}登录...`, 'info');
        
        setTimeout(() => {
            if (window.APP_GLOBALS && window.APP_GLOBALS.user) {
                window.APP_GLOBALS.user.login({
                    email: `user@${provider}.com`,
                    name: `${provider}用户`,
                    provider: provider,
                    loginTime: new Date().toISOString()
                });
            }
            
            showNotification('登录成功！', 'success');
            setTimeout(() => {
                window.location.href = '/graph/home.html';
            }, 1000);
        }, 2000);
    };

    // Verification code sender
    window.sendVerificationCode = function() {
        const btn = document.getElementById('verifyBtn');
        const phoneInput = document.querySelector('input[type="tel"]');
        
        if (!phoneInput || !phoneInput.value) {
            showNotification('请先输入手机号码', 'warning');
            return;
        }
        
        const phoneRegex = /^1[3-9]\d{9}$/;
        if (!phoneRegex.test(phoneInput.value)) {
            showNotification('请输入正确的手机号码', 'error');
            return;
        }
        
        let countdown = 60;
        btn.textContent = `${countdown}秒后重发`;
        btn.disabled = true;
        btn.classList.add('btn-countdown');
        
        const timer = setInterval(() => {
            countdown--;
            btn.textContent = `${countdown}秒后重发`;
            
            if (countdown <= 0) {
                clearInterval(timer);
                btn.textContent = '发送验证码';
                btn.disabled = false;
                btn.classList.remove('btn-countdown');
            }
        }, 1000);
        
        showNotification('验证码已发送', 'success');
    };

    // Form validation
    function initializeFormValidation() {
        const inputs = document.querySelectorAll('input[required]');
        inputs.forEach(input => {
            input.addEventListener('blur', function() {
                if (this.value.trim() === '') {
                    this.classList.add('error');
                    this.classList.remove('success');
                } else {
                    this.classList.remove('error');
                    this.classList.add('success');
                }
            });
            
            input.addEventListener('input', function() {
                if (this.classList.contains('error') && this.value.trim() !== '') {
                    this.classList.remove('error');
                    this.classList.add('success');
                }
            });
        });
        
        const emailInputs = document.querySelectorAll('input[type="email"]');
        emailInputs.forEach(input => {
            input.addEventListener('blur', function() {
                const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                if (this.value && !emailRegex.test(this.value)) {
                    this.classList.add('error');
                    this.classList.remove('success');
                }
            });
        });
    }

    // Helper function
    function showNotification(message, type) {
        if (window.showNotification) {
            window.showNotification(message, type);
        } else {
            console.log(`[${type}] ${message}`);
        }
    }

})();
