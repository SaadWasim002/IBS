package com.upi.IBS.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BankResponse {

    private String status;
    private String rrn;
    private String failureReason;
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
                .status("FAILED")
                .rrn(null)
                .failureReason(failureReason)
                .accountVpa(accountVpa)
                .build();
    }
}