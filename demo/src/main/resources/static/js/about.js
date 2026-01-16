/**
 * About Page JavaScript
 * 关于我们页面 JavaScript 模块
 */

(function() {
    'use strict';

    // Team member data
    const teamMembers = {
        member1: {
            name: '张博士',
            role: '首席技术官',
            image: 'https://spark-builder.s3.cn-north-1.amazonaws.com.cn/image/2025/10/28/7f0b60f9-2ecb-4583-9974-a6ac1c86ba3f.png',
            bio: '拥有15年人工智能和知识图谱领域的研究经验，曾在多家知名科技公司担任技术负责人。',
            expertise: ['知识图谱构建', '图神经网络', '自然语言处理', '分布式系统']
        },
        member2: {
            name: '李经理',
            role: '产品总监',
            image: 'https://images.unsplash.com/photo-1675904581137-baf52052e6ce?w=200&h=200',
            bio: '10年产品管理经验，曾成功打造多款百万级用户产品。',
            expertise: ['产品战略规划', '用户体验设计', '敏捷开发管理', '数据驱动决策']
        },
        member3: {
            name: '王工程师',
            role: '前端架构师',
            image: 'https://images.unsplash.com/photo-1681070909604-f555aa006564?w=200&h=200',
            bio: '8年前端开发经验，专注于大规模数据可视化和交互设计。',
            expertise: ['数据可视化', 'React/Vue架构', '性能优化', 'WebGL/Canvas']
        }
    };

    // Initialize when DOM is loaded
    document.addEventListener('DOMContentLoaded', function() {
        initializeTeamModals();
    });

    // Initialize team member modal functionality
    function initializeTeamModals() {
        window.openTeamMemberModal = function(memberId) {
            const member = teamMembers[memberId];
            if (!member) return;

            const modal = document.getElementById('teamMemberModal');
            const modalContent = document.getElementById('modalContent');

            if (modal && modalContent) {
                modalContent.innerHTML = `
                    <div class="flex flex-col md:flex-row gap-6">
                        <div class="flex-shrink-0">
                            <img src="${member.image}" alt="${member.name}" class="w-32 h-32 rounded-full object-cover mx-auto md:mx-0">
                        </div>
                        <div class="flex-1">
                            <h3 class="text-2xl font-bold text-base-content mb-2">${member.name}</h3>
                            <p class="text-primary font-medium mb-4">${member.role}</p>
                            <p class="text-base-content/80 leading-relaxed mb-4">${member.bio}</p>
                            <div>
                                <h4 class="font-semibold text-base-content mb-2">专业领域</h4>
                                <div class="flex flex-wrap gap-2">
                                    ${member.expertise.map(skill => `<span class="badge badge-outline badge-primary">${skill}</span>`).join('')}
                                </div>
                            </div>
                        </div>
                    </div>
                `;
                modal.showModal();
            }
        };
    }

})();
