package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图谱收藏实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "graph_favorite")
public class GraphFavorite {

    @EmbeddedId
    private GraphFavoriteId id;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
