package com.example.bankcards.controller;

import com.example.bankcards.dto.CardCreateRequest;
import com.example.bankcards.dto.CardResponse;
import com.example.bankcards.entity.CardStatus;
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

import java.math.BigDecimal;

@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
@Tag(name = "Карты", description = "API для управления банковскими картами")
@SecurityRequirement(name = "bearerAuth")
public class CardController {

    private final CardService cardService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Создать карту", description = "Только для администратора")
    public ResponseEntity<CardResponse> createCard(@Valid @RequestBody CardCreateRequest request) {
        CardResponse response = cardService.createCard(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Получить баланс карты")
    public ResponseEntity<BigDecimal> getCardBalance(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        BigDecimal balance = cardService.getCardBalance(id, currentUser.getId());
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/my")
    @Operation(summary = "Получить карты текущего пользователя")
    public ResponseEntity<Page<CardResponse>> getUserCards(
            @PageableDefault(size = 20) Pageable pageable) {

        User currentUser = getCurrentUser();

        Page<CardResponse> cards = cardService.getUserCards(currentUser.getId(), pageable);
        return ResponseEntity.ok(cards);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получить все карты", description = "Только для администратора")
    public ResponseEntity<Page<CardResponse>> getAllCards(
            @PageableDefault(size = 20) Pageable pageable) {

        Page<CardResponse> cards = cardService.getAllCards(pageable);
        return ResponseEntity.ok(cards);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить карту по ID")
    public ResponseEntity<CardResponse> getCardById(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        CardResponse card = cardService.getCardById(id, currentUser.getId());
        return ResponseEntity.ok(card);
    }

    @PutMapping("/{id}/block")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Заблокировать карту", description = "Только для администратора")
    public ResponseEntity<CardResponse> adminBlockCard(@PathVariable Long id) {
        CardResponse card = cardService.blockCard(id);
        return ResponseEntity.ok(card);
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Активировать карту", description = "Только для администратора")
    public ResponseEntity<CardResponse> activateCard(@PathVariable Long id) {
        CardResponse card = cardService.activateCard(id);
        return ResponseEntity.ok(card);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Удалить карту", description = "Только для администратора")
    public ResponseEntity<Void> deleteCard(@PathVariable Long id) {
        cardService.deleteCard(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my/search")
    @Operation(summary = "Поиск карт текущего пользователя")
    public ResponseEntity<Page<CardResponse>> searchCards(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {

        User currentUser = getCurrentUser();

        Page<CardResponse> cards = cardService.searchUserCards(currentUser.getId(), query, pageable);
        return ResponseEntity.ok(cards);
    }

    @GetMapping("/my/filter")
    @Operation(summary = "Фильтрация карт по статусу")
    public ResponseEntity<Page<CardResponse>> filterCards(
            @RequestParam(required = false) CardStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        User currentUser = getCurrentUser();
        Page<CardResponse> cards = cardService.filterUserCards(currentUser.getId(), status, pageable);
        return ResponseEntity.ok(cards);
    }
}