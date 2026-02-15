package com.sdu.kgplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web 配置 - 静态资源映射
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.base-path:uploads}")
    private String uploadBasePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 动态映射上传文件目录，兼容本地开发和生产环境
        String absolutePath = Paths.get(uploadBasePath).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(absolutePath);
    }
}
