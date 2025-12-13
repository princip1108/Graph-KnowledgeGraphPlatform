package com.sdu.kgplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节点 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeDto {

    private String nodeId;

    @NotBlank(message = "节点名称不能为空")
    @Size(max = 255, message = "节点名称不能超过255个字符")
    private String name;

    private String type;
    private String description;  // 节点简介/描述
    private Integer importance;
    private Integer outDegree;
    private Integer inDegree;
    private Integer totalDegree;
}
