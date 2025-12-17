package com.sdu.kgplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 * 图谱收藏复合主键
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class GraphFavoriteId implements Serializable {

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "graph_id")
    private Integer graphId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphFavoriteId that = (GraphFavoriteId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(graphId, that.graphId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, graphId);
    }
}
