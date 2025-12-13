package com.sdu.kgplatform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关系 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationshipDto {

    private String relationId;

    @NotBlank(message = "源节点ID不能为空")
    private String sourceNodeId;

    @NotBlank(message = "目标节点ID不能为空")
    private String targetNodeId;

    private String sourceNodeName;
    private String targetNodeName;

    @NotBlank(message = "关系类型不能为空")
    private String type;
}
