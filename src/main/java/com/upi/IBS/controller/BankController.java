package com.upi.IBS.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upi.IBS.dto.request.HmacSignRequest;
import com.upi.IBS.dto.response.HmacSignResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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

    @Autowired
    public BankController(ObjectMapper objectMapper, @Value("${hmac.secret}") String hmacSecret) {
        this.objectMapper = objectMapper;
        this.hmacSecret = hmacSecret;
    }

    @PostMapping("/bank/sign")
    public ResponseEntity<HmacSignResponse> sign(@RequestBody HmacSignRequest request) {
        try {
            // Sort keys alphabetically to match HmacAuthFilter check
            Map<String, Object> payloadMap = new TreeMap<>();
            payloadMap.put("transaction_id", request.getTransactionId().toString());
            payloadMap.put("account_vpa", request.getAccountVpa());
            payloadMap.put("amount_paise", request.getAmountPaise());

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
                    .hmacSignature(signature)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating HMAC signature", e);
            throw new RuntimeException("HMAC generation failed", e);
        }
    }
}
