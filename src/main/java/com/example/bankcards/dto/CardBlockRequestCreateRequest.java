package com.example.bankcards.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardBlockRequestCreateRequest {

    @NotNull(message = "Card ID is required")
    private Long cardId;

    @NotBlank(message = "Reason is required")
    private String reason;
}