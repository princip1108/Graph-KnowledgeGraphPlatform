package com.sdu.kgplatform.service;

import com.sdu.kgplatform.dto.UserRegistrationDto;
import com.sdu.kgplatform.entity.Gender;
import com.sdu.kgplatform.entity.Role;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.entity.UserStatus;
import com.sdu.kgplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sdu.kgplatform.security.CustomUserDetails;

import com.sdu.kgplatform.dto.UserProfileDto;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

/**
 * Spring Security 认证核心方法：根据用户名加载用户数据
 */
@Service
public class UserService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Spring Security 认证核心方法：根据邮箱或手机号加载用户数据
     */
    @Override
    public UserDetails loadUserByUsername(String account) throws UsernameNotFoundException {
        if (account == null || account.trim().isEmpty()) {
            throw new UsernameNotFoundException("账号不能为空");
        }

        User user = null;

        // 尝试用邮箱查找（邮箱格式包含@）
        if (account.contains("@")) {
            user = userRepository.findByEmail(account).orElse(null);
        }

        // 如果邮箱没找到，尝试用手机号查找
        if (user == null) {
            user = userRepository.findByPhone(account).orElse(null);
        }

        // 如果手机号也没找到，尝试用用户名查找
        if (user == null) {
            user = userRepository.findByUserName(account).orElse(null);
        }

        if (user == null) {
            throw new UsernameNotFoundException("账号不存在: " + account);
        }

        if (user.getUserStatus() == UserStatus.DELETED) {
            throw new DisabledException("该账号已注销");
        }
        if (user.getUserStatus() == UserStatus.BANNED) {
            if (user.getBannedUntil() != null && user.getBannedUntil().isBefore(java.time.LocalDateTime.now())) {
                // 封禁已到期，自动解封
                user.setUserStatus(UserStatus.OFFLINE);
                user.setBannedUntil(null);
                userRepository.save(user);
            } else if (user.getBannedUntil() != null) {
                // 临时封禁，提示剩余时间
                java.time.Duration remaining = java.time.Duration.between(java.time.LocalDateTime.now(), user.getBannedUntil());
                long hours = remaining.toHours();
                String msg = hours > 24 ? "该账号已被封禁，剩余" + (hours / 24) + "天" : "该账号已被封禁，剩余" + Math.max(1, hours) + "小时";
                throw new LockedException(msg);
            } else {
                // 永久封禁
                throw new LockedException("该账号已被永久封禁");
            }
        }

        // 返回的用户名使用实际查询的账号
        return new CustomUserDetails(
                user.getUserId(),
                account,
                user.getPasswordHash(),
                user.getAvatar(),
                Collections.singleton(user.getRole().toAuthority()));
    }

    /**
     * 用户注册（支持邮箱或手机号注册）
     * 
     * @param dto 用户注册信息
     * @return 注册成功的用户
     */
    @Transactional
    public User register(UserRegistrationDto dto) {
        String username = dto.getUserName();
        String email = dto.getEmail();
        String phone = dto.getPhone();
        String password = dto.getPassword();

        log.info("[注册] 用户名={}, 邮箱={}, 密码是否为空={}, 密码长度={}",
                username, email, (password == null || password.isEmpty()),
                password != null ? password.length() : 0);

        // 邮箱为必填项
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("邮箱为必填项");
        }

        // 检查用户名是否已存在
        if (userRepository.existsByUserName(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }

        // 检查邮箱是否已存在
        if (email != null && !email.isEmpty() && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已被注册: " + email);
        }

        // 检查手机号是否已存在
        if (phone != null && !phone.isEmpty() && userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("手机号已被注册: " + phone);
        }

        // 创建新用户
        User user = new User();
        user.setUserName(username);
        String encodedPwd = passwordEncoder.encode(password);
        log.info("[注册] BCrypt hash 前10位={}", encodedPwd.substring(0, Math.min(10, encodedPwd.length())));
        user.setPasswordHash(encodedPwd); // 密码加密存储
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(Role.USER); // 默认角色
        user.setUserStatus(UserStatus.ONLINE); // 默认状态
        user.setEmailVerified(true); // 邮箱已通过验证码验证
        user.setPhoneVerified(false); // 手机未验证
        // createdAt 和 updateAt 由 @PrePersist 自动设置

        return userRepository.save(user);
    }

    /**
     * 检查用户名是否存在
     */
    public boolean existsByUserName(String userName) {
        return userRepository.existsByUserName(userName);
    }

    /**
     * 检查邮箱是否存在
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 检查手机号是否存在
     */
    public boolean existsByPhone(String phone) {
        return userRepository.existsByPhone(phone);
    }

    /**
     * 根据账号（邮箱或手机号）获取用户资料
     */
    public UserProfileDto getUserProfile(String account) {
        if (account == null || account.trim().isEmpty()) {
            throw new UsernameNotFoundException("账号不能为空");
        }

        User user = null;

        // 邮箱格式包含@
        if (account.contains("@")) {
            user = userRepository.findByEmail(account).orElse(null);
        }

        // 尝试手机号查找
        if (user == null) {
            user = userRepository.findByPhone(account).orElse(null);
        }

        // 尝试用户名查找
        if (user == null) {
            user = userRepository.findByUserName(account).orElse(null);
        }

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + account);
        }

        return convertToProfileDto(user, false);
    }

    /**
     * 根据用户ID获取用户资料（默认脱敏，用于查看他人资料）
     */
    public UserProfileDto getUserProfileById(Integer userId) {
        return getUserProfileById(userId, true);
    }

    /**
     * 根据用户ID获取用户资料
     * @param mask 是否脱敏（查看自己资料时传false）
     */
    public UserProfileDto getUserProfileById(Integer userId, boolean mask) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        return convertToProfileDto(user, mask);
    }

    /**
     * 更新用户最后登录时间
     */
    @Transactional
    public void updateLastLoginTime(String account) {
        if (account == null || account.trim().isEmpty())
            return;

        User user = null;
        if (account.contains("@")) {
            user = userRepository.findByEmail(account).orElse(null);
        }
        if (user == null) {
            user = userRepository.findByPhone(account).orElse(null);
        }
        if (user == null) {
            user = userRepository.findByUserName(account).orElse(null);
        }
        if (user != null) {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    /**
     * 更新用户资料
     */
    @Transactional
    public UserProfileDto updateUserProfile(String account, UserProfileDto profileDto) {
        if (account == null || account.trim().isEmpty()) {
            throw new UsernameNotFoundException("账号不能为空");
        }

        User user = null;
        if (account.contains("@")) {
            user = userRepository.findByEmail(account).orElse(null);
        }
        if (user == null) {
            user = userRepository.findByPhone(account).orElse(null);
        }
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + account);
        }

        // 更新可修改的字段
        if (profileDto.getUserName() != null && !profileDto.getUserName().trim().isEmpty()) {
            user.setUserName(profileDto.getUserName().trim());
        }
        if (profileDto.getBio() != null) {
            user.setBio(profileDto.getBio().trim());
        }
        if (profileDto.getInstitution() != null) {
            user.setInstitution(profileDto.getInstitution().trim());
        }
        if (profileDto.getBirthday() != null) {
            user.setBirthday(profileDto.getBirthday());
        }
        if (profileDto.getAvatar() != null) {
            user.setAvatar(profileDto.getAvatar().trim());
        }
        // 处理性别字段
        if (profileDto.getGender() != null && !profileDto.getGender().trim().isEmpty()) {
            try {
                user.setGender(Gender.valueOf(profileDto.getGender().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // 忽略无效的性别值
            }
        }
        // 处理电话号码字段（未绑定时可设置，已绑定后不可更改）
        if (profileDto.getPhone() != null && !profileDto.getPhone().trim().isEmpty()) {
            String newPhone = profileDto.getPhone().trim();
            if (user.getPhone() == null || user.getPhone().trim().isEmpty()) {
                // 当前未绑定电话，允许绑定新号码
                if (userRepository.existsByPhone(newPhone)) {
                    throw new IllegalArgumentException("该手机号已被其他用户使用");
                }
                user.setPhone(newPhone);
                user.setPhoneVerified(false);
            }
            // 已绑定电话号码，忽略修改请求
        }
        // 处理邮箱字段（未绑定时可设置，已绑定后不可更改）
        if (profileDto.getEmail() != null && !profileDto.getEmail().trim().isEmpty()) {
            String newEmail = profileDto.getEmail().trim();
            if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                // 当前未绑定邮箱，允许绑定新邮箱
                if (userRepository.existsByEmail(newEmail)) {
                    throw new IllegalArgumentException("该邮箱已被其他用户使用");
                }
                user.setEmail(newEmail);
                user.setEmailVerified(false);
            }
            // 已绑定邮箱，忽略修改请求
        }

        User savedUser = userRepository.save(user);
        return convertToProfileDto(savedUser, false);
    }

    /**
     * 根据用户ID更新用户资料（兼容 OAuth2 登录用户）
     */
    @Transactional
    public UserProfileDto updateUserProfileById(Integer userId, UserProfileDto profileDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + userId));

        if (profileDto.getUserName() != null && !profileDto.getUserName().trim().isEmpty()) {
            user.setUserName(profileDto.getUserName().trim());
        }
        if (profileDto.getBio() != null) {
            user.setBio(profileDto.getBio().trim());
        }
        if (profileDto.getInstitution() != null) {
            user.setInstitution(profileDto.getInstitution().trim());
        }
        if (profileDto.getBirthday() != null) {
            user.setBirthday(profileDto.getBirthday());
        }
        if (profileDto.getAvatar() != null) {
            user.setAvatar(profileDto.getAvatar().trim());
        }
        if (profileDto.getGender() != null && !profileDto.getGender().trim().isEmpty()) {
            try {
                user.setGender(Gender.valueOf(profileDto.getGender().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // 忽略无效的性别值
            }
        }
        if (profileDto.getPhone() != null && !profileDto.getPhone().trim().isEmpty()) {
            String newPhone = profileDto.getPhone().trim();
            if (user.getPhone() == null || user.getPhone().trim().isEmpty()) {
                if (userRepository.existsByPhone(newPhone)) {
                    throw new IllegalArgumentException("该手机号已被其他用户使用");
                }
                user.setPhone(newPhone);
                user.setPhoneVerified(false);
            }
        }
        if (profileDto.getEmail() != null && !profileDto.getEmail().trim().isEmpty()) {
            String newEmail = profileDto.getEmail().trim();
            if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                if (userRepository.existsByEmail(newEmail)) {
                    throw new IllegalArgumentException("该邮箱已被其他用户使用");
                }
                user.setEmail(newEmail);
                user.setEmailVerified(false);
            }
        }

        User saved = userRepository.save(user);
        return convertToProfileDto(saved, false);
    }


    /**
     * 通过邮箱重置密码
     */
    @Transactional
    public void resetPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("该邮箱未注册: " + email));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("[密码重置] 用户 {} 密码已重置", email);
    }

    /**
     * 修改密码（需验证旧密码）
     */
    @Transactional
    public void changePassword(String account, String oldPassword, String newPassword) {
        User user = userRepository.findByEmail(account)
                .or(() -> userRepository.findByPhone(account))
                .or(() -> userRepository.findByUserName(account))
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + account));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("[修改密码] 用户 {} 密码已修改", account);
    }

    /**
     * 更新用户头像
     */
    @Transactional
    public void updateUserAvatar(String account, String avatarUrl) {
        if (account == null || account.trim().isEmpty()) {
            throw new UsernameNotFoundException("账号不能为空");
        }

        User user = null;
        // 先按邮箱查找
        if (account.contains("@")) {
            user = userRepository.findByEmail(account).orElse(null);
        }
        // 再按手机号查找
        if (user == null) {
            user = userRepository.findByPhone(account).orElse(null);
        }
        // 最后按用户名查找
        if (user == null) {
            user = userRepository.findByUserName(account).orElse(null);
        }
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + account);
        }

        user.setAvatar(avatarUrl);
        userRepository.save(user);
    }

    /**
     * 将 User 实体转换为 UserProfileDto（默认脱敏，用于查看他人资料）
     */
    private UserProfileDto convertToProfileDto(User user) {
        return convertToProfileDto(user, true);
    }

    /**
     * 将 User 实体转换为 UserProfileDto
     * @param mask 是否脱敏邮箱和手机号（查看他人资料时为true，查看自己资料时为false）
     */
    private UserProfileDto convertToProfileDto(User user, boolean mask) {
        return UserProfileDto.builder()
                .userId(user.getUserId())
                .userName(user.getUserName())
                .email(mask ? maskEmail(user.getEmail()) : user.getEmail())
                .phone(mask ? maskPhone(user.getPhone()) : user.getPhone())
                .avatar(user.getAvatar())
                .gender(user.getGender() != null ? user.getGender().name() : null)
                .birthday(user.getBirthday())
                .institution(user.getInstitution())
                .bio(user.getBio())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .status(user.getUserStatus() != null ? user.getUserStatus().getDescription() : null)
                .emailVerified(user.getEmailVerified())
                .phoneVerified(user.getPhoneVerified())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    /**
     * 邮箱脱敏
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String name = parts[0];
        if (name.length() <= 2) {
            return name.charAt(0) + "***@" + parts[1];
        }
        return name.substring(0, 2) + "***@" + parts[1];
    }

    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
