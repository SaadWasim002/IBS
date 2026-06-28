package com.upi.IBS.exception;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(UUID transactionId) {
        super("Transaction not found for ID: " + transactionId);
    }
}
