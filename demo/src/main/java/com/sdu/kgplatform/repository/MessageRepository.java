package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Integer> {

    /**
     * 获取两个用户之间的对话历史
     */
    @Query("SELECT m FROM Message m WHERE (m.senderId = :userId1 AND m.toId = :userId2) OR (m.senderId = :userId2 AND m.toId = :userId1) ORDER BY m.sendTime DESC")
    Page<Message> findConversation(@Param("userId1") Integer userId1, @Param("userId2") Integer userId2,
            Pageable pageable);

    /**
     * 获取我的最近联系人（发送或接收）
     * 这是一个较复杂的查询，为了简化 MVP，我们查询我参与的所有消息，然后应用层分组
     * 或者使用 Group By 查询最新的一条
     */
    @Query("SELECT m FROM Message m WHERE m.senderId = :userId OR m.toId = :userId ORDER BY m.sendTime DESC")
    List<Message> findUserMessages(@Param("userId") Integer userId);

    /**
     * 统计未读消息数
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.toId = :userId AND m.isRead = false")
    long countUnread(@Param("userId") Integer userId);

    /**
     * 将来自某人的消息标记为已读
     */
    @Query("SELECT m FROM Message m WHERE m.senderId = :senderId AND m.toId = :userId AND m.isRead = false")
    List<Message> findUnreadFromSender(@Param("userId") Integer userId, @Param("senderId") Integer senderId);
}
