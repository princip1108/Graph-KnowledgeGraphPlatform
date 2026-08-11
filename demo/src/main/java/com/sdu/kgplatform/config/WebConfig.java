package com.sdu.kgplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Web 配置 - 静态资源映射
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.base-path:uploads}")
    private String uploadBasePath;

    private static final CacheControl VENDOR_CACHE = CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic();
    private static final CacheControl APP_CACHE = CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic().mustRevalidate();
    private static final CacheControl UPLOAD_CACHE = CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(VENDOR_CACHE);
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(APP_CACHE);
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(APP_CACHE);
        registry.addResourceHandler("/libs/**")
                .addResourceLocations("classpath:/static/libs/")
                .setCacheControl(VENDOR_CACHE);

        // 动态映射上传文件目录，兼容本地开发和生产环境
        String absolutePath = Paths.get(uploadBasePath).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(absolutePath)
                .setCacheControl(UPLOAD_CACHE);
    }
}
