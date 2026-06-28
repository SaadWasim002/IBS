package com.upi.IBS.controller;

import com.upi.IBS.dto.request.CreditRequest;
import com.upi.IBS.dto.request.DebitRequest;
import com.upi.IBS.dto.request.HmacSignRequest;
import com.upi.IBS.dto.request.ReversalRequest;
import com.upi.IBS.dto.response.BankResponse;
import com.upi.IBS.dto.response.BalanceResponse;
import com.upi.IBS.dto.response.LedgerEntryResponse;
import com.upi.IBS.dto.response.HmacSignResponse;
import com.upi.IBS.entity.LedgerEntry;
import com.upi.IBS.service.BankService;
import com.upi.IBS.service.HmacSigningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/bank")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final HmacSigningService hmacSigningService;

    @PostMapping("/debit")
    public ResponseEntity<BankResponse> debit(@RequestBody @Valid DebitRequest request) {
        try {
            BankResponse response = bankService.processDebit(request);
            return ResponseEntity.ok(response);
        } catch (DataIntegrityViolationException e) {
            return handleIdempotentRecovery(request.getTransactionId(), request.getAccountVpa(), e);
        }
    }

    @PostMapping("/credit")
    public ResponseEntity<BankResponse> credit(@Valid @RequestBody CreditRequest request) {
        try {
            BankResponse response = bankService.processCredit(request);
            return ResponseEntity.ok(response);
        } catch (DataIntegrityViolationException e) {
            return handleIdempotentRecovery(request.getTransactionId(), request.getAccountVpa(), e);
        }
    }

    @PostMapping("/reversal")
    public ResponseEntity<BankResponse> reversal(@Valid @RequestBody ReversalRequest request) {
        try {
            BankResponse response = bankService.processReversal(request);
            return ResponseEntity.ok(response);
        } catch (DataIntegrityViolationException e) {
            return handleIdempotentRecovery(request.getReversalTxnId(), request.getAccountVpa(), e);
        }
    }

    @PostMapping("/sign")
    public ResponseEntity<HmacSignResponse> sign(@RequestBody HmacSignRequest request) {
        HmacSignResponse response = hmacSigningService.processSign(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/account/{vpa}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String vpa) {
        BalanceResponse response = bankService.getAccountBalance(vpa);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ledger/{transaction_id}")
    public ResponseEntity<List<LedgerEntryResponse>> getLedgerTrail(@PathVariable("transaction_id") UUID transactionId) {
        List<LedgerEntryResponse> response = bankService.getLedgerTrail(transactionId);
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
