package com.upi.IBS.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upi.IBS.config.SecurityConfig;
import com.upi.IBS.dto.request.CreditRequest;
import com.upi.IBS.dto.response.BankResponse;
import com.upi.IBS.entity.Account;
import com.upi.IBS.entity.LedgerEntry;
import com.upi.IBS.exception.AccountNotFoundException;
import com.upi.IBS.filter.HmacAuthFilter;
import com.upi.IBS.service.BankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BankController.class)
@Import({SecurityConfig.class, HmacAuthFilter.class})
class BankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BankService bankService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${hmac.secret}")
    private String hmacSecret;

    private UUID txnId;
    private String testVpa;
    private Long testAmount;

    @BeforeEach
    void setUp() {
        txnId = UUID.randomUUID();
        testVpa = "riya@okhdfcbank";
        testAmount = 5000L;
    }

    private String createSignedPayload(UUID transactionId, String vpa, Long amount) throws Exception {
        // Step 1: Create sorted map for payload
        Map<String, Object> payloadMap = new TreeMap<>();
        payloadMap.put("transaction_id", transactionId.toString());
        payloadMap.put("account_vpa", vpa);
        payloadMap.put("amount_paise", amount);

        // Step 2: Serialize to JSON exactly like HmacAuthFilter does
        String payloadJson = objectMapper.writeValueAsString(payloadMap);

        // Step 3: Calculate HMAC
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKeySpec);
        byte[] hmacBytes = sha256Hmac.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(hmacBytes);

        // Step 4: Create final payload with signature
        Map<String, Object> finalMap = new LinkedHashMap<>(payloadMap);
        finalMap.put("hmac_signature", signature);

        return objectMapper.writeValueAsString(finalMap);
    }

    @Test
    void credit_Success() throws Exception {
        BankResponse bankResponse = BankResponse.builder()
                .status("SUCCESS")
                .rrn("RRN123456789012")
                .build();

        when(bankService.processCredit(any(CreditRequest.class))).thenReturn(bankResponse);

        String jsonPayload = createSignedPayload(txnId, testVpa, testAmount);

        mockMvc.perform(post("/bank/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.rrn").value("RRN123456789012"))
                .andExpect(jsonPath("$.failure_reason").doesNotExist());
    }

    @Test
    void credit_AccountNotFound() throws Exception {
        when(bankService.processCredit(any(CreditRequest.class)))
                .thenThrow(new AccountNotFoundException("Account not found for VPA: " + testVpa));

        String jsonPayload = createSignedPayload(txnId, testVpa, testAmount);

        mockMvc.perform(post("/bank/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.failure_reason").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void credit_ValidationFailure() throws Exception {
        // Invalid request: negative amount
        String jsonPayload = createSignedPayload(txnId, testVpa, -100L);

        mockMvc.perform(post("/bank/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.failure_reason").value(org.hamcrest.Matchers.containsString("VALIDATION_FAILED")));
    }

    @Test
    void credit_IdempotentRecoveryOnDatabaseConstraintViolation() throws Exception {
        // Mock service throwing DataIntegrityViolationException on processCredit
        when(bankService.processCredit(any(CreditRequest.class)))
                .thenThrow(new DataIntegrityViolationException("Unique index violation"));

        // Mock service returning existing ledger entry details
        LedgerEntry existingLedger = LedgerEntry.builder()
                .entryId(UUID.randomUUID())
                .transactionId(txnId)
                .rrn("RRN999999999999")
                .build();
        when(bankService.getLedgerEntryByTransactionId(txnId)).thenReturn(Optional.of(existingLedger));

        String jsonPayload = createSignedPayload(txnId, testVpa, testAmount);

        mockMvc.perform(post("/bank/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.rrn").value("RRN999999999999"))
                .andExpect(jsonPath("$.failure_reason").doesNotExist());
    }

    @Test
    void credit_InvalidHmacSignature() throws Exception {
        // Modify signature to make it invalid
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("transaction_id", txnId.toString());
        payloadMap.put("account_vpa", testVpa);
        payloadMap.put("amount_paise", testAmount);
        payloadMap.put("hmac_signature", "invalid-signature");

        String jsonPayload = objectMapper.writeValueAsString(payloadMap);

        mockMvc.perform(post("/bank/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid HMAC signature."));
    }
}
