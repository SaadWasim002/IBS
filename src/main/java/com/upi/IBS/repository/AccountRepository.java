package com.upi.IBS.repository;

import com.upi.IBS.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByVpa(String vpa);

    @Modifying
    @Query("UPDATE Account a SET a.dailyUsedPaise = 0")
    void resetDailyLimits();
}