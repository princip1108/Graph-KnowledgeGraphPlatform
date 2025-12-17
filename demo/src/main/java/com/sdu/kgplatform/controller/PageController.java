package com.sdu.kgplatform.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面路由控制器 - 处理静态页面访问
 */
@Controller
public class PageController {

    // ========== 主框架页面（导航栏不刷新） ==========
    
    @GetMapping("/app")
    public String mainFrame() {
        return "main";
    }

    @GetMapping("/graph/home_content.html")
    public String homeContent() {
        return "graph/home_content";
    }

    // ========== 图谱相关页面 ==========
    
    @GetMapping({"/", "/graph/home.html"})
    public String home() {
        return "graph/home";
    }

    @GetMapping("/graph/graph_list.html")
    public String graphList() {
        return "graph/graph_list";
    }

    @GetMapping("/graph/graph_detail.html")
    public String graphDetail() {
        return "graph/graph_detail";
    }

    // ========== 用户相关页面 ==========

    @GetMapping("/user/login_register.html")
    public String loginRegister() {
        return "user/login_register";
    }

    @GetMapping("/user/profile.html")
    public String profile() {
        return "user/profile";
    }

    @GetMapping("/user/registration_success.html")
    public String registrationSuccess() {
        return "user/registration_success";
    }

    @GetMapping("/user/password_reset.html")
    public String passwordReset() {
        return "user/password_reset";
    }

    // ========== 社区相关页面 ==========

    @GetMapping("/community/feedback.html")
    public String feedback() {
        return "community/feedback";
    }

    @GetMapping("/community/forum_list.html")
    public String forumList() {
        return "community/forum_list";
    }

    @GetMapping("/community/post_detail.html")
    public String postDetail() {
        return "community/post_detail";
    }

    @GetMapping("/community/post_edit.html")
    public String postEdit() {
        return "community/post_edit";
    }

    // ========== 其他页面 ==========

    @GetMapping("/pages/documentation.html")
    public String documentation() {
        return "pages/documentation";
    }

    @GetMapping("/pages/about.html")
    public String about() {
        return "pages/about";
    }

    @GetMapping("/admin/dashboard.html")
    public String adminDashboard() {
        return "admin/dashboard";
    }
}
