package com.upi.IBS.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class DebitRequest {

    @NotNull(message = "transaction_id is required")
    @JsonProperty("transaction_id")
    private UUID transactionId;

    @NotBlank(message = "account_vpa is required")
    @JsonProperty("account_vpa")
    private String accountVpa;

    @NotNull(message = "amount_paise is required")
    @Positive(message = "amount_paise must be greater than zero")
    @JsonProperty("amount_paise")
    private Long amountPaise;

    @NotBlank(message = "upi_pin_hash is required")
    @JsonProperty("upi_pin_hash")
    private String upiPinHash;

    @NotBlank(message = "hmac_signature is required")
    @JsonProperty("hmac_signature")
    private String hmacSignature;
}