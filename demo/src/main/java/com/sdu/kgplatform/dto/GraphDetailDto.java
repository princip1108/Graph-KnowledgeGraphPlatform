package com.sdu.kgplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 图谱详情 DTO - 用于返回图谱完整信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphDetailDto {

    private Integer graphId;
    private Integer uploaderId;
    private String uploaderName;
    private String uploaderAvatar;
    private String name;
    private String description;
    private String status;
    private LocalDate uploadDate;
    private LocalDateTime lastModified;
    private String coverImage;
    private Boolean isCustomCover;
    private String shareLink;

    // 统计信息
    private Integer viewCount;
    private Integer downloadCount;
    private Integer collectCount;
    private Integer nodeCount;
    private Integer relationCount;

    // 图谱质量指标
    private BigDecimal density;
    private BigDecimal relationRichness;
    private BigDecimal entityRichness;

    // 缓存信息
    private String cachedFilePath;
    private String cachedFileFormat;
    private LocalDateTime cachedGenerationDatetime;
    private Boolean isCacheValid;

    // 分类信息
    private Integer categoryId;
    private String categoryName;
    private String domain;
}
