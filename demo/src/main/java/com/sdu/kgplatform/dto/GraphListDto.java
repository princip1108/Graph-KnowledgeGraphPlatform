package com.sdu.kgplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 图谱列表项 DTO - 用于列表展示的简化信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphListDto {

    private Integer graphId;
    private String name;
    private String description;
    private String status;
    private String coverImage;
    private Boolean isCustomCover;
    private LocalDate uploadDate;
    
    // 上传者信息
    private Integer uploaderId;
    private String uploaderName;

    // 统计信息
    private Integer viewCount;
    private Integer collectCount;
    private Integer nodeCount;
    private Integer relationCount;
}
