package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 帖子点赞实体类 - 对应数据库 post_like 表
 * 使用复合主键 (user_id, post_id)
 */
@Entity
@Table(name = "post_like")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostLike {

    @EmbeddedId
    private PostLikeId id;

    @Column(name = "like_time")
    private LocalDateTime likeTime;
}
