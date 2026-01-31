package com.example.bankcards.exception;

public class PendingBlockRequestExistsException extends RuntimeException {
    public PendingBlockRequestExistsException(String message) {
        super(message);
    }
}