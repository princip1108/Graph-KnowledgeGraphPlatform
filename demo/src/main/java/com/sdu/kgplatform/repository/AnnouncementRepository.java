package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 公告数据访问层
 */
@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Integer> {

    List<Announcement> findByIsActiveTrueOrderByIsPinnedDescCreatedAtDesc();

    List<Announcement> findTop5ByIsActiveTrueOrderByIsPinnedDescCreatedAtDesc();
}
