package com.upi.IBS.service;

import com.upi.IBS.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class FailureSimulatorService {

    private final ConcurrentMap<String, String> simulatedFailures = new ConcurrentHashMap<>();

    public void setFailure(String vpa, String failureType) {
        log.info("Setting simulated failure of type {} for VPA {}", failureType, vpa);
        simulatedFailures.put(vpa.toLowerCase(), failureType.toUpperCase());
    }

    public void clearFailure(String vpa) {
        log.info("Clearing simulated failure for VPA {}", vpa);
        simulatedFailures.remove(vpa.toLowerCase());
    }

    public void checkAndTriggerFailure(String vpa, long amountPaise) {
        String failureType = simulatedFailures.remove(vpa.toLowerCase());
        if (failureType == null) {
            return;
        }

        log.info("Triggering simulated failure of type {} for VPA {}", failureType, vpa);
        switch (failureType) {
            case "INSUFFICIENT_BALANCE":
                throw new InsufficientBalanceException(vpa, 0L, amountPaise);
            case "INVALID_PIN":
                throw new InvalidPinException(1);
            case "LIMIT_EXCEEDED":
                throw new LimitExceededException(vpa);
            case "ACCOUNT_LOCKED":
                throw new AccountLockedException(vpa);
            case "TIMEOUT":
                try {
                    log.info("Simulating gateway timeout (sleeping for 6 seconds)...");
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Timeout simulation interrupted", e);
                }
                break;
            default:
                log.warn("Unknown simulated failure type: {}. Ignoring.", failureType);
        }
    }
}
