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
}