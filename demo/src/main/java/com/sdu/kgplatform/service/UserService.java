package com.sdu.kgplatform.service;

import com.sdu.kgplatform.dto.UserProfileDto;
import com.sdu.kgplatform.dto.UserRegistrationDto;
import com.sdu.kgplatform.entity.Gender;
import com.sdu.kgplatform.entity.Role;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.entity.UserStatus;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.security.CustomUserDetails;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String account) throws UsernameNotFoundException {
        User user = findByAccount(account)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        refreshUserStatus(user);
        validateUserStatus(user);

        String password = user.getPasswordHash() != null ? user.getPasswordHash() : "";
        Role role = user.getRole() != null ? user.getRole() : Role.USER;

        return new CustomUserDetails(
                user.getUserId(),
                resolvePrimaryAccount(user),
                password,
                user.getAvatar(),
                Collections.singleton(role.toAuthority()));
    }

    @Transactional
    public User register(UserRegistrationDto registrationDto) {
        String userName = trimToNull(registrationDto.getUserName());
        String email = trimToNull(registrationDto.getEmail());
        String phone = trimToNull(registrationDto.getPhone());
        String password = registrationDto.getPassword();

        if (userName == null) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (email == null) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (password == null || password.length() < 8 || password.length() > 20) {
            throw new IllegalArgumentException("密码长度应为8-20位");
        }
        if (userRepository.existsByUserName(userName)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("手机号已被注册");
        }

        User user = new User();
        user.setUserName(userName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.USER);
        user.setUserStatus(UserStatus.OFFLINE);
        user.setEmailVerified(Boolean.TRUE);
        user.setPhoneVerified(Boolean.FALSE);
        user.setNickname(userName);
        user.setBio(null);
        user.setInstitution(null);
        user.setAvatar(null);

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return email != null && userRepository.existsByEmail(email.trim());
    }

    @Transactional
    public void updateLastLoginTime(String account) {
        User user = findRequiredByAccount(account);
        user.setLastLoginAt(LocalDateTime.now());
        if (user.getUserStatus() != UserStatus.DELETED) {
            user.setUserStatus(UserStatus.ONLINE);
        }
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(String account) {
        return toProfile(findRequiredByAccount(account));
    }

    @Transactional(readOnly = true)
    public UserProfileDto getUserProfileById(Integer userId) {
        return toProfile(findRequiredById(userId));
    }

    @Transactional(readOnly = true)
    public UserProfileDto getUserProfileById(Integer userId, boolean sanitizeSensitiveFields) {
        UserProfileDto profile = toProfile(findRequiredById(userId));
        if (sanitizeSensitiveFields) {
            profile.setEmail(null);
            profile.setPhone(null);
        }
        return profile;
    }

    @Transactional
    public UserProfileDto updateUserProfileById(Integer userId, UserProfileDto profileDto) {
        User user = findRequiredById(userId);

        String userName = trimToNull(profileDto.getUserName());
        String email = trimToNull(profileDto.getEmail());
        String phone = trimToNull(profileDto.getPhone());

        if (userName == null) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (email == null) {
            throw new IllegalArgumentException("邮箱不能为空");
        }

        if (!userName.equals(user.getUserName()) && userRepository.existsByUserName(userName)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
        if (phone != null && !phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("手机号已被注册");
        }

        user.setUserName(userName);
        user.setNickname(userName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setInstitution(trimToNull(profileDto.getInstitution()));
        user.setBio(trimToNull(profileDto.getBio()));

        if (profileDto.getGender() != null && !profileDto.getGender().isBlank()) {
            user.setGender(Gender.valueOf(profileDto.getGender().trim().toUpperCase()));
        } else {
            user.setGender(null);
        }
        user.setBirthday(profileDto.getBirthday());

        return toProfile(userRepository.save(user));
    }

    @Transactional
    public void changePassword(String account, String oldPassword, String newPassword) {
        User user = findRequiredByAccount(account);

        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 20) {
            throw new IllegalArgumentException("密码长度应为8-20位");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void resetPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("该邮箱未注册"));

        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 20) {
            throw new IllegalArgumentException("密码长度应为8-20位");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void updateUserAvatar(String account, String avatarUrl) {
        User user = findRequiredByAccount(account);
        user.setAvatar(avatarUrl);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByAccount(String account) {
        String normalized = trimToNull(account);
        if (normalized == null) {
            return Optional.empty();
        }

        return userRepository.findByEmail(normalized)
                .or(() -> userRepository.findByPhone(normalized))
                .or(() -> userRepository.findByUserName(normalized));
    }

    private User findRequiredByAccount(String account) {
        return findByAccount(account)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private User findRequiredById(Integer userId) {
        return userRepository.findById(userId)
                .map(this::refreshUserStatus)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private User refreshUserStatus(User user) {
        if (user.getUserStatus() == UserStatus.BANNED
                && user.getBannedUntil() != null
                && user.getBannedUntil().isBefore(LocalDateTime.now())) {
            user.setUserStatus(UserStatus.OFFLINE);
            user.setBannedUntil(null);
            userRepository.save(user);
        }
        return user;
    }

    private void validateUserStatus(User user) {
        if (user.getUserStatus() == UserStatus.DELETED) {
            throw new IllegalArgumentException("该账号已注销");
        }
        if (user.getUserStatus() == UserStatus.BANNED) {
            throw new IllegalArgumentException("该账号已被封禁");
        }
    }

    private UserProfileDto toProfile(User user) {
        refreshUserStatus(user);
        return UserProfileDto.builder()
                .userId(user.getUserId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .gender(user.getGender() != null ? user.getGender().name() : null)
                .birthday(user.getBirthday())
                .institution(user.getInstitution())
                .bio(user.getBio())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .status(user.getUserStatus() != null ? user.getUserStatus().getDescription() : null)
                .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                .phoneVerified(Boolean.TRUE.equals(user.getPhoneVerified()))
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private String resolvePrimaryAccount(User user) {
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            return user.getPhone();
        }
        return user.getUserName();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
