package com.example.bankcards.controller;

import com.example.bankcards.dto.TransferRequest;
import com.example.bankcards.dto.TransferResponse;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
@Tag(name = "Переводы", description = "API для управления переводами между картами")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {

    private final TransferService transferService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    @PostMapping
    @Operation(summary = "Создать перевод между своими картами")
    public ResponseEntity<TransferResponse> createTransfer(
            @Valid @RequestBody TransferRequest request) {

        User currentUser = getCurrentUser();

        TransferResponse response = transferService.transferBetweenOwnCards(request, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    @Operation(summary = "Получить переводы текущего пользователя")
    public ResponseEntity<Page<TransferResponse>> getUserTransfers(
            @PageableDefault(size = 20) Pageable pageable) {

        User currentUser = getCurrentUser();

        Page<TransferResponse> transfers = transferService.getUserTransfers(currentUser.getId(), pageable);
        return ResponseEntity.ok(transfers);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить перевод по ID")
    public ResponseEntity<TransferResponse> getTransferById(@PathVariable Long id) {

        User currentUser = getCurrentUser();

        TransferResponse transfer = transferService.getTransferById(id, currentUser.getId());
        return ResponseEntity.ok(transfer);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Отменить перевод")
    public ResponseEntity<Void> cancelTransfer(@PathVariable Long id) {

        User currentUser = getCurrentUser();

        transferService.cancelTransfer(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}