package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.OauthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OauthAccountRepository extends JpaRepository<OauthAccount, Integer> {

    void deleteByUserId(Integer userId);
}
