package com.upi.IBS.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditRequest {

    @NotNull(message = "transaction_id is required")
    @JsonProperty("transaction_id")
    private UUID transactionId;

    @NotBlank(message = "account_vpa is required")
    @JsonProperty("account_vpa")
    private String accountVpa;

    @NotNull(message = "amount_paise is required")
    @Positive(message = "amount_paise must be positive")
    @JsonProperty("amount_paise")
    private Long amountPaise;

    @NotBlank(message = "hmac_signature is required")
    @JsonProperty("hmac_signature")
    private String hmacSignature;
}
