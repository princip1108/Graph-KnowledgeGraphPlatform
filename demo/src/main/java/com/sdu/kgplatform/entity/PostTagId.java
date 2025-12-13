package com.sdu.kgplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 帖子标签关联复合主键类
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostTagId implements Serializable {

    @Column(name = "post_id")
    private Integer postId;

    @Column(name = "tag_id")
    private Integer tagId;
}
