package com.sdu.kgplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 轻量级关系 DTO - 用于可视化渲染
 * 只包含拓扑结构必需的字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiteRelationshipDto {
    private String relationId;
    private String sourceNodeId;
    private String targetNodeId;
    private String type;
}
