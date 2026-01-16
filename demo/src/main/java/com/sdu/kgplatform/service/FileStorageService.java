package com.sdu.kgplatform.service;

import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;

public interface FileStorageService {
    /**
     * 初始化存储目录
     */
    void init();

    /**
     * 存储文件
     * 
     * @param file   上传的文件 content
     * @param subDir 子目录名称 (e.g., "avatars", "covers")
     * @return 文件的访问 URL (相对路径)
     */
    String storeFile(MultipartFile file, String subDir);

    /**
     * 获取上传根目录
     */
    Path getUploadPath();
}
