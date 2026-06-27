package com.upi.IBS.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String vpa) {
        super("Account not found for VPA: " + vpa);
    }
}