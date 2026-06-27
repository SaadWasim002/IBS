package com.upi.IBS.controller;

import com.upi.IBS.dto.request.AddAccountRequest;
import com.upi.IBS.entity.Account;
import com.upi.IBS.repository.AccountRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev") // IMPORTANT: Only active in 'dev' profile
@RequiredArgsConstructor
public class TestAccountController {

    private final AccountRepository accountRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @PostMapping("/bank/test/add-account")
    public ResponseEntity<?> addTestAccount(@RequestBody @Valid AddAccountRequest request) {
        if (accountRepository.findByVpa(request.getVpa()).isPresent()) {
            return ResponseEntity.badRequest().body("Account with VPA '" + request.getVpa() + "' already exists.");
        }

        Account account = Account.builder()
                .vpa(request.getVpa())
                .balancePaise(request.getInitialBalancePaise())
                .upiPinHash(passwordEncoder.encode(request.getPin()))
                .dailyLimitPaise(10000000L) // Default: ₹1,00,000
                .dailyUsedPaise(0L)
                .pinLocked(false)
                .pinAttemptCount(0)
                .build();

        Account savedAccount = accountRepository.save(account);
        return ResponseEntity.ok(savedAccount);
    }
}