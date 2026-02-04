package com.sdu.kgplatform.domain.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConversationDto {
    private Integer otherUserId;
    private String otherUserName;
    private String otherUserAvatar;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;
}
