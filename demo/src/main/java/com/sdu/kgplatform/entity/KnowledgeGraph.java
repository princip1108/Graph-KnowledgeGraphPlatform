package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 知识图谱实体类 - 对应数据库 knowledge_graph 表
 */
@Entity
@Table(name = "knowledge_graph", indexes = {
        @Index(name = "idx_graph_status_hot", columnList = "status, hot_score"),
        @Index(name = "idx_graph_status_domain_hot", columnList = "status, domain, hot_score"),
        @Index(name = "idx_graph_status_view", columnList = "status, view_count"),
        @Index(name = "idx_graph_uploader_modified", columnList = "uploader_id, last_modified"),
        @Index(name = "idx_graph_uploader_status", columnList = "uploader_id, status"),
        @Index(name = "idx_graph_category_status", columnList = "category_id, status"),
        @Index(name = "idx_graph_share_link", columnList = "share_link")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGraph {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer graphId;

    @Column(name = "uploader_id")
    private Integer uploaderId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "description", length = 900)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private GraphStatus status;

    @Column(name = "upload_date")
    private LocalDate uploadDate;

    @Column(name = "last_modified")
    private LocalDateTime lastModified;

    @Column(name = "cover_image", length = 512)
    private String coverImage;

    @Column(name = "is_custom_cover")
    private Boolean isCustomCover = false;

    @Column(name = "share_link", length = 255)
    private String shareLink;

    @Column(name = "cached_file_path", length = 512)
    private String cachedFilePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "cached_file_format")
    private CachedFileFormat cachedFileFormat;

    @Column(name = "cached_generation_datetime")
    private LocalDateTime cachedGenerationDatetime;

    @Column(name = "is_cache_valid")
    private Boolean isCacheValid;

    @Column(name = "view_count")
    private Integer viewCount;

    @Column(name = "download_count")
    private Integer downloadCount;

    @Column(name = "collect_count")
    private Integer collectCount;

    @Column(name = "node_count")
    private Integer nodeCount;

    @Column(name = "relation_count")
    private Integer relationCount;

    @Column(name = "density", precision = 5, scale = 2)
    private BigDecimal density;

    @Column(name = "relation_richness", precision = 5, scale = 2)
    private BigDecimal relationRichness;

    @Column(name = "entity_richness", precision = 5, scale = 2)
    private BigDecimal entityRichness;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "domain", length = 50)
    private String domain = "other";

    @Column(name = "hot_score")
    private Double hotScore = 0.0;
}
