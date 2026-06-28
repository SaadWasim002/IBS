package com.upi.IBS.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BankResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("rrn")
    private String rrn;

    @JsonProperty("failure_reason")
    private String failureReason;

    @JsonProperty("account_vpa")
    private String accountVpa;

    public static BankResponse success(String rrn, String accountVpa) {
        return BankResponse.builder()
                .status("SUCCESS")
                .rrn(rrn)
                .failureReason(null)
                .accountVpa(accountVpa)
                .build();
    }

    public static BankResponse failure(String failureReason, String accountVpa) {
        return BankResponse.builder()
                .status("FAILURE")
                .rrn(null)
                .failureReason(failureReason)
                .accountVpa(accountVpa)
                .build();
    }
}