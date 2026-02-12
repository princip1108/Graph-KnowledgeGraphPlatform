package com.sdu.kgplatform.service;

import com.sdu.kgplatform.entity.UserFeedback;
import com.sdu.kgplatform.repository.FeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 用户反馈业务逻辑层
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackRepository feedbackRepository;
    private final EmailService emailService;

    @Value("${spring.mail.username}")
    private String adminEmail;

    public FeedbackService(FeedbackRepository feedbackRepository, EmailService emailService) {
        this.feedbackRepository = feedbackRepository;
        this.emailService = emailService;
    }

    /**
     * 提交反馈：保存到数据库 + 发送邮件通知
     */
    @Transactional
    public UserFeedback submitFeedback(Integer userId, String feedbackType, String subject, String content, String contactEmail) {
        // 1. 保存到数据库
        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setSubject("[" + mapFeedbackType(feedbackType) + "] " + subject);
        feedback.setContent(content);
        feedback.setContactInfo(contactEmail);
        feedback.setSubmitTime(LocalDateTime.now());
        feedbackRepository.save(feedback);
        log.info("用户反馈已保存: feedbackId={}, subject={}", feedback.getFeedbackId(), feedback.getSubject());

        // 2. 异步发送邮件通知管理员
        String timeStr = feedback.getSubmitTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String adminNotice = String.format(
                "收到新的用户反馈：\n\n" +
                "反馈类型：%s\n" +
                "主题：%s\n" +
                "内容：%s\n" +
                "用户邮箱：%s\n" +
                "提交时间：%s\n" +
                "反馈ID：%d",
                mapFeedbackType(feedbackType), subject, content, contactEmail, timeStr, feedback.getFeedbackId()
        );
        emailService.sendSimpleMail(adminEmail, "【Graph+】新用户反馈: " + subject, adminNotice);

        // 3. 异步给用户发确认邮件
        String userConfirm = String.format(
                "您好！\n\n" +
                "感谢您向 Graph+知识图谱平台 提交反馈，我们已收到您的意见。\n\n" +
                "反馈主题：%s\n" +
                "提交时间：%s\n\n" +
                "我们会在24小时内通过此邮箱与您联系，请留意查收。\n\n" +
                "——Graph+知识图谱平台团队",
                subject, timeStr
        );
        emailService.sendSimpleMail(contactEmail, "【Graph+】您的反馈已收到", userConfirm);

        return feedback;
    }

    /**
     * 反馈类型映射
     */
    private String mapFeedbackType(String type) {
        if (type == null) return "一般反馈";
        return switch (type) {
            case "bug" -> "问题报告";
            case "feature" -> "功能建议";
            case "general" -> "一般反馈";
            default -> type;
        };
    }
}
