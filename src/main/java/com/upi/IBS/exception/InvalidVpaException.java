package com.upi.IBS.exception;

public class InvalidVpaException extends RuntimeException {
    public InvalidVpaException(String vpa) {
        super("Invalid VPA format: " + vpa);
    }
}
