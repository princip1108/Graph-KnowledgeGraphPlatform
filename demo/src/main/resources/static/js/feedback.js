/**
 * Feedback Page JavaScript
 * 用户反馈页面 JavaScript 模块
 */

(function() {
    'use strict';

    let selectedFiles = [];

    document.addEventListener('DOMContentLoaded', function() {
        initCustomDropdown();
        initFileUpload();
        initFormValidation();
        initFormSubmission();
    });

    function initCustomDropdown() {
        const dropdowns = document.querySelectorAll('.custom-dropdown');
        
        dropdowns.forEach(dropdown => {
            const trigger = dropdown.querySelector('.dropdown-trigger');
            const items = dropdown.querySelectorAll('.dropdown-item');
            const selectedText = dropdown.querySelector('.selected-text');
            const hiddenInput = dropdown.previousElementSibling;
            
            trigger.addEventListener('click', function(e) {
                e.stopPropagation();
                dropdown.classList.toggle('open');
            });
            
            items.forEach(item => {
                item.addEventListener('click', function(e) {
                    e.stopPropagation();
                    const value = this.getAttribute('data-value');
                    const text = this.querySelector('span:last-child').textContent;
                    const icon = this.querySelector('.item-icon').cloneNode(true);
                    
                    selectedText.innerHTML = '';
                    selectedText.appendChild(icon);
                    selectedText.appendChild(document.createTextNode(' ' + text));
                    selectedText.classList.remove('placeholder');
                    
                    if (hiddenInput && hiddenInput.tagName === 'INPUT') {
                        hiddenInput.value = value;
                    }
                    
                    items.forEach(i => i.classList.remove('selected'));
                    this.classList.add('selected');
                    dropdown.classList.remove('open');
                });
            });
        });
        
        document.addEventListener('click', function() {
            dropdowns.forEach(dropdown => dropdown.classList.remove('open'));
        });
    }

    function initFileUpload() {
        const fileUploadArea = document.getElementById('fileUploadArea');
        const fileInput = document.getElementById('fileInput');

        if (!fileUploadArea || !fileInput) return;

        fileUploadArea.addEventListener('click', () => fileInput.click());
        fileInput.addEventListener('change', e => handleFiles(e.target.files));

        fileUploadArea.addEventListener('dragover', e => {
            e.preventDefault();
            fileUploadArea.classList.add('drag-over');
        });

        fileUploadArea.addEventListener('dragleave', e => {
            e.preventDefault();
            fileUploadArea.classList.remove('drag-over');
        });

        fileUploadArea.addEventListener('drop', e => {
            e.preventDefault();
            fileUploadArea.classList.remove('drag-over');
            handleFiles(e.dataTransfer.files);
        });
    }

    function handleFiles(files) {
        const maxSize = 10 * 1024 * 1024;
        const allowedTypes = ['image/', 'application/pdf', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'text/plain'];

        Array.from(files).forEach(file => {
            if (file.size > maxSize) {
                showNotification(`文件 "${file.name}" 超过10MB限制`, 'error');
                return;
            }

            const isAllowed = allowedTypes.some(type => file.type.startsWith(type) || file.type === type);
            if (!isAllowed) {
                showNotification(`文件 "${file.name}" 格式不支持`, 'error');
                return;
            }

            if (selectedFiles.some(f => f.name === file.name && f.size === file.size)) {
                showNotification(`文件 "${file.name}" 已存在`, 'warning');
                return;
            }

            selectedFiles.push(file);
            addFileToList(file);
        });
    }

    function addFileToList(file) {
        const fileList = document.getElementById('fileList');
        const fileItem = document.createElement('div');
        fileItem.className = 'file-item';
        
        let fileSize = (file.size / 1024).toFixed(1) + ' KB';
        if (file.size >= 1024 * 1024) {
            fileSize = (file.size / (1024 * 1024)).toFixed(1) + ' MB';
        }

        fileItem.innerHTML = `
            <div class="file-info">
                <span class="iconify" data-icon="heroicons:document" data-width="20"></span>
                <div>
                    <div class="font-medium">${file.name}</div>
                    <div class="text-sm text-base-content/60">${fileSize}</div>
                </div>
            </div>
            <span class="iconify file-remove" data-icon="heroicons:x-mark" data-width="20" title="移除文件"></span>
        `;

        fileItem.querySelector('.file-remove').addEventListener('click', function() {
            selectedFiles = selectedFiles.filter(f => f !== file);
            fileItem.remove();
        });

        fileList.appendChild(fileItem);
    }

    function initFormValidation() {
        const form = document.getElementById('feedbackForm');
        if (!form) return;

        const inputs = form.querySelectorAll('input[required], textarea[required]');

        inputs.forEach(input => {
            input.addEventListener('blur', () => validateField(input));
            input.addEventListener('input', () => clearFieldError(input));
        });
    }

    function validateField(field) {
        const value = field.value.trim();
        let isValid = true;
        let errorMessage = '';

        clearFieldError(field);

        if (field.hasAttribute('required') && !value) {
            isValid = false;
            errorMessage = '此字段为必填项';
        } else if (field.type === 'email' && value) {
            const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
            if (!emailRegex.test(value)) {
                isValid = false;
                errorMessage = '请输入有效的邮箱地址';
            }
        }

        if (!isValid) showFieldError(field, errorMessage);
        return isValid;
    }

    function showFieldError(field, message) {
        field.classList.add(field.tagName.toLowerCase() + '-error');
        
        const errorDiv = document.createElement('div');
        errorDiv.className = 'error-message';
        errorDiv.innerHTML = `<span class="iconify" data-icon="heroicons:exclamation-circle" data-width="16"></span>${message}`;

        const formControl = field.closest('.form-control');
        if (formControl) {
            const existingError = formControl.querySelector('.error-message');
            if (existingError) existingError.remove();
            formControl.appendChild(errorDiv);
        }
    }

    function clearFieldError(field) {
        field.classList.remove(field.tagName.toLowerCase() + '-error');
        const formControl = field.closest('.form-control');
        if (formControl) {
            const errorMessage = formControl.querySelector('.error-message');
            if (errorMessage) errorMessage.remove();
        }
    }

    function initFormSubmission() {
        const form = document.getElementById('feedbackForm');
        const submitBtn = document.getElementById('submitBtn');

        if (!form || !submitBtn) return;

        form.addEventListener('submit', function(e) {
            e.preventDefault();

            const requiredFields = form.querySelectorAll('input[required], textarea[required]');
            let isFormValid = true;

            requiredFields.forEach(field => {
                if (!validateField(field)) isFormValid = false;
            });

            // Check hidden input for dropdown
            const feedbackType = document.getElementById('feedbackType');
            if (feedbackType && !feedbackType.value) {
                isFormValid = false;
                showNotification('请选择反馈类型', 'error');
            }

            if (!isFormValid) {
                showNotification('请填写所有必填字段', 'error');
                return;
            }

            submitBtn.classList.add('btn-loading');
            submitBtn.disabled = true;

            // 收集表单数据
            const payload = {
                feedbackType: feedbackType.value,
                subject: form.querySelector('[name="subject"]').value.trim(),
                content: form.querySelector('[name="content"]').value.trim(),
                email: form.querySelector('[name="email"]').value.trim()
            };

            fetch('/api/feedback', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            })
            .then(response => response.json())
            .then(data => {
                submitBtn.classList.remove('btn-loading');
                submitBtn.disabled = false;

                if (data.success) {
                    showSuccessMessage();
                    form.reset();
                    selectedFiles = [];
                    document.getElementById('fileList').innerHTML = '';

                    // Reset dropdown
                    const dropdown = document.querySelector('.custom-dropdown');
                    if (dropdown) {
                        const selectedText = dropdown.querySelector('.selected-text');
                        selectedText.innerHTML = '请选择反馈类型';
                        selectedText.classList.add('placeholder');
                        dropdown.querySelectorAll('.dropdown-item').forEach(i => i.classList.remove('selected'));
                    }

                    window.scrollTo({ top: 0, behavior: 'smooth' });
                } else {
                    showNotification(data.error || '提交失败，请稍后重试', 'error');
                }
            })
            .catch(err => {
                submitBtn.classList.remove('btn-loading');
                submitBtn.disabled = false;
                showNotification('网络错误，请稍后重试', 'error');
                console.error('反馈提交失败:', err);
            });
        });
    }

    function showSuccessMessage() {
        const successDiv = document.createElement('div');
        successDiv.className = 'success-message mb-6';
        successDiv.innerHTML = `
            <span class="iconify" data-icon="heroicons:check-circle" data-width="24"></span>
            <div>
                <div class="font-semibold">反馈提交成功！</div>
                <div class="text-sm">感谢您的反馈，我们会在24小时内通过邮箱与您联系。</div>
            </div>
        `;

        const cardBody = document.querySelector('.card-body');
        if (cardBody) {
            cardBody.insertBefore(successDiv, cardBody.firstChild);
            setTimeout(() => successDiv.remove(), 5000);
        }
    }

    function showNotification(message, type) {
        if (window.showNotification) {
            window.showNotification(message, type);
        }
    }

})();
