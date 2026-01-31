package com.example.bankcards.dto;

import com.example.bankcards.entity.BlockRequestStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardBlockRequestResponse {

    private Long id;
    private Long cardId;
    private String maskedCardNumber;
    private Long requestedById;
    private String requestedByUsername;
    private String reason;
    private BlockRequestStatus status;
    private Long processedById;
    private String processedByUsername;
    private String adminComment;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime requestedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedAt;
}