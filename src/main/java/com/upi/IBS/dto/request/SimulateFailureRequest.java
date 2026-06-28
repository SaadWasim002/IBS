package com.upi.IBS.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulateFailureRequest {

    @NotBlank(message = "vpa is required")
    @JsonProperty("vpa")
    private String vpa;

    @JsonProperty("failure_type")
    private String failureType;

    @JsonProperty("clear")
    private boolean clear;
}
