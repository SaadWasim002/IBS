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
            log.info("Processing credit request for VPA: {}, amount: {} paise", request.getAccountVpa(), request.getAmountPaise());

            // 1. Idempotency Check: Check if transaction has already been processed
            List<LedgerEntry> existingEntries = ledgerEntryRepository.findByTransactionId(request.getTransactionId());
            if (!existingEntries.isEmpty()) {
                LedgerEntry existingEntry = existingEntries.get(0);
                log.info("Duplicate transaction detected. Returning existing ledger entry with RRN: {}", existingEntry.getRrn());
                return BankResponse.success(existingEntry.getRrn(), request.getAccountVpa());
            }

            // 2. Look up account by VPA
            Account account = accountRepository.findByVpa(request.getAccountVpa())
                    .orElseThrow(() -> {
                        log.warn("Account not found for VPA: {}", request.getAccountVpa());
                        return new AccountNotFoundException(request.getAccountVpa());
                    });

            // 3. Increment the balance
            long newBalance = account.getBalancePaise() + request.getAmountPaise();
            account.setBalancePaise(newBalance);
            accountRepository.save(account);

            // 4. Generate RRN (12 digits)
            String rrn = "RRN" + (100000000000L + (long) (Math.random() * 900000000000L));

            // 5. Create Ledger Entry
            LedgerEntry ledgerEntry = LedgerEntry.builder()
                    .transactionId(request.getTransactionId())
                    .account(account)
                    .type(EntryType.CREDIT)
                    .amountPaise(request.getAmountPaise())
                    .rrn(rrn)
                    .build();

            ledgerEntryRepository.save(ledgerEntry);
            log.info("Successfully credited VPA: {}. New balance: {} paise. RRN: {}", request.getAccountVpa(), newBalance, rrn);

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

    private Account findAndValidateAccount(String vpa) {
        Account account = accountRepository.findByVpa(vpa)
                .orElseThrow(() -> new AccountNotFoundException(vpa));

        if (account.isPinLocked()) {
            log.warn("Account is PIN locked: {}", vpa);
            throw new AccountLockedException(vpa);
        }
        return account;
    }

    private void verifyPin(Account account, String rawPin) {
        // Note: The request field is named `upiPinHash`, but it should contain the raw PIN
        // for bcrypt's `matches` method to work correctly.
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

    private String executeDebit(Account account, DebitRequest request) {
        // Update account state
        account.setBalancePaise(account.getBalancePaise() - request.getAmountPaise());
        account.setDailyUsedPaise(account.getDailyUsedPaise() + request.getAmountPaise());
        account.setPinAttemptCount(0); // Reset on successful auth
        accountRepository.save(account);

        // Create ledger entry
        String rrn = generateRrn();
        LedgerEntry entry = LedgerEntry.builder()
                .transactionId(request.getTransactionId())
                .account(account)
                .type(EntryType.DEBIT)
                .amountPaise(request.getAmountPaise())
                .rrn(rrn)
                .build();
        ledgerEntryRepository.save(entry);
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
