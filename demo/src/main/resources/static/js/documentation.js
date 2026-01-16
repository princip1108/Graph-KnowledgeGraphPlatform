/**
 * Documentation Page JavaScript
 * 使用文档页面 JavaScript 模块
 */

(function() {
    'use strict';

    document.addEventListener('DOMContentLoaded', function() {
        initializeDocSearch();
        initializeFaqSearch();
        initializeSmoothScroll();
        initializeActiveNavLink();
    });

    // Document search functionality
    function initializeDocSearch() {
        const searchInput = document.getElementById('docSearchInput');
        const searchBtn = document.getElementById('docSearchBtn');
        
        if (searchInput && searchBtn) {
            searchBtn.addEventListener('click', function() {
                performDocSearch(searchInput.value);
            });
            
            searchInput.addEventListener('keydown', function(e) {
                if (e.key === 'Enter') {
                    performDocSearch(searchInput.value);
                }
            });
        }
    }

    function performDocSearch(query) {
        if (!query.trim()) return;
        
        // Highlight matching text in articles
        const articles = document.querySelectorAll('article');
        let found = false;
        
        articles.forEach(article => {
            const text = article.textContent.toLowerCase();
            if (text.includes(query.toLowerCase())) {
                article.scrollIntoView({ behavior: 'smooth', block: 'start' });
                found = true;
                return;
            }
        });
        
        if (!found && window.showNotification) {
            window.showNotification('未找到相关内容', 'info');
        }
    }

    // FAQ search functionality
    function initializeFaqSearch() {
        const faqSearchInput = document.getElementById('faqSearchInput');
        const faqSearchBtn = document.getElementById('faqSearchBtn');
        
        if (faqSearchInput && faqSearchBtn) {
            faqSearchBtn.addEventListener('click', function() {
                filterFaq(faqSearchInput.value);
            });
            
            faqSearchInput.addEventListener('input', function() {
                filterFaq(this.value);
            });
        }
    }

    function filterFaq(query) {
        const faqItems = document.querySelectorAll('#faqAccordion .collapse');
        const lowerQuery = query.toLowerCase().trim();
        
        faqItems.forEach(item => {
            const title = item.querySelector('.collapse-title');
            const content = item.querySelector('.collapse-content');
            const text = (title?.textContent || '') + (content?.textContent || '');
            
            if (lowerQuery === '' || text.toLowerCase().includes(lowerQuery)) {
                item.style.display = '';
            } else {
                item.style.display = 'none';
            }
        });
    }

    // Smooth scroll for anchor links
    function initializeSmoothScroll() {
        document.querySelectorAll('a[href^="#"]').forEach(anchor => {
            anchor.addEventListener('click', function(e) {
                e.preventDefault();
                const targetId = this.getAttribute('href').substring(1);
                const target = document.getElementById(targetId);
                
                if (target) {
                    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    updateActiveNavLink(targetId);
                }
            });
        });
    }

    // Active navigation link tracking
    function initializeActiveNavLink() {
        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    updateActiveNavLink(entry.target.id);
                }
            });
        }, { rootMargin: '-100px 0px -50% 0px' });
        
        document.querySelectorAll('article[id]').forEach(article => {
            observer.observe(article);
        });
    }

    function updateActiveNavLink(activeId) {
        document.querySelectorAll('.doc-nav-link').forEach(link => {
            link.classList.remove('active');
            if (link.getAttribute('href') === '#' + activeId) {
                link.classList.add('active');
            }
        });
    }

    // Scroll to section helper
    window.scrollToSection = function(sectionId) {
        const section = document.getElementById(sectionId);
        if (section) {
            section.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    };

})();
