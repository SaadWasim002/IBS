package com.upi.IBS.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upi.IBS.dto.request.HmacSignRequest;
import com.upi.IBS.dto.response.HmacSignResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
public class HmacSigningService {

    private final ObjectMapper objectMapper;
    private final String hmacSecret;

    public HmacSigningService(ObjectMapper objectMapper, @Value("${hmac.secret}") String hmacSecret) {
        this.objectMapper = objectMapper;
        this.hmacSecret = hmacSecret;
    }

    public HmacSignResponse processSign(HmacSignRequest request) {
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
            String signature = calculateHmac(payloadJson);

            return HmacSignResponse.builder()
                    .transactionId(request.getTransactionId())
                    .accountVpa(request.getAccountVpa())
                    .amountPaise(request.getAmountPaise())
                    .upiPinHash(request.getUpiPinHash())
                    .hmacSignature(signature)
                    .build();
        } catch (Exception e) {
            log.error("Error generating HMAC signature", e);
            throw new RuntimeException("HMAC generation failed", e);
        }
    }

    public String calculateHmac(String data) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKeySpec);
        byte[] hmacBytes = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }
}
