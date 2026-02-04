package com.sdu.kgplatform.domain.dto;

import lombok.Data;
import java.time.LocalDateTime;
import com.sdu.kgplatform.entity.ResourceType;

@Data
public class BrowsingHistoryDto {
    private Integer id;
    private Integer userId;
    private ResourceType resourceType;
    private Integer resourceId; // graphId or postId
    private String resourceName; // graph name or post title
    private LocalDateTime viewTime;
}
