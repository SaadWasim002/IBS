package com.upi.IBS.exception;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String vpa, long balance, long required) {
        super("Insufficient balance for " + vpa + ". Has: " + balance + " paise, needs: " + required + " paise");
    }
}