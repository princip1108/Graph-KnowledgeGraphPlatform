package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 帖子标签关联实体类 - 对应数据库 post_tag 表
 * 使用复合主键 (post_id, tag_id)
 */
@Entity
@Table(name = "post_tag")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostTag {

    @EmbeddedId
    private PostTagId id;
}
