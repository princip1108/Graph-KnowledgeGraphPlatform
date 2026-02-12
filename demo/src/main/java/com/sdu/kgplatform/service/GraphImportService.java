package com.sdu.kgplatform.service;

import com.sdu.kgplatform.dto.GraphDetailDto;
import org.springframework.web.multipart.MultipartFile;

public interface GraphImportService {
    /**
     * 导入图谱
     * 
     * @param file        JSON文件
     * @param name        图谱名称 (可选，优先于文件内名称)
     * @param description 描述 (可选)
     * @param status      状态 (e.g., "DRAFT")
     * @param domain      领域分类代码 (e.g., "medical")
     * @param coverFile   封面图片 (可选)
     * @param userId      创建者ID
     * @return 创建的图谱详情
     */
    GraphDetailDto importGraph(MultipartFile file, String name, String description, String status,
            String domain, MultipartFile coverFile, Integer userId);
}
