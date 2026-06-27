package com.upi.IBS.controller;

import com.upi.IBS.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
public class DatabaseCheckController {

    private final AccountRepository accountRepository;

    @Autowired
    public DatabaseCheckController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping("/bank/db-check")
    public ResponseEntity<?> checkDatabaseConnection() {
        try {
            long accountCount = accountRepository.count();
            String message = "Database connection is OK. Found " + accountCount + " accounts.";
            return ResponseEntity.ok(Collections.singletonMap("message", message));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Database connection failed: " + e.getMessage()));
        }
    }
}