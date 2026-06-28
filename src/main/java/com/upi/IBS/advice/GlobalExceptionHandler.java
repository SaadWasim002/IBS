package com.upi.IBS.advice;

import com.upi.IBS.dto.response.BankResponse;
import com.upi.IBS.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<BankResponse> handleNotFound(AccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(BankResponse.failure("ACCOUNT_NOT_FOUND", null));
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<BankResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(BankResponse.failure("INSUFFICIENT_BALANCE", null));
    }

    @ExceptionHandler(InvalidPinException.class)
    public ResponseEntity<BankResponse> handleInvalidPin(InvalidPinException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(BankResponse.failure("INVALID_PIN", null));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<BankResponse> handleLocked(AccountLockedException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(BankResponse.failure("ACCOUNT_LOCKED", null));
    }

    @ExceptionHandler(LimitExceededException.class)
    public ResponseEntity<BankResponse> handleLimit(LimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(BankResponse.failure("LIMIT_EXCEEDED", null));
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<BankResponse> handleDuplicate(DuplicateTransactionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(BankResponse.failure("DUPLICATE_TRANSACTION", null));
    }

    @ExceptionHandler(InvalidVpaException.class)
    public ResponseEntity<BankResponse> handleInvalidVpa(InvalidVpaException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BankResponse.failure(ex.getMessage(), null));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<BankResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(BankResponse.failure("CONCURRENT_MODIFICATION", null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BankResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BankResponse.failure(message, null));
    }
}