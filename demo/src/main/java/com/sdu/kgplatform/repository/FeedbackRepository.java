package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.UserFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 用户反馈数据访问层
 */
public interface FeedbackRepository extends JpaRepository<UserFeedback, Integer> {

    List<UserFeedback> findByUserIdOrderBySubmitTimeDesc(Integer userId);
}
