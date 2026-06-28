package com.upi.IBS.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class ReversalRequest {

    @NotNull(message = "original_txn_id is required")
    @JsonProperty("original_txn_id")
    private UUID originalTxnId;

    @NotNull(message = "reversal_txn_id is required")
    @JsonProperty("reversal_txn_id")
    private UUID reversalTxnId;

    @NotBlank(message = "account_vpa is required")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+$", message = "Invalid VPA format")
    @JsonProperty("account_vpa")
    private String accountVpa;

    @NotNull(message = "amount_paise is required")
    @Positive(message = "amount_paise must be positive")
    @JsonProperty("amount_paise")
    private Long amountPaise;
}
