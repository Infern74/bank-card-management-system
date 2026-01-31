package com.example.bankcards.exception;

public class CardNotOwnedException extends RuntimeException {
    public CardNotOwnedException(String message) {
        super(message);
    }
}