package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.Role;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // 根据 GitHub ID 查找用户
    Optional<User> findByGithubId(String githubId);

    // 关键词搜索（用户名/邮箱模糊匹配）+ 分页
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.userName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);

    // 关键词搜索 + 角色筛选 + 分页
    @Query("SELECT u FROM User u WHERE u.role = :role AND (" +
           "LOWER(u.userName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchUsersByRole(@Param("keyword") String keyword, @Param("role") Role role, Pageable pageable);

    // 按角色分页查询
    Page<User> findByRole(Role role, Pageable pageable);

    // 按状态分页查询
    Page<User> findByUserStatus(UserStatus status, Pageable pageable);

    // 按状态 + 关键词搜索
    @Query("SELECT u FROM User u WHERE u.userStatus = :status AND (" +
           "LOWER(u.userName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchUsersByStatus(@Param("keyword") String keyword, @Param("status") UserStatus status, Pageable pageable);

    // 统计封禁用户数
    long countByUserStatus(UserStatus status);
}
