package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评论实体类 - 对应数据库 comment 表
 */
@Entity
@Table(name = "comment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer commentId;

    @Lob
    @Column(name = "comment_text")
    private String commentText;

    @Column(name = "comment_time")
    private LocalDateTime commentTime;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "post_id")
    private Integer postId;

    @Column(name = "parent_comment_id")
    private Integer parentCommentId;
}
