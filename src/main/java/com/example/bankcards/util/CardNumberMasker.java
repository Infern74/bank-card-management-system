package com.example.bankcards.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CardNumberMasker {

    @Value("${app.card.mask-pattern}")
    private String maskPattern;

    public String maskCardNumber(String lastFourDigits) {
        return String.format(maskPattern, lastFourDigits);
    }

    public static String extractLastFourDigits(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            throw new IllegalArgumentException("Invalid card number");
        }
        return cardNumber.substring(cardNumber.length() - 4);
    }
}