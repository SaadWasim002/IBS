package com.upi.IBS.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upi.IBS.dto.request.CreditRequest;
import com.upi.IBS.dto.response.BankResponse;
import com.upi.IBS.entity.LedgerEntry;
import com.upi.IBS.service.BankService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import com.upi.IBS.dto.request.HmacSignRequest;
import com.upi.IBS.dto.request.DebitRequest;
import com.upi.IBS.dto.response.HmacSignResponse;
import com.upi.IBS.dto.response.BankResponse;
import com.upi.IBS.service.BankService;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@RestController
public class BankController {

    private final ObjectMapper objectMapper;
    private final String hmacSecret;
    private final BankService bankService;
    private final BankService bankService;

    @Autowired
    public BankController(ObjectMapper objectMapper, @Value("${hmac.secret}") String hmacSecret, BankService bankService) {
        this.objectMapper = objectMapper;
        this.hmacSecret = hmacSecret;
        this.bankService = bankService;
    }

    @PostMapping("/bank/debit")
    public ResponseEntity<BankResponse> debit(@RequestBody @Valid DebitRequest request) {
        BankResponse response = bankService.processDebit(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bank/sign")
    public ResponseEntity<HmacSignResponse> sign(@RequestBody HmacSignRequest request) {
        try {
            // Sort keys alphabetically to match HmacAuthFilter check
            Map<String, Object> payloadMap = new TreeMap<>();
            payloadMap.put("transaction_id", request.getTransactionId().toString());
            payloadMap.put("account_vpa", request.getAccountVpa());
            payloadMap.put("amount_paise", request.getAmountPaise());
            if (request.getUpiPinHash() != null) {
                payloadMap.put("upi_pin_hash", request.getUpiPinHash());
            }

            // Serialize to JSON string
            String payloadJson = objectMapper.writeValueAsString(payloadMap);

            // Compute HMAC-SHA256
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKeySpec);
            byte[] hmacBytes = sha256Hmac.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(hmacBytes);

            HmacSignResponse response = HmacSignResponse.builder()
                    .transactionId(request.getTransactionId())
                    .accountVpa(request.getAccountVpa())
                    .amountPaise(request.getAmountPaise())
                    .upiPinHash(request.getUpiPinHash())
                    .hmacSignature(signature)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating HMAC signature", e);
            throw new RuntimeException("HMAC generation failed", e);
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
