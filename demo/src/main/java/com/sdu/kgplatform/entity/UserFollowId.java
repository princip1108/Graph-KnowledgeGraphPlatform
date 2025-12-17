package com.sdu.kgplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 用户关注复合主键
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserFollowId implements Serializable {

    @Column(name = "follower_id")
    private Integer followerId;

    @Column(name = "followed_id")
    private Integer followedId;
}
