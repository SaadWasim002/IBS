package com.upi.IBS.service;

import com.upi.IBS.entity.Account;
import com.upi.IBS.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLockService {

    private final AccountRepository accountRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordFailedPinAttempt(String vpa) {
        Account account = accountRepository.findByVpa(vpa)
                .orElseThrow(() -> new IllegalStateException("Attempted to record failed PIN for non-existent account: " + vpa));

        int attempts = account.getPinAttemptCount() + 1;
        account.setPinAttemptCount(attempts);

        if (attempts >= 3) {
            account.setPinLocked(true);
            log.warn("Account locked after 3 failed PIN attempts: {}", vpa);
        }
        accountRepository.save(account);
        log.info("Recorded failed PIN attempt {} for VPA: {}", attempts, vpa);
        return attempts;
    }
}