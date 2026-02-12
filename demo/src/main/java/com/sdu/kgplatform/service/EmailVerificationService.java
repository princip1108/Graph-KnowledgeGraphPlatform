package com.sdu.kgplatform.service;

import com.sdu.kgplatform.entity.EmailVerificationCode;
import com.sdu.kgplatform.repository.EmailVerificationCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

/**
 * 邮箱验证码服务：生成、发送、校验
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final int CODE_LENGTH = 6;
    private static final int EXPIRE_MINUTES = 5;
    private static final int COOLDOWN_SECONDS = 60;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private final EmailVerificationCodeRepository codeRepository;
    private final JavaMailSender mailSender;

    public EmailVerificationService(EmailVerificationCodeRepository codeRepository,
                                    JavaMailSender mailSender) {
        this.codeRepository = codeRepository;
        this.mailSender = mailSender;
    }

    /**
     * 发送验证码到指定邮箱
     */
    @Transactional
    public void sendCode(String email, String purpose) {
        // 冷却检查：60秒内不能重复发送
        Optional<EmailVerificationCode> lastCode = codeRepository.findTopByEmailOrderByCreatedAtDesc(email);
        if (lastCode.isPresent()) {
            LocalDateTime lastSent = lastCode.get().getCreatedAt();
            if (lastSent.plusSeconds(COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
                throw new IllegalStateException("发送太频繁，请" + COOLDOWN_SECONDS + "秒后再试");
            }
        }

        // 生成6位数字验证码
        String code = generateCode();

        // 保存到数据库
        EmailVerificationCode entity = new EmailVerificationCode();
        entity.setEmail(email);
        entity.setCode(code);
        entity.setExpireAt(LocalDateTime.now().plusMinutes(EXPIRE_MINUTES));
        entity.setUsed(false);
        codeRepository.save(entity);

        // 发送邮件
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            String subjectPrefix;
            switch (purpose) {
                case "reset": subjectPrefix = "密码重置"; break;
                case "change-password": subjectPrefix = "修改密码"; break;
                default: subjectPrefix = "注册"; break;
            }
            message.setSubject("【GraphWisdom智汇星图】" + subjectPrefix + "验证码");
            message.setText("您好！\n\n您的" + subjectPrefix + "验证码为：" + code
                    + "\n\n验证码有效期为" + EXPIRE_MINUTES + "分钟，请尽快使用。\n\n如非本人操作，请忽略此邮件。");
            mailSender.send(message);
            log.info("验证码已发送至: {}", email);
        } catch (Exception e) {
            log.error("发送验证码邮件失败: {}", e.getMessage());
            throw new RuntimeException("邮件发送失败，请稍后重试");
        }
    }

    /**
     * 校验验证码是否正确且有效
     */
    @Transactional
    public boolean verifyCode(String email, String code) {
        Optional<EmailVerificationCode> opt = codeRepository
                .findTopByEmailAndUsedFalseAndExpireAtAfterOrderByCreatedAtDesc(email, LocalDateTime.now());

        if (opt.isEmpty()) {
            return false;
        }

        EmailVerificationCode entity = opt.get();
        if (entity.getCode().equals(code)) {
            entity.setUsed(true);
            codeRepository.save(entity);
            return true;
        }
        return false;
    }

    private String generateCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
