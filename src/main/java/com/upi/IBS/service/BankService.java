package com.upi.IBS.service;

import com.upi.IBS.dto.request.CreditRequest;
import com.upi.IBS.dto.response.BankResponse;
import com.upi.IBS.entity.Account;
import com.upi.IBS.entity.EntryType;
import com.upi.IBS.entity.LedgerEntry;
import com.upi.IBS.exception.AccountNotFoundException;
import com.upi.IBS.repository.AccountRepository;
import com.upi.IBS.repository.LedgerEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class BankService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    public BankService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
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
                return BankResponse.builder()
                        .status("SUCCESS")
                        .rrn(existingEntry.getRrn())
                        .failureReason(null)
                        .build();
            }

            // 2. Look up account by VPA
            Account account = accountRepository.findByVpa(request.getAccountVpa())
                    .orElseThrow(() -> {
                        log.warn("Account not found for VPA: {}", request.getAccountVpa());
                        return new AccountNotFoundException("Account not found for VPA: " + request.getAccountVpa());
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

            return BankResponse.builder()
                    .status("SUCCESS")
                    .rrn(rrn)
                    .failureReason(null)
                    .build();

        } finally {
            MDC.remove("transaction_id");
        }
    }

    @Transactional(readOnly = true)
    public Optional<LedgerEntry> getLedgerEntryByTransactionId(UUID transactionId) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transactionId);
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.get(0));
    }
}
