package com.sdu.kgplatform.service;

import com.sdu.kgplatform.domain.dto.ConversationDto;
import com.sdu.kgplatform.entity.Message;
import com.sdu.kgplatform.entity.MessageStatus;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.entity.UserStatus;
import com.sdu.kgplatform.repository.MessageRepository;
import com.sdu.kgplatform.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
        if (senderId == null || receiverId == null) {
            throw new IllegalArgumentException("发送方和接收方不能为空");
        }
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("不能给自己发送私信");
        }

        requireActiveUser(senderId, "发送方");
        requireActiveUser(receiverId, "接收方");

        String messageText = normalizeMessageText(content);

        Message message = new Message();
        message.setSenderId(senderId);
        message.setToId(receiverId);
        message.setMessageText(messageText);
        message.setSendTime(LocalDateTime.now());
        message.setMessageStatus(MessageStatus.已发送);
        message.setIsRead(false);
        return messageRepository.save(message);
    }

    public List<ConversationDto> getConversations(Integer userId) {
        List<Message> latestMessages = messageRepository.findLatestConversationMessages(userId, PageRequest.of(0, 100));
        Set<Integer> otherUserIds = latestMessages.stream()
                .map(m -> m.getSenderId().equals(userId) ? m.getToId() : m.getSenderId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Integer, User> usersById = userRepository.findAllById(otherUserIds).stream()
                .collect(Collectors.toMap(User::getUserId, user -> user, (left, right) -> left));

        Map<Integer, Integer> unreadCounts = messageRepository.countUnreadBySender(userId).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).intValue(),
                        row -> ((Number) row[1]).intValue(),
                        Integer::sum));

        List<ConversationDto> conversations = new ArrayList<>();
        for (Message m : latestMessages) {
            boolean isSentByMe = m.getSenderId().equals(userId);
            Integer otherPersonId = isSentByMe ? m.getToId() : m.getSenderId();

            ConversationDto dto = new ConversationDto();
            dto.setOtherUserId(otherPersonId);
            User otherUser = usersById.get(otherPersonId);
            if (otherUser != null) {
                dto.setOtherUserName(otherUser.getUserName());
                dto.setOtherUserAvatar(otherUser.getAvatar());
            } else {
                dto.setOtherUserName("未知用户");
            }
            dto.setLastMessage(m.getMessageText());
            dto.setLastMessageTime(m.getSendTime());
            dto.setUnreadCount(unreadCounts.getOrDefault(otherPersonId, 0));
            conversations.add(dto);
        }

        return conversations;
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

    private User requireActiveUser(Integer userId, String label) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(label + "用户不存在"));
        if (user.getUserStatus() == UserStatus.DELETED) {
            throw new IllegalArgumentException(label + "用户已注销");
        }
        if (user.getUserStatus() == UserStatus.BANNED) {
            throw new IllegalArgumentException(label + "用户已被封禁");
        }
        return user;
    }

    private String normalizeMessageText(String content) {
        String messageText = content == null ? null : content.trim();
        if (messageText == null || messageText.isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (messageText.length() > 2000) {
            throw new IllegalArgumentException("消息内容不能超过2000个字符");
        }
        return messageText;
    }
}
