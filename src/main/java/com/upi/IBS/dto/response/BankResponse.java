package com.upi.IBS.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankResponse {

    private String status;
    private String rrn;

    @JsonProperty("failure_reason")
    private String failureReason;
}
