package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.UserFollow;
import com.sdu.kgplatform.entity.UserFollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户关注 Repository
 */
@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, UserFollowId> {

    List<UserFollow> findByIdFollowerId(Integer followerId);

    List<UserFollow> findByIdFollowedId(Integer followedId);

    default List<UserFollow> findByFollowerId(Integer followerId) {
        return findByIdFollowerId(followerId);
    }

    boolean existsById(UserFollowId id);

    long countByIdFollowerId(Integer followerId);

    long countByIdFollowedId(Integer followedId);

    void deleteByIdFollowerId(Integer followerId);

    void deleteByIdFollowedId(Integer followedId);
}
