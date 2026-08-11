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
public class GraphOrphanCleanupResult {

    private int validGraphCount;
    private long deletedNodeCount;
    private long deletedRelationshipCount;
    private List<String> warnings;

    public boolean isSuccess() {
        return warnings == null || warnings.isEmpty();
    }
}
