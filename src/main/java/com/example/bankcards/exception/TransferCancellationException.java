package com.example.bankcards.exception;

public class TransferCancellationException extends RuntimeException {
    public TransferCancellationException(String message) {
        super(message);
    }
}