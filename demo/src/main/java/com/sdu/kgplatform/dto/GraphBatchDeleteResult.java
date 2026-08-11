package com.sdu.kgplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphBatchDeleteResult {

    private int requestedCount;
    private List<Integer> successIds;
    private List<FailedItem> failedItems;

    public int getCount() {
        return successIds == null ? 0 : successIds.size();
    }

    public boolean isAllSucceeded() {
        return failedItems == null || failedItems.isEmpty();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedItem {
        private Integer graphId;
        private String code;
        private String message;
    }
}
