package com.upi.IBS.exception;

public class InvalidPinException extends RuntimeException {
    private final int attemptCount;

    public InvalidPinException(int attemptCount) {
        super("Invalid UPI PIN. Attempt " + attemptCount + " of 3");
        this.attemptCount = attemptCount;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}