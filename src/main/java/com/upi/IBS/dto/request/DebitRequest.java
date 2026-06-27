package com.upi.IBS.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class DebitRequest {

    @NotNull(message = "transaction_id is required")
    private UUID transactionId;

    @NotBlank(message = "account_vpa is required")
    private String accountVpa;

    @NotNull(message = "amount_paise is required")
    @Positive(message = "amount_paise must be greater than zero")
    private Long amountPaise;

    @NotBlank(message = "upi_pin_hash is required")
    private String upiPinHash;

    @NotBlank(message = "hmac_signature is required")
    private String hmacSignature;
}