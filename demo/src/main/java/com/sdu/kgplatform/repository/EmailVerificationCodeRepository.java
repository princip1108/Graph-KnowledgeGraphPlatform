package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    // 查询该邮箱最新一条未使用且未过期的验证码
    Optional<EmailVerificationCode> findTopByEmailAndUsedFalseAndExpireAtAfterOrderByCreatedAtDesc(
            String email, LocalDateTime now);

    // 查询该邮箱最近一条记录（用于60秒冷却判断）
    Optional<EmailVerificationCode> findTopByEmailOrderByCreatedAtDesc(String email);
}
