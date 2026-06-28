package com.upi.IBS.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.upi.IBS.entity.EntryType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class LedgerEntryResponse {
    @JsonProperty("entry_id")
    private UUID entryId;

    @JsonProperty("transaction_id")
    private UUID transactionId;

    @JsonProperty("account_vpa")
    private String accountVpa;

    @JsonProperty("type")
    private EntryType type;

    @JsonProperty("amount_paise")
    private Long amountPaise;

    @JsonProperty("rrn")
    private String rrn;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
