package com.example.bankcards.controller;

import com.example.bankcards.dto.CardBlockRequestCreateRequest;
import com.example.bankcards.dto.CardBlockRequestResponse;
import com.example.bankcards.entity.BlockRequestStatus;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/card-block-requests")
@RequiredArgsConstructor
@Tag(name = "Запросы на блокировку карт", description = "API для управления запросами на блокировку карт")
@SecurityRequirement(name = "bearerAuth")
public class CardBlockRequestController {

    private final CardService cardService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    @PostMapping
    @Operation(summary = "Создать запрос на блокировку карты")
    public ResponseEntity<CardBlockRequestResponse> createBlockRequest(
            @Valid @RequestBody CardBlockRequestCreateRequest request) {

        User currentUser = getCurrentUser();

        CardBlockRequestResponse response = cardService.requestCardBlock(
                request.getCardId(),
                currentUser.getId(),
                request.getReason());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    @Operation(summary = "Получить мои запросы на блокировку")
    public ResponseEntity<Page<CardBlockRequestResponse>> getMyBlockRequests(
            @PageableDefault(size = 20) Pageable pageable) {

        User currentUser = getCurrentUser();

        Page<CardBlockRequestResponse> requests = cardService.getUserBlockRequests(
                currentUser.getId(),
                pageable);

        return ResponseEntity.ok(requests);
    }

    @PostMapping("/{requestId}/cancel")
    @Operation(summary = "Отменить свой запрос на блокировку")
    public ResponseEntity<CardBlockRequestResponse> cancelBlockRequest(
            @PathVariable Long requestId) {

        User currentUser = getCurrentUser();

        CardBlockRequestResponse response = cardService.cancelBlockRequest(
                requestId,
                currentUser.getId());

        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получить все запросы на блокировку", description = "Только для администратора")
    public ResponseEntity<Page<CardBlockRequestResponse>> getAllBlockRequests(
            @PageableDefault(size = 20) Pageable pageable) {

        Page<CardBlockRequestResponse> requests = cardService.getAllBlockRequests(pageable);
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получить запросы по статусу", description = "Только для администратора")
    public ResponseEntity<Page<CardBlockRequestResponse>> getBlockRequestsByStatus(
            @PathVariable BlockRequestStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<CardBlockRequestResponse> requests = cardService.getBlockRequestsByStatus(status, pageable);
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Одобрить запрос на блокировку", description = "Только для администратора")
    public ResponseEntity<CardBlockRequestResponse> approveBlockRequest(
            @PathVariable Long requestId,
            @RequestParam(required = false) String adminComment) {

        User currentAdmin = getCurrentUser();
        CardBlockRequestResponse response = cardService.approveBlockRequest(requestId, adminComment, currentAdmin.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Отклонить запрос на блокировку", description = "Только для администратора")
    public ResponseEntity<CardBlockRequestResponse> rejectBlockRequest(
            @PathVariable Long requestId,
            @RequestParam(required = false) String adminComment) {

        User currentAdmin = getCurrentUser();
        CardBlockRequestResponse response = cardService.rejectBlockRequest(requestId, adminComment, currentAdmin.getId());
        return ResponseEntity.ok(response);
    }
}