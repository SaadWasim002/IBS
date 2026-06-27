package com.upi.IBS.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HmacSignResponse {

    @JsonProperty("transaction_id")
    private UUID transactionId;

    @JsonProperty("account_vpa")
    private String accountVpa;

    @JsonProperty("amount_paise")
    private Long amountPaise;

    @JsonProperty("upi_pin_hash")
    private String upiPinHash;

    @JsonProperty("hmac_signature")
    private String hmacSignature;
}
