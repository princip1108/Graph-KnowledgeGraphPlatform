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
        // 使用绝对路径映射上传文件目录
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:///D:/idea projects/KnowledgeGraphPlatform/demo/uploads/");
    }
}
