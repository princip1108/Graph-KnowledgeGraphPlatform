package com.sdu.kgplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 轻量级节点 DTO - 用于可视化渲染
 * 只包含坐标计算和渲染必需的字段，剔除大文本描述
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiteNodeDto {
    private String nodeId;
    private String name;
    private String type;
}
