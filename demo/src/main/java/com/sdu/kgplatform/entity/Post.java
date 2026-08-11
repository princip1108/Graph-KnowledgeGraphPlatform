package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 帖子实体类 - 对应数据库 post 表
 */
@Entity
@Table(name = "post", indexes = {
        @Index(name = "idx_post_status_upload", columnList = "post_status, upload_time"),
        @Index(name = "idx_post_status_category", columnList = "post_status, category"),
        @Index(name = "idx_post_status_like", columnList = "post_status, like_count"),
        @Index(name = "idx_post_author_upload", columnList = "author_id, upload_time"),
        @Index(name = "idx_post_author_status", columnList = "author_id, post_status"),
        @Index(name = "idx_post_graph_status", columnList = "graph_id, post_status"),
        @Index(name = "idx_post_pinned_status", columnList = "is_pinned, post_status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer postId;

    @Column(name = "author_id")
    private Integer authorId;

    @Column(name = "post_title", length = 255)
    private String postTitle;

    @Column(name = "post_abstract", length = 900)
    private String postAbstract;

    @Lob
    @Column(name = "post_text", columnDefinition = "TEXT")
    private String postText;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_status")
    private PostStatus postStatus;

    @Column(name = "like_count")
    private Integer likeCount;

    @Column(name = "upload_time")
    private LocalDateTime uploadTime;

    @Column(name = "graph_id")
    private Integer graphId;

    @Column(name = "is_pinned")
    private Boolean isPinned = false;

    @Column(name = "view_count")
    private Integer viewCount = 0;

    @Column(name = "favorite_count")
    private Integer favoriteCount = 0;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "category", length = 50)
    private String category = "other";
}
