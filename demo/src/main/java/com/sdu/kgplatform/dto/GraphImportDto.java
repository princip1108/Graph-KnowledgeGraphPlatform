package com.sdu.kgplatform.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphImportDto {
    private String name;
    private String description;
    private String coverImage;
    private List<NodeImportItem> nodes;
    private List<RelationImportItem> relations;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodeImportItem {
        private String name;
        private String type;
        private String description;
        // Capture other properties if needed in the future, currently ignored as per
        // NodeDto limitations
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelationImportItem {
        private String source;
        private String target;
        private String type;
    }
}
