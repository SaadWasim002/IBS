package com.upi.IBS.service;

import com.upi.IBS.dto.request.CreditRequest;
import com.upi.IBS.dto.request.DebitRequest;
import com.upi.IBS.dto.response.BankResponse;
import com.upi.IBS.entity.Account;
import com.upi.IBS.entity.EntryType;
import com.upi.IBS.entity.LedgerEntry;
import com.upi.IBS.exception.*;
import com.upi.IBS.repository.AccountRepository;
import com.upi.IBS.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AccountLockService accountLockService;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BankResponse processDebit(DebitRequest request) {
        MDC.put("transaction_id", request.getTransactionId().toString());
        try {
            log.info("Processing debit for VPA: {} amount: {} paise",
                    request.getAccountVpa(), request.getAmountPaise());

            // Step 1: Validate request and account state
            validateDuplicateTransaction(request.getTransactionId());
            Account account = findAndValidateAccount(request.getAccountVpa());

            // Step 2: Verify PIN
            verifyPin(account, request.getUpiPinHash());

            // Step 3: Check business rules
            checkTransactionRules(account, request.getAmountPaise());

            // Step 4: Execute debit transaction
            String rrn = executeDebit(account, request);

            log.info("Debit successful. VPA: {}, RRN: {}, Amount: {} paise",
                    request.getAccountVpa(), rrn, request.getAmountPaise());

            return BankResponse.success(rrn, request.getAccountVpa());
        } finally {
            MDC.remove("transaction_id");
        }
    }

    @Transactional
    public BankResponse processCredit(CreditRequest request) {
        MDC.put("transaction_id", request.getTransactionId().toString());
        try {
            log.info("Processing credit request for VPA: {}, amount: {} paise",
                    request.getAccountVpa(), request.getAmountPaise());

            // Step 1: Idempotency Check
            validateDuplicateTransaction(request.getTransactionId());

            // Step 2: Find the destination account
            Account account = findAccount(request.getAccountVpa());

            // Step 3: Execute credit transaction
            String rrn = executeCredit(account, request);

            return BankResponse.success(rrn, request.getAccountVpa());
        } finally {
            MDC.remove("transaction_id");
        }
    }

    @Transactional(readOnly = true)
    public Optional<LedgerEntry> getLedgerEntryByTransactionId(UUID transactionId) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transactionId);
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.get(0));
    }

    private void validateDuplicateTransaction(UUID transactionId) {
        if (ledgerEntryRepository.existsByTransactionId(transactionId)) {
            log.warn("Duplicate transaction detected: {}", transactionId);
            throw new DuplicateTransactionException(transactionId);
        }
    }

    private Account findAccount(String vpa) {
        return accountRepository.findByVpa(vpa)
                .orElseThrow(() -> new AccountNotFoundException(vpa));
    }

    private Account findAndValidateAccount(String vpa) {
        Account account = findAccount(vpa);
        if (account.isPinLocked()) {
            log.warn("Account is PIN locked: {}", vpa);
            throw new AccountLockedException(vpa);
        }
        return account;
    }

    private void verifyPin(Account account, String rawPin) {
        log.info("Stored Hash: {} - Incoming PIN: {}", account.getUpiPinHash(), rawPin);
        if (passwordEncoder.matches(rawPin, account.getUpiPinHash())) {
            return; // PIN is correct
        }

        // PIN is incorrect. Record the failure in a separate transaction.
        int newAttemptCount = accountLockService.recordFailedPinAttempt(account.getVpa());
        throw new InvalidPinException(newAttemptCount);
    }

    private void checkTransactionRules(Account account, Long amountToDebit) {
        // Check balance
        if (account.getBalancePaise() < amountToDebit) {
            log.warn("Insufficient balance for VPA: {}. Balance: {}, Required: {}",
                    account.getVpa(), account.getBalancePaise(), amountToDebit);
            throw new InsufficientBalanceException(account.getVpa(), account.getBalancePaise(), amountToDebit);
        }

        // Check daily limit
        if (account.getDailyUsedPaise() + amountToDebit > account.getDailyLimitPaise()) {
            log.warn("Daily limit exceeded for VPA: {}", account.getVpa());
            throw new LimitExceededException(account.getVpa());
        }
    }

    private void createAndSaveLedgerEntry(UUID transactionId, Account account, EntryType type, Long amountPaise,
            String rrn) {
        LedgerEntry entry = LedgerEntry.builder()
                .transactionId(transactionId)
                .account(account)
                .type(type)
                .amountPaise(amountPaise)
                .rrn(rrn)
                .build();
        ledgerEntryRepository.save(entry);
    }

    private String executeDebit(Account account, DebitRequest request) {
        // Update account state
        account.setBalancePaise(account.getBalancePaise() - request.getAmountPaise());
        account.setDailyUsedPaise(account.getDailyUsedPaise() + request.getAmountPaise());
        account.setPinAttemptCount(0); // Reset on successful auth
        accountRepository.save(account);

        // Create ledger entry
        String rrn = generateRrn();
        createAndSaveLedgerEntry(request.getTransactionId(), account, EntryType.DEBIT, request.getAmountPaise(), rrn);
        return rrn;
    }

    private String executeCredit(Account account, CreditRequest request) {
        // Update account state
        account.setBalancePaise(account.getBalancePaise() + request.getAmountPaise());
        accountRepository.save(account);

        // Create ledger entry
        String rrn = generateRrn();
        createAndSaveLedgerEntry(request.getTransactionId(), account, EntryType.CREDIT, request.getAmountPaise(), rrn);

        log.info("Successfully credited VPA: {}. New balance: {} paise. RRN: {}",
                request.getAccountVpa(), account.getBalancePaise(), rrn);
        return rrn;
    }

    private String generateRrn() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        // Using a more robust random number generator and ensuring 4 digits
        String random = String.format("%04d", new SecureRandom().nextInt(10000));
        return "RRN" + timestamp + random;
    }
}
