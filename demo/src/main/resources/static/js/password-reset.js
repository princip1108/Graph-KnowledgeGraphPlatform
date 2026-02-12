/**
 * Password Reset Page JavaScript
 * 密码重置页面 JavaScript 模块
 */

(function() {
    'use strict';

    let currentStep = 1;
    let countdownTimer = null;
    let isCodeSent = false;

    document.addEventListener('DOMContentLoaded', function() {
        initializeFormValidation();
        initializeCodeInput();
    });

    function initializeFormValidation() {
        const inputs = document.querySelectorAll('input');
        inputs.forEach(input => {
            input.addEventListener('blur', function() {
                validateField(this.id);
            });
            
            input.addEventListener('input', function() {
                clearError(this.id);
                if (this.id === 'new-password') {
                    updatePasswordStrength(this.value);
                }
            });
        });
    }

    function initializeCodeInput() {
        const codeInput = document.getElementById('verification-code');
        if (codeInput) {
            codeInput.addEventListener('input', function() {
                this.value = this.value.replace(/\D/g, '').substring(0, 6);
            });
        }
    }

    // Send verification code
    window.sendVerificationCode = function() {
        const contactInput = document.getElementById('contact-input');
        const contact = contactInput.value.trim();
        
        if (!validateContactInput(contact)) {
            return;
        }
        
        const sendBtn = document.getElementById('send-code-btn');
        sendBtn.disabled = true;
        sendBtn.innerHTML = '<span class="loading loading-spinner loading-xs"></span> 发送中...';
        
        fetch('/api/auth/send-code', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: contact, purpose: 'reset' })
        })
        .then(response => response.json().then(data => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
            if (ok) {
                if (window.showNotification) window.showNotification('验证码已发送，请查收', 'success');
                startCountdown();
                isCodeSent = true;
            } else {
                if (window.showNotification) window.showNotification(data.error || '发送失败', 'error');
                sendBtn.disabled = false;
            }
            sendBtn.innerHTML = '获取验证码';
        })
        .catch(() => {
            if (window.showNotification) window.showNotification('网络错误，请稍后重试', 'error');
            sendBtn.disabled = false;
            sendBtn.innerHTML = '获取验证码';
        });
    };

    function startCountdown() {
        let seconds = 60;
        const timerElement = document.getElementById('resend-timer');
        const countdownElement = document.getElementById('countdown');
        const resendLink = document.getElementById('resend-link');
        const sendBtn = document.getElementById('send-code-btn');
        
        timerElement.classList.remove('hidden');
        resendLink.style.display = 'none';
        sendBtn.disabled = true;
        
        countdownTimer = setInterval(() => {
            seconds--;
            countdownElement.textContent = seconds;
            
            if (seconds <= 0) {
                clearInterval(countdownTimer);
                timerElement.classList.add('hidden');
                resendLink.style.display = 'inline';
                sendBtn.disabled = false;
            }
        }, 1000);
    }

    function validateContactInput(contact) {
        if (!contact) {
            showError('contact-input', '请输入邮箱或手机号');
            return false;
        }
        
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        const phoneRegex = /^1[3-9]\d{9}$/;
        
        if (!emailRegex.test(contact) && !phoneRegex.test(contact)) {
            showError('contact-input', '请输入有效的邮箱或手机号');
            return false;
        }
        
        clearError('contact-input');
        return true;
    }

    // Verify code
    window.verifyCode = function() {
        const code = document.getElementById('verification-code').value.trim();
        
        if (!code) {
            showError('verification-code', '请输入验证码');
            return;
        }
        
        if (code.length !== 6) {
            showError('verification-code', '验证码应为6位数字');
            return;
        }
        
        if (!isCodeSent) {
            showError('verification-code', '请先获取验证码');
            return;
        }
        
        const contact = document.getElementById('contact-input').value.trim();
        
        fetch('/api/auth/verify-reset-code', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: contact, code: code })
        })
        .then(response => response.json().then(data => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
            if (ok && data.success) {
                if (window.showNotification) window.showNotification('验证成功', 'success');
                nextStep();
            } else {
                showError('verification-code', data.error || '验证码错误');
            }
        })
        .catch(() => {
            showError('verification-code', '网络错误，请稍后重试');
        });
    };

    function nextStep() {
        currentStep++;
        updateStepDisplay();
        updateProgressTracker();
    }

    function updateStepDisplay() {
        document.getElementById('verification-step').classList.add('hidden');
        document.getElementById('password-step').classList.add('hidden');
        document.getElementById('success-step').classList.add('hidden');
        
        switch(currentStep) {
            case 1:
                document.getElementById('verification-step').classList.remove('hidden');
                break;
            case 2:
                document.getElementById('password-step').classList.remove('hidden');
                document.getElementById('password-step').classList.add('step-transition');
                break;
            case 3:
                document.getElementById('success-step').classList.remove('hidden');
                document.getElementById('success-step').classList.add('step-transition', 'success-bounce');
                document.getElementById('back-link').classList.add('hidden');
                break;
        }
    }

    function updateProgressTracker() {
        for (let i = 1; i <= 3; i++) {
            const step = document.getElementById(`step-${i}`);
            step.classList.remove('step-primary', 'step-success');
            
            if (i < currentStep) {
                step.classList.add('step-success');
            } else if (i === currentStep) {
                step.classList.add('step-primary');
            }
        }
    }

    function updatePasswordStrength(password) {
        const indicators = ['strength-1', 'strength-2', 'strength-3', 'strength-4'];
        const strengthText = document.getElementById('strength-text');
        
        indicators.forEach(id => {
            const element = document.getElementById(id);
            element.className = 'h-2 flex-1 bg-base-300 rounded';
        });
        
        if (!password) {
            strengthText.textContent = '请输入密码';
            return;
        }
        
        let strength = 0;
        
        if (password.length >= 8) strength++;
        if (/[a-z]/.test(password)) strength++;
        if (/[A-Z]/.test(password)) strength++;
        if (/\d/.test(password)) strength++;
        if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) strength++;
        
        let strengthLabel = '';
        
        if (strength <= 1) {
            document.getElementById('strength-1').classList.add('strength-weak');
            strengthLabel = '弱';
        } else if (strength <= 2) {
            document.getElementById('strength-1').classList.add('strength-fair');
            document.getElementById('strength-2').classList.add('strength-fair');
            strengthLabel = '一般';
        } else if (strength <= 3) {
            document.getElementById('strength-1').classList.add('strength-good');
            document.getElementById('strength-2').classList.add('strength-good');
            document.getElementById('strength-3').classList.add('strength-good');
            strengthLabel = '良好';
        } else {
            indicators.forEach(id => {
                document.getElementById(id).classList.add('strength-strong');
            });
            strengthLabel = '强';
        }
        
        strengthText.textContent = `密码强度：${strengthLabel}`;
    }

    // Toggle password visibility
    window.togglePasswordVisibility = function(inputId) {
        const input = document.getElementById(inputId);
        const icon = input.nextElementSibling.querySelector('.iconify');
        
        if (input.type === 'password') {
            input.type = 'text';
            icon.setAttribute('data-icon', 'heroicons:eye-slash');
        } else {
            input.type = 'password';
            icon.setAttribute('data-icon', 'heroicons:eye');
        }
    };

    // Reset password
    window.resetPassword = function() {
        const newPassword = document.getElementById('new-password').value;
        const confirmPassword = document.getElementById('confirm-password').value;
        
        if (!validatePassword(newPassword, confirmPassword)) {
            return;
        }
        
        const email = document.getElementById('contact-input').value.trim();
        
        fetch('/api/auth/reset-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: email, newPassword: newPassword })
        })
        .then(response => response.json().then(data => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
            if (ok && data.success) {
                if (window.showNotification) window.showNotification('密码重置成功！', 'success');
                nextStep();
            } else {
                showError('new-password', data.error || '重置失败，请重试');
            }
        })
        .catch(() => {
            showError('new-password', '网络错误，请稍后重试');
        });
    };

    function validatePassword(newPassword, confirmPassword) {
        if (!newPassword) {
            showError('new-password', '请输入新密码');
            return false;
        }
        
        if (newPassword.length < 8 || newPassword.length > 20) {
            showError('new-password', '密码长度应为8-20位');
            return false;
        }
        
        if (!/^(?=.*[A-Za-z])(?=.*\d)/.test(newPassword)) {
            showError('new-password', '密码必须包含字母和数字');
            return false;
        }
        
        if (!confirmPassword) {
            showError('confirm-password', '请确认新密码');
            return false;
        }
        
        if (newPassword !== confirmPassword) {
            showError('confirm-password', '两次输入的密码不一致');
            return false;
        }
        
        clearError('new-password');
        clearError('confirm-password');
        return true;
    }

    function validateField(fieldId) {
        const field = document.getElementById(fieldId);
        if (!field) return true;
        
        const value = field.value.trim();
        
        switch(fieldId) {
            case 'contact-input':
                return validateContactInput(value);
            case 'verification-code':
                if (!value) {
                    showError(fieldId, '请输入验证码');
                    return false;
                }
                if (value.length !== 6) {
                    showError(fieldId, '验证码应为6位数字');
                    return false;
                }
                break;
        }
        
        clearError(fieldId);
        return true;
    }

    function showError(fieldId, message) {
        const field = document.getElementById(fieldId);
        const errorContainer = field.closest('.form-control').querySelector('.error-container');
        
        clearError(fieldId);
        
        const errorElement = document.createElement('div');
        errorElement.className = 'text-error text-sm error-message';
        errorElement.textContent = message;
        errorContainer.appendChild(errorElement);
        
        field.classList.add('input-error');
    }

    function clearError(fieldId) {
        const field = document.getElementById(fieldId);
        if (!field) return;
        
        const formControl = field.closest('.form-control');
        if (!formControl) return;
        
        const errorContainer = formControl.querySelector('.error-container');
        if (!errorContainer) return;
        
        while (errorContainer.firstChild) {
            errorContainer.removeChild(errorContainer.firstChild);
        }
        
        field.classList.remove('input-error');
    }

})();
