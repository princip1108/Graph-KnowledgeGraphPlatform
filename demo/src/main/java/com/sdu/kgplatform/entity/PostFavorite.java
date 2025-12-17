package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 帖子收藏实体类
 */
@Entity
@Table(name = "post_favorite")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostFavorite {

    @EmbeddedId
    private PostFavoriteId id;

    @Column(name = "favorite_time")
    private LocalDateTime favoriteTime;
}
