package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息实体类 - 对应数据库 message 表
 */
@Entity
@Table(name = "message")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer messageId;

    @Column(name = "sender_id")
    private Integer senderId;

    @Column(name = "to_id")
    private Integer toId;

    @Column(name = "send_time")
    private LocalDateTime sendTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_status")
    private MessageStatus messageStatus;

    @Lob
    @Column(name = "message_text")
    private String messageText;
}
