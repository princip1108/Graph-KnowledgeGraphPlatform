package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户反馈实体类 - 对应数据库 user_feedback 表
 */
@Entity
@Table(name = "user_feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer feedbackId;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "subject", length = 100)
    private String subject;

    @Lob
    @Column(name = "content")
    private String content;

    @Column(name = "contact_info", length = 255)
    private String contactInfo;

    @Column(name = "submit_time")
    private LocalDateTime submitTime;

    @Column(name = "handler_id")
    private Integer handlerId;
}
