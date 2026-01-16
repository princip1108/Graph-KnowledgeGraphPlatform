// Post Edit Page JavaScript
let easyMDE;
let tagify;
let postId = new URLSearchParams(window.location.search).get('id');
let graphIdFromUrl = new URLSearchParams(window.location.search).get('graphId');
let graphNameFromUrl = new URLSearchParams(window.location.search).get('graphName');
let hasUnsavedChanges = false;
let autoSaveTimer = null;

document.addEventListener('DOMContentLoaded', async function() {
    easyMDE = new EasyMDE({ 
        element: document.getElementById('postContent'),
        placeholder: "在此输入正文（支持 Markdown）...",
        spellChecker: false,
        status: false,
        toolbar: ["bold", "italic", "heading", "|", "quote", "code", "unordered-list", "ordered-list", "|", "link", "image", "table", "|", "guide"],
        uploadImage: true,
        imageMaxSize: 5 * 1024 * 1024,
        imageAccept: "image/png, image/jpeg, image/gif, image/webp",
        imageUploadFunction: function(file, onSuccess, onError) {
            var formData = new FormData();
            formData.append('file', file);
            fetch('/api/upload/image', {
                method: 'POST',
                body: formData,
                credentials: 'include'
            })
            .then(function(res) { return res.json(); })
            .then(function(data) {
                if (data.success) {
                    onSuccess(data.url);
                } else {
                    onError(data.error || '上传失败');
                }
            })
            .catch(function(err) { onError('网络错误'); });
        }
    });

    tagify = new Tagify(document.getElementById('postTags'), { maxTags: 5 });

    await checkLogin();

    // URL 参数自动填充图谱
    if (graphIdFromUrl) {
        document.getElementById('graphId').value = graphIdFromUrl;
        document.getElementById('selectedGraphName').textContent = graphNameFromUrl || '图谱 #' + graphIdFromUrl;
        document.getElementById('selectedGraph').classList.remove('hidden');
    }

    if (postId) {
        document.title = "编辑帖子 - Graph+知识图谱平台";
        document.getElementById('pageTitle').textContent = "编辑帖子";
        document.getElementById('publishBtn').innerHTML = '<span class="iconify" data-icon="heroicons:pencil-square" data-width="18"></span> 更新';
        loadPostData();
    }

    // 实时预览
    easyMDE.codemirror.on('change', function() {
        updatePreview();
        markUnsaved();
    });
    document.getElementById('postTitle').addEventListener('input', function() {
        document.getElementById('previewTitle').textContent = this.value || '标题预览';
        document.getElementById('previewTitle').classList.toggle('text-base-content/30', !this.value);
        document.getElementById('previewTitle').classList.toggle('text-base-content', !!this.value);
        markUnsaved();
    });

    // 自动保存（30秒）
    autoSaveTimer = setInterval(autoSave, 30000);

    // 退出拦截
    window.addEventListener('beforeunload', function(e) {
        if (hasUnsavedChanges) {
            e.preventDefault();
            e.returnValue = '';
        }
    });
});

function updatePreview() {
    var content = easyMDE.value();
    var previewEl = document.getElementById('previewContent');
    if (content) {
        previewEl.innerHTML = DOMPurify.sanitize(marked.parse(content));
        previewEl.classList.remove('text-base-content/50');
        previewEl.classList.add('text-base-content');
    } else {
        previewEl.innerHTML = '正文预览将显示在这里...';
        previewEl.classList.add('text-base-content/50');
        previewEl.classList.remove('text-base-content');
    }
}

function markUnsaved() {
    hasUnsavedChanges = true;
    document.getElementById('unsavedIndicator').classList.add('show');
}

function markSaved() {
    hasUnsavedChanges = false;
    document.getElementById('unsavedIndicator').classList.remove('show');
}

function autoSave() {
    if (!hasUnsavedChanges) return;
    var draft = {
        title: document.getElementById('postTitle').value,
        content: easyMDE.value(),
        tags: tagify.value.map(function(t) { return t.value; }),
        graphId: document.getElementById('graphId').value,
        abstract: document.getElementById('postAbstract').value,
        time: new Date().toISOString()
    };
    localStorage.setItem('post_draft_' + (postId || 'new'), JSON.stringify(draft));
    markSaved();
    showToast('草稿已自动保存', 'info');
}

async function checkLogin() {
    try {
        var response = await fetch('/user/api/check-auth', { credentials: 'include' });
        var data = await response.json();
        if (!data.authenticated) {
            window.location.href = '/user/login_register.html?redirect=' + encodeURIComponent(window.location.href);
        }
    } catch (e) {
        window.location.href = '/user/login_register.html?redirect=' + encodeURIComponent(window.location.href);
    }
}

async function loadPostData() {
    try {
        var response = await fetch('/api/posts/' + postId, { credentials: 'include' });
        var data = await response.json();
        if (data.success) {
            var post = data.post;
            document.getElementById('postTitle').value = post.postTitle;
            document.getElementById('previewTitle').textContent = post.postTitle;
            document.getElementById('previewTitle').classList.remove('text-base-content/30');
            easyMDE.value(post.postText || '');
            updatePreview();
            if (post.postAbstract) document.getElementById('postAbstract').value = post.postAbstract;
            if (data.tags) tagify.addTags(data.tags.map(function(t) { return t.tagName; }));
            if (post.graphId) {
                document.getElementById('graphId').value = post.graphId;
                document.getElementById('selectedGraphName').textContent = data.graphName || '图谱 #' + post.graphId;
                document.getElementById('selectedGraph').classList.remove('hidden');
            }
            markSaved();
        } else {
            showToast('加载失败: ' + data.error, 'error');
        }
    } catch (e) {
        showToast('加载失败', 'error');
    }
}

async function searchGraphs() {
    var keyword = document.getElementById('graphSearch').value.trim();
    if (!keyword) return;
    try {
        var response = await fetch('/api/graph/search?keyword=' + encodeURIComponent(keyword) + '&size=5');
        var data = await response.json();
        var results = document.getElementById('graphResults');
        results.innerHTML = '';
        if (data.content && data.content.length > 0) {
            data.content.forEach(function(g) {
                var div = document.createElement('div');
                div.className = 'p-2 hover:bg-base-200 rounded cursor-pointer text-sm';
                div.textContent = g.name;
                div.onclick = function() { selectGraph(g.graphId, g.name); };
                results.appendChild(div);
            });
            results.classList.remove('hidden');
        } else {
            results.innerHTML = '<div class="text-sm text-base-content/50 p-2">未找到图谱</div>';
            results.classList.remove('hidden');
        }
    } catch (e) {
        showToast('搜索失败', 'error');
    }
}

function selectGraph(id, name) {
    document.getElementById('graphId').value = id;
    document.getElementById('selectedGraphName').textContent = name;
    document.getElementById('selectedGraph').classList.remove('hidden');
    document.getElementById('graphResults').classList.add('hidden');
    document.getElementById('graphSearch').value = '';
    markUnsaved();
}

function clearGraph() {
    document.getElementById('graphId').value = '';
    document.getElementById('selectedGraph').classList.add('hidden');
    markUnsaved();
}

async function publishPost() {
    var title = document.getElementById('postTitle').value.trim();
    var content = easyMDE.value();
    var tags = tagify.value.map(function(t) { return t.value; });
    var graphId = document.getElementById('graphId').value;
    var abstract = document.getElementById('postAbstract').value.trim() || content.substring(0, 150);

    if (!title) { showToast('请输入标题', 'warning'); return; }
    if (!content) { showToast('请输入正文内容', 'warning'); return; }

    var btn = document.getElementById('publishBtn');
    btn.disabled = true;
    btn.innerHTML = '<span class="loading loading-spinner loading-sm"></span> 处理中...';

    try {
        var url = postId ? '/api/posts/' + postId : '/api/posts';
        var method = postId ? 'PUT' : 'POST';
        var response = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ title: title, content: content, tags: tags, abstract: abstract, graphId: graphId || null })
        });
        var data = await response.json();

        if (data.success) {
            showToast(postId ? '更新成功！' : '发布成功！', 'success');
            localStorage.removeItem('post_draft_' + (postId || 'new'));
            hasUnsavedChanges = false;
            setTimeout(function() {
                window.location.href = '/community/post_detail.html?id=' + (data.post ? data.post.postId : postId);
            }, 1000);
        } else {
            showToast(data.error || '操作失败', 'error');
            resetPublishBtn();
        }
    } catch (e) {
        showToast('网络错误', 'error');
        resetPublishBtn();
    }
}

function resetPublishBtn() {
    var btn = document.getElementById('publishBtn');
    btn.disabled = false;
    btn.innerHTML = postId 
        ? '<span class="iconify" data-icon="heroicons:pencil-square" data-width="18"></span> 更新'
        : '<span class="iconify" data-icon="heroicons:paper-airplane" data-width="18"></span> 发布';
}

function saveDraft() {
    autoSave();
    showToast('草稿已保存', 'success');
}

function showToast(message, type) {
    type = type || 'info';
    var toast = document.getElementById('toast');
    var alert = toast.querySelector('.alert');
    var msg = document.getElementById('toastMessage');
    alert.className = 'alert';
    if (type === 'success') alert.classList.add('alert-success');
    if (type === 'error') alert.classList.add('alert-error');
    if (type === 'warning') alert.classList.add('alert-warning');
    if (type === 'info') alert.classList.add('alert-info');
    msg.textContent = message;
    toast.classList.remove('hidden');
    setTimeout(function() { toast.classList.add('hidden'); }, 3000);
}
