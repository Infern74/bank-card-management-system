package com.example.bankcards.exception;

public class CardAlreadyBlockedException extends RuntimeException {
    public CardAlreadyBlockedException(String message) {
        super(message);
    }
}