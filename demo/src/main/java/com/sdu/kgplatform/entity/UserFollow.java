package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户关注实体类
 */
@Entity
@Table(name = "user_follow")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserFollow {

    @EmbeddedId
    private UserFollowId id;

    @Column(name = "follow_time")
    private LocalDateTime followTime;
}
