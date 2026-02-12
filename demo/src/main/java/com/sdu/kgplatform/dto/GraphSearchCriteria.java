package com.sdu.kgplatform.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class GraphSearchCriteria {
    private String keyword;
    private Integer categoryId;
    private String domain;

    private LocalDate startDate;
    private LocalDate endDate;

    // Graph Structure
    private Integer minNodes;
    private Integer maxNodes;
    private Integer minEdges;
    private Integer maxEdges;
    private BigDecimal minDensity;
    private BigDecimal maxDensity;

    // Graph Quality
    private BigDecimal minEntityRichness;
    private BigDecimal maxEntityRichness;
    private BigDecimal minRelationRichness;
    private BigDecimal maxRelationRichness;

    // Popularity
    private Integer minViewCount;
    private Integer maxViewCount;
    private Integer minDownloadCount;
    private Integer maxDownloadCount;
}
