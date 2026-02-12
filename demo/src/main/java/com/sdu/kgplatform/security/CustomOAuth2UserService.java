package com.sdu.kgplatform.security;

import com.sdu.kgplatform.entity.Role;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.entity.UserStatus;
import com.sdu.kgplatform.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * GitHub OAuth2 登录用户服务
 * 处理 GitHub 回调后的用户查找/创建/绑定逻辑
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!"github".equals(registrationId)) {
            throw new OAuth2AuthenticationException("不支持的 OAuth2 提供商: " + registrationId);
        }

        return processGitHubUser(attributes);
    }

    private CustomOAuth2User processGitHubUser(Map<String, Object> attributes) {
        String githubId = String.valueOf(attributes.get("id"));
        String login = (String) attributes.get("login");
        String email = (String) attributes.get("email");
        String avatarUrl = (String) attributes.get("avatar_url");
        String name = (String) attributes.get("name");

        // 1. 通过 githubId 查找已绑定用户
        Optional<User> existingByGithub = userRepository.findByGithubId(githubId);
        if (existingByGithub.isPresent()) {
            User user = existingByGithub.get();
            checkUserStatus(user);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            return buildOAuth2User(user, attributes);
        }

        // 2. 通过邮箱查找已有账户 → 自动绑定
        if (email != null && !email.isEmpty()) {
            Optional<User> existingByEmail = userRepository.findByEmail(email);
            if (existingByEmail.isPresent()) {
                User user = existingByEmail.get();
                checkUserStatus(user);
                user.setGithubId(githubId);
                user.setOauthProvider("github");
                user.setLastLoginAt(LocalDateTime.now());
                // 如果用户没有头像，使用 GitHub 头像
                if (user.getAvatar() == null || user.getAvatar().isEmpty()) {
                    user.setAvatar(avatarUrl);
                }
                userRepository.save(user);
                return buildOAuth2User(user, attributes);
            }
        }

        // 3. 全新用户 → 创建账户
        User newUser = new User();
        String userName = generateUniqueUserName(login);
        newUser.setUserName(userName);
        newUser.setNickname(name != null ? name : login);
        newUser.setEmail(email);
        newUser.setGithubId(githubId);
        newUser.setOauthProvider("github");
        newUser.setAvatar(avatarUrl);
        newUser.setPasswordHash(UUID.randomUUID().toString()); // 随机密码，OAuth 用户不需要密码登录
        newUser.setRole(Role.USER);
        newUser.setUserStatus(UserStatus.ONLINE);
        newUser.setEmailVerified(email != null && !email.isEmpty());
        newUser.setPhoneVerified(false);
        newUser.setLastLoginAt(LocalDateTime.now());

        User saved = userRepository.save(newUser);
        return buildOAuth2User(saved, attributes);
    }

    private void checkUserStatus(User user) {
        if (user.getUserStatus() == UserStatus.DELETED) {
            throw new OAuth2AuthenticationException("该账号已注销");
        }
        if (user.getUserStatus() == UserStatus.BANNED) {
            throw new OAuth2AuthenticationException("该账号已被封禁");
        }
    }

    /**
     * 生成唯一用户名，如果 gh_login 已存在则加随机后缀
     */
    private String generateUniqueUserName(String login) {
        String baseName = "gh_" + login;
        if (!userRepository.existsByUserName(baseName)) {
            return baseName;
        }
        // 加随机后缀
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        return baseName + "_" + suffix;
    }

    /**
     * 构建返回给 Spring Security 的 OAuth2User 对象
     */
    private CustomOAuth2User buildOAuth2User(User user, Map<String, Object> attributes) {
        return new CustomOAuth2User(
                user.getUserId(),
                user.getUserName(),
                user.getAvatar(),
                Collections.singleton(user.getRole().toAuthority()),
                attributes
        );
    }
}
