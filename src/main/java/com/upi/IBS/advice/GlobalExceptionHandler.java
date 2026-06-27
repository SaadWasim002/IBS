package com.upi.IBS.advice;

import com.upi.IBS.dto.response.BankResponse;
import com.upi.IBS.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<BankResponse> handleAccountNotFound(AccountNotFoundException e) {
        log.warn("AccountNotFoundException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(BankResponse.builder()
                        .status("FAILURE")
                        .failureReason("ACCOUNT_NOT_FOUND")
                        .build());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<BankResponse> handleAccountLocked(AccountLockedException e) {
        log.warn("AccountLockedException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BankResponse.builder()
                        .status("FAILURE")
                        .failureReason("ACCOUNT_LOCKED")
                        .build());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<BankResponse> handleInsufficientBalance(InsufficientBalanceException e) {
        log.warn("InsufficientBalanceException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BankResponse.builder()
                        .status("FAILURE")
                        .failureReason("INSUFFICIENT_BALANCE")
                        .build());
    }

    @ExceptionHandler(InvalidPinException.class)
    public ResponseEntity<BankResponse> handleInvalidPin(InvalidPinException e) {
        log.warn("InvalidPinException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BankResponse.builder()
                        .status("FAILURE")
                        .failureReason("INVALID_PIN")
                        .build());
    }

    @ExceptionHandler(LimitExceededException.class)
    public ResponseEntity<BankResponse> handleLimitExceeded(LimitExceededException e) {
        log.warn("LimitExceededException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BankResponse.builder()
                        .status("FAILURE")
                        .failureReason("LIMIT_EXCEEDED")
                        .build());
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<BankResponse> handleDuplicateTransaction(DuplicateTransactionException e) {
        log.warn("DuplicateTransactionException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(BankResponse.builder()
                        .status("FAILURE")
                        .failureReason("409 DUPLICATE")
                        .build());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<BankResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException e) {
        log.error("Optimistic locking failure: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(BankResponse.builder()
                        .status("FAILURE")
                        .failureReason("CONCURRENT_UPDATE_CONFLICT")
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BankResponse> handleValidationExceptions(MethodArgumentNotValidException e) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BankResponse.builder()
                        .status("FAILURE")
                        .failureReason("VALIDATION_FAILED: " + errors)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BankResponse> handleGeneralException(Exception e) {
        log.error("Unhandled exception occurred", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BankResponse.builder()
                        .status("FAILURE")
                        .failureReason("INTERNAL_SERVER_ERROR")
                        .build());
    }
}
