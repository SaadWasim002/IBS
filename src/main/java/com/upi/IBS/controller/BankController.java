package com.upi.IBS.controller;

import com.upi.IBS.dto.request.DebitRequest;
import com.upi.IBS.dto.response.BankResponse;
import com.upi.IBS.service.BankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/bank")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;

    @PostMapping("/debit")
    public ResponseEntity<BankResponse> debit(@RequestBody @Valid DebitRequest request) {
        BankResponse response = bankService.processDebit(request);
        return ResponseEntity.ok(response);
    }
}