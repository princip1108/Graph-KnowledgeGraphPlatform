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
@Table(name = "post")
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
    @Column(name = "post_text")
    private String postText;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_status")
    private PostStatus postStatus;

    @Column(name = "like_count")
    private Integer likeCount;

    @Column(name = "upload_time")
    private LocalDateTime uploadTime;
}
