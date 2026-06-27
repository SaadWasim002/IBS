package com.upi.IBS.exception;

public class LimitExceededException extends RuntimeException {
    public LimitExceededException(String vpa) {
        super("Daily transaction limit exceeded for: " + vpa);
    }
}