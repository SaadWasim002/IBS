    package com.upi.IBS.exception;

import java.util.UUID;

public class DuplicateTransactionException extends RuntimeException {
    public DuplicateTransactionException(UUID transactionId) {
        super("Transaction already processed: " + transactionId);
    }
}