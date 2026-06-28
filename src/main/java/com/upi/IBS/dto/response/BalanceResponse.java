package com.upi.IBS.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BalanceResponse {
    @JsonProperty("vpa")
    private String vpa;

    @JsonProperty("balance_paise")
    private Long balancePaise;

    @JsonProperty("daily_limit_paise")
    private Long dailyLimitPaise;

    @JsonProperty("daily_used_paise")
    private Long dailyUsedPaise;

    @JsonProperty("pin_locked")
    private Boolean pinLocked;
}
