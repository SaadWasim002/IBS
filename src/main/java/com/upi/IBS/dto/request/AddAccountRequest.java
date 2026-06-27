package com.upi.IBS.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class AddAccountRequest {
    @NotBlank(message = "vpa is required")
    private String vpa;

    @NotNull(message = "initialBalancePaise is required")
    @PositiveOrZero
    private Long initialBalancePaise;

    @NotBlank(message = "pin is required")
    private String pin;
}