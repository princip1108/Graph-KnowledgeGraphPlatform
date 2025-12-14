package com.sdu.kgplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置 - 静态资源映射
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射上传文件目录（同时支持 demo/uploads 和根目录 uploads）
        // 头像保存在 demo/uploads/avatars/，封面保存在 uploads/covers/
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(
                    "file:///D:/idea projects/KnowledgeGraphPlatform/demo/uploads/",
                    "file:///D:/idea projects/KnowledgeGraphPlatform/uploads/"
                );
    }
}
