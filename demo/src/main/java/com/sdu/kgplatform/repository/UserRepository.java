package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.Role;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    // 根据用户名查找
    Optional<User> findByUserName(String userName);

    // 根据邮箱查找
    Optional<User> findByEmail(String email);

    // 根据手机号查找
    Optional<User> findByPhone(String phone);

    // 检查用户名是否存在
    boolean existsByUserName(String userName);

    // 检查邮箱是否存在
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    // 根据角色统计用户数量
    long countByRole(Role role);

    // 根据角色查找用户
    List<User> findByRole(Role role);

    // 根据状态查找用户
    List<User> findByUserStatus(UserStatus status);
}
