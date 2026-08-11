package com.sdu.kgplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationshipBatchCreateResult {

    private boolean success;
    private boolean allSucceeded;
    private int requestedCount;
    private int count;
    private int successCount;
    private int failedCount;
    private List<RelationshipDto> relations;
    private List<Map<String, Object>> failedItems;
}
