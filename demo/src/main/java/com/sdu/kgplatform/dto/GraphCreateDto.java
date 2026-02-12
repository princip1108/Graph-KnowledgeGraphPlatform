package com.sdu.kgplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建图谱请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphCreateDto {

    @NotBlank(message = "图谱名称不能为空")
    @Size(max = 255, message = "图谱名称不能超过255个字符")
    private String name;

    @Size(max = 900, message = "描述不能超过900个字符")
    private String description;

    /**
     * 封面图片URL
     */
    private String coverImage;

    /**
     * 是否为用户自定义封面
     */
    private Boolean isCustomCover;

    /**
     * 初始状态：DRAFT(草稿), PUBLISHED(已发布), PRIVATE(私有)
     */
    private String status;

    /**
     * 分类ID
     */
    private Integer categoryId;

    /**
     * 领域分类代码
     */
    private String domain;
}
