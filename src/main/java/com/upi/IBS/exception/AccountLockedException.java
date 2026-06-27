package com.upi.IBS.exception;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String vpa) {
        super("Account locked due to too many failed PIN attempts: " + vpa);
    }
}