package com.upi.IBS.controller;

import com.upi.IBS.dto.request.CreditRequest;
import com.upi.IBS.dto.request.DebitRequest;
import com.upi.IBS.dto.request.HmacSignRequest;
import com.upi.IBS.dto.response.BankResponse;
import com.upi.IBS.dto.response.HmacSignResponse;
import com.upi.IBS.entity.LedgerEntry;
import com.upi.IBS.service.BankService;
import com.upi.IBS.service.HmacSigningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final HmacSigningService hmacSigningService;

    @PostMapping("/bank/debit")
    public ResponseEntity<BankResponse> debit(@RequestBody @Valid DebitRequest request) {
        try {
            BankResponse response = bankService.processDebit(request);
            return ResponseEntity.ok(response);
        } catch (DataIntegrityViolationException e) {
            return handleIdempotentRecovery(request.getTransactionId(), request.getAccountVpa(), e);
        }
    }

    @PostMapping("/bank/credit")
    public ResponseEntity<BankResponse> credit(@Valid @RequestBody CreditRequest request) {
        try {
            BankResponse response = bankService.processCredit(request);
            return ResponseEntity.ok(response);
        } catch (DataIntegrityViolationException e) {
            return handleIdempotentRecovery(request.getTransactionId(), request.getAccountVpa(), e);
        }
    }

    @PostMapping("/bank/sign")
    public ResponseEntity<HmacSignResponse> sign(@RequestBody HmacSignRequest request) {
        HmacSignResponse response = hmacSigningService.processSign(request);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<BankResponse> handleIdempotentRecovery(UUID transactionId, String accountVpa, DataIntegrityViolationException e) {
        log.info("DataIntegrityViolationException caught (possible concurrent duplicate request). Attempting idempotent recovery for transaction ID: {}", transactionId);
        Optional<LedgerEntry> existing = bankService.getLedgerEntryByTransactionId(transactionId);
        if (existing.isPresent()) {
            log.info("Idempotent recovery successful. Returning existing transaction details with RRN: {}", existing.get().getRrn());
            BankResponse response = BankResponse.success(existing.get().getRrn(), accountVpa);
            return ResponseEntity.ok(response);
        }
        // If it wasn't a duplicate transaction ID violation, rethrow the exception
        throw e;
    }
}
