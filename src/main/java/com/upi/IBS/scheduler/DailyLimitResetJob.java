package com.upi.IBS.scheduler;

import com.upi.IBS.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLimitResetJob {

    private final AccountRepository accountRepository;

    @Transactional
    @Scheduled(cron = "0 0 0 * * *")
    public void resetDailyLimit() {
        log.info("Starting daily limit reset scheduler job...");
        try {
            accountRepository.resetDailyLimits();
            log.info("Successfully reset daily limits for all accounts.");
        } catch (Exception e) {
            log.error("Failed to reset daily limits", e);
        }
    }
}
