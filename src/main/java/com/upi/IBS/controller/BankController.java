package com.upi.IBS.controller;

import com.upi.IBS.dto.request.CreditRequest;
import com.upi.IBS.dto.response.BankResponse;
import com.upi.IBS.entity.LedgerEntry;
import com.upi.IBS.service.BankService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
public class BankController {

    private final BankService bankService;

    @Autowired
    public BankController(BankService bankService) {
        this.bankService = bankService;
    }

    @PostMapping("/bank/credit")
    public ResponseEntity<BankResponse> credit(@Valid @RequestBody CreditRequest request) {
        try {
            BankResponse response = bankService.processCredit(request);
            return ResponseEntity.ok(response);
        } catch (DataIntegrityViolationException e) {
            log.info("DataIntegrityViolationException caught (possible concurrent duplicate request). Attempting idempotent recovery for transaction ID: {}", request.getTransactionId());
            Optional<LedgerEntry> existing = bankService.getLedgerEntryByTransactionId(request.getTransactionId());
            if (existing.isPresent()) {
                log.info("Idempotent recovery successful. Returning existing transaction details with RRN: {}", existing.get().getRrn());
                BankResponse response = BankResponse.builder()
                        .status("SUCCESS")
                        .rrn(existing.get().getRrn())
                        .failureReason(null)
                        .build();
                return ResponseEntity.ok(response);
            }
            // If it wasn't a duplicate transaction ID violation, rethrow the exception
            throw e;
        }
    }
}
