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
     * 获取每个会话的最新一条消息，避免把用户全部私信拉入内存再分组。
     */
    @Query(value = """
            SELECT ranked.message_id, ranked.sender_id, ranked.to_id, ranked.send_time,
                   ranked.message_status, ranked.message_text, ranked.is_read
            FROM (
                SELECT m.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY CASE WHEN m.sender_id = :userId THEN m.to_id ELSE m.sender_id END
                           ORDER BY m.send_time DESC, m.message_id DESC
                       ) AS row_num
                FROM message m
                WHERE m.sender_id = :userId OR m.to_id = :userId
            ) ranked
            WHERE ranked.row_num = 1
            ORDER BY ranked.send_time DESC, ranked.message_id DESC
            """, nativeQuery = true)
    List<Message> findLatestConversationMessages(@Param("userId") Integer userId, Pageable pageable);

    /**
     * 按发送人统计未读数，配合会话列表批量填充。
     */
    @Query("SELECT m.senderId, COUNT(m) FROM Message m WHERE m.toId = :userId AND m.isRead = false GROUP BY m.senderId")
    List<Object[]> countUnreadBySender(@Param("userId") Integer userId);

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
