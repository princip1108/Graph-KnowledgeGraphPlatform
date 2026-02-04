package com.sdu.kgplatform.service;

import com.sdu.kgplatform.domain.dto.ConversationDto;
import com.sdu.kgplatform.entity.Message;
import com.sdu.kgplatform.entity.MessageStatus;
import com.sdu.kgplatform.repository.MessageRepository;
import com.sdu.kgplatform.repository.UserRepository; // Assuming UserRepository exists
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public MessageService(MessageRepository messageRepository, UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    /**
     * 发送私信
     */
    @Transactional
    public Message sendMessage(Integer senderId, Integer receiverId, String content) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setToId(receiverId);
        message.setMessageText(content);
        message.setSendTime(LocalDateTime.now());
        message.setMessageStatus(MessageStatus.已发送);
        message.setIsRead(false);
        return messageRepository.save(message);
    }

    /**
     * 获取会话列表 (MVP实现: 内存分组)
     * 性能优化提示: 如果数据量大，应该在数据库层做分组查询
     */
    public List<ConversationDto> getConversations(Integer userId) {
        // 获取所有相关消息
        List<Message> allMessages = messageRepository.findUserMessages(userId);

        Map<Integer, ConversationDto> conversationMap = new HashMap<>();

        for (Message m : allMessages) {
            boolean isSentByMe = m.getSenderId().equals(userId);
            Integer otherPersonId = isSentByMe ? m.getToId() : m.getSenderId();

            ConversationDto dto = conversationMap.getOrDefault(otherPersonId, new ConversationDto());
            if (dto.getOtherUserId() == null) {
                dto.setOtherUserId(otherPersonId);
                // 填充用户信息
                userRepository.findById(otherPersonId).ifPresent(user -> {
                    dto.setOtherUserName(user.getUserName());
                    // dto.setOtherUserAvatar(user.getAvatar()); // Assuming avatar field
                });
                dto.setUnreadCount(0);
            }

            // 更新最新消息 (列表已按时间倒序，所以遇到的第一个就是最新的)
            if (dto.getLastMessageTime() == null) {
                dto.setLastMessage(m.getMessageText());
                dto.setLastMessageTime(m.getSendTime());
            }

            // 统计未读
            if (!isSentByMe && Boolean.FALSE.equals(m.getIsRead())) {
                dto.setUnreadCount(dto.getUnreadCount() + 1);
            }

            conversationMap.put(otherPersonId, dto);
        }

        return new ArrayList<>(conversationMap.values()).stream()
                .sorted(Comparator.comparing(ConversationDto::getLastMessageTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 获取与某人的聊天记录
     */
    public Page<Message> getHistory(Integer userId, Integer otherUserId, Pageable pageable) {
        return messageRepository.findConversation(userId, otherUserId, pageable);
    }

    /**
     * 标记已读
     */
    @Transactional
    public void markAsRead(Integer userId, Integer senderId) {
        List<Message> unread = messageRepository.findUnreadFromSender(userId, senderId);
        if (!unread.isEmpty()) {
            unread.forEach(m -> m.setIsRead(true));
            messageRepository.saveAll(unread);
        }
    }

    /**
     * 删除单条消息 (仅逻辑删除或物理删除，这里简化为物理删除)
     */
    @Transactional
    public void deleteMessage(Integer userId, Integer messageId) {
        messageRepository.findById(messageId).ifPresent(msg -> {
            if (msg.getSenderId().equals(userId) || msg.getToId().equals(userId)) {
                // 真实场景可能需要双向删除逻辑 (sender_deleted, receiver_deleted)
                // MVP: 直接删除
                messageRepository.delete(msg);
            }
        });
    }

    public long getUnreadCount(Integer userId) {
        return messageRepository.countUnread(userId);
    }
}
