package com.example.bankcards.service;

import com.example.bankcards.dto.CardBlockRequestResponse;
import com.example.bankcards.dto.CardCreateRequest;
import com.example.bankcards.dto.CardResponse;
import com.example.bankcards.entity.*;
import com.example.bankcards.exception.*;
import com.example.bankcards.repository.CardBlockRequestRepository;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.util.CardNumberMasker;
import com.example.bankcards.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;
    private final CardNumberMasker cardNumberMasker;
    private final CardBlockRequestRepository cardBlockRequestRepository;

    @Value("${app.card.max-initial-balance:1000000.00}")
    private BigDecimal maxInitialBalance;

    @Transactional
    public CardResponse createCard(CardCreateRequest request) {
        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + request.getOwnerId()));

        String cardNumber = request.getCardNumber();
        String cardHash = encryptionUtil.hash(cardNumber);

        if (cardRepository.findByCardNumberHash(cardHash).isPresent()) {
            throw new IllegalArgumentException("Card with this number already exists");
        }

        if (request.getExpirationDate().isBefore(LocalDate.now().plusMonths(1))) {
            throw new IllegalArgumentException("Expiration date must be at least 1 month in the future");
        }

        if (request.getInitialBalance().compareTo(maxInitialBalance) > 0) {
            throw new IllegalArgumentException("Initial balance exceeds maximum allowed: " + maxInitialBalance);
        }

        Card card = Card.builder()
                .cardNumberEncrypted(encryptionUtil.encrypt(cardNumber))
                .cardNumberHash(cardHash)
                .cardNumberLastFour(cardNumber.substring(cardNumber.length() - 4))
                .cardHolderName(request.getCardHolderName())
                .expirationDate(request.getExpirationDate())
                .status(CardStatus.ACTIVE)
                .balance(request.getInitialBalance() != null ?
                        request.getInitialBalance() : BigDecimal.ZERO)
                .cvvEncrypted(encryptionUtil.encrypt(request.getCvv()))
                .owner(owner)
                .build();

        card = cardRepository.save(card);
        return mapToResponse(card);
    }

    @Transactional(readOnly = true)
    public Page<CardResponse> getUserCards(Long ownerId, Pageable pageable) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + ownerId));

        return cardRepository.findByOwner(owner, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<CardResponse> getAllCards(Pageable pageable) {
        return cardRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public CardResponse getCardById(Long cardId, Long userId) {
        Card card = cardRepository.findByIdWithOwner(cardId)
                .orElseThrow(() -> new CardNotFoundException("Card not found with id: " + cardId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        boolean isOwner = card.getOwner().getId().equals(userId);
        boolean isAdmin = user.getRoles().contains(Role.ROLE_ADMIN);

        if (!isOwner && !isAdmin) {
            throw new CardAccessDeniedException("Access denied to card");
        }

        return mapToResponse(card);
    }

    @Transactional
    public CardResponse blockCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException("Card not found with id: " + cardId));

        if (card.isExpired()) {
            throw new CardExpiredException("Cannot block expired card");
        }

        if (card.getStatus() == CardStatus.BLOCKED) {
            throw new CardAlreadyBlockedException("Card is already blocked");
        }

        card.setStatus(CardStatus.BLOCKED);
        card = cardRepository.save(card);
        return mapToResponse(card);
    }

    @Transactional
    public CardBlockRequestResponse requestCardBlock(Long cardId, Long userId, String reason) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException("Card not found with id: " + cardId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        if (!card.getOwner().getId().equals(userId)) {
            throw new CardNotOwnedException("Card does not belong to user");
        }

        if (!card.isActive()) {
            throw new CardAlreadyBlockedException("Card is not active (blocked or expired)");
        }

        Optional<CardBlockRequest> existingRequest = cardBlockRequestRepository
                .findPendingRequestForCard(cardId);

        if (existingRequest.isPresent()) {
            throw new PendingBlockRequestExistsException("There is already a pending block request for this card");
        }

        CardBlockRequest blockRequest = CardBlockRequest.builder()
                .card(card)
                .requestedBy(user)
                .reason(reason)
                .status(BlockRequestStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .build();

        blockRequest = cardBlockRequestRepository.save(blockRequest);
        return mapToBlockRequestResponse(blockRequest);
    }

    @Transactional
    public CardBlockRequestResponse approveBlockRequest(Long requestId, String adminComment, Long adminUserId) {
        CardBlockRequest blockRequest = cardBlockRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Block request not found with id: " + requestId));

        if (blockRequest.getStatus() != BlockRequestStatus.PENDING) {
            throw new IllegalArgumentException("Block request is not pending");
        }

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!adminUser.getRoles().contains(Role.ROLE_ADMIN)) {
            throw new AccessDeniedException("Only admin can approve block requests");
        }

        Card card = blockRequest.getCard();
        if (!card.isActive()) {
            throw new IllegalArgumentException("Card is not active (expired or already blocked)");
        }

        card.setStatus(CardStatus.BLOCKED);
        cardRepository.save(card);

        blockRequest.setStatus(BlockRequestStatus.APPROVED);
        blockRequest.setProcessedBy(adminUser);
        blockRequest.setAdminComment(adminComment);
        blockRequest.setProcessedAt(LocalDateTime.now());
        blockRequest = cardBlockRequestRepository.save(blockRequest);

        return mapToBlockRequestResponse(blockRequest);
    }


    @Transactional
    public CardBlockRequestResponse rejectBlockRequest(Long requestId, String adminComment, Long adminUserId) {
        CardBlockRequest blockRequest = cardBlockRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Block request not found with id: " + requestId));

        if (blockRequest.getStatus() != BlockRequestStatus.PENDING) {
            throw new IllegalArgumentException("Block request is not pending");
        }

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!adminUser.getRoles().contains(Role.ROLE_ADMIN)) {
            throw new AccessDeniedException("Only admin can reject block requests");
        }

        blockRequest.setStatus(BlockRequestStatus.REJECTED);
        blockRequest.setProcessedBy(adminUser);
        blockRequest.setAdminComment(adminComment);
        blockRequest.setProcessedAt(LocalDateTime.now());
        blockRequest = cardBlockRequestRepository.save(blockRequest);

        return mapToBlockRequestResponse(blockRequest);
    }

    @Transactional
    public CardBlockRequestResponse cancelBlockRequest(Long requestId, Long userId) {
        CardBlockRequest blockRequest = cardBlockRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Block request not found with id: " + requestId));

        if (!blockRequest.getRequestedBy().getId().equals(userId)) {
            throw new CardNotOwnedException("Only the requester can cancel the request");
        }

        if (blockRequest.getStatus() != BlockRequestStatus.PENDING) {
            throw new IllegalArgumentException("Only pending requests can be cancelled");
        }

        blockRequest.setStatus(BlockRequestStatus.CANCELLED);
        blockRequest.setProcessedAt(LocalDateTime.now());
        blockRequest = cardBlockRequestRepository.save(blockRequest);

        return mapToBlockRequestResponse(blockRequest);
    }

    @Transactional(readOnly = true)
    public Page<CardBlockRequestResponse> getAllBlockRequests(Pageable pageable) {
        return cardBlockRequestRepository.findAll(pageable)
                .map(this::mapToBlockRequestResponse);
    }

    @Transactional(readOnly = true)
    public Page<CardBlockRequestResponse> getBlockRequestsByStatus(BlockRequestStatus status, Pageable pageable) {
        return cardBlockRequestRepository.findByStatus(status, pageable)
                .map(this::mapToBlockRequestResponse);
    }

    @Transactional(readOnly = true)
    public Page<CardBlockRequestResponse> getUserBlockRequests(Long userId, Pageable pageable) {
        return cardBlockRequestRepository.findByRequestedBy_Id(userId, pageable)
                .map(this::mapToBlockRequestResponse);
    }

    @Transactional
    public CardResponse activateCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException("Card not found with id: " + cardId));

        if (card.isExpired()) {
            throw new CardExpiredException("Cannot activate expired card");
        }

        if (card.getStatus() == CardStatus.ACTIVE) {
            throw new IllegalArgumentException("Card is already active");
        }

        card.setStatus(CardStatus.ACTIVE);
        card = cardRepository.save(card);

        return mapToResponse(card);
    }

    @Transactional
    public void deleteCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException("Card not found with id: " + cardId));

        if (card.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Cannot delete card with positive balance");
        }

        Optional<CardBlockRequest> pendingRequest = cardBlockRequestRepository
                .findPendingRequestForCard(cardId);
        if (pendingRequest.isPresent()) {
            throw new IllegalArgumentException("Cannot delete card with pending block request");
        }

        cardRepository.delete(card);
    }

    @Transactional(readOnly = true)
    public Page<CardResponse> searchUserCards(Long userId, String query, Pageable pageable) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        Long id = null;
        try {
            id = Long.parseLong(query);
        } catch (NumberFormatException e) {
            // Не является числом, оставляем null
        }

        return cardRepository.findByOwnerAndSearch(owner, query, id, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public boolean isCardOwner(Long cardId, Long userId) {
        return cardRepository.findOwnerIdById(cardId)
                .map(ownerId -> ownerId.equals(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCardBalance(Long cardId, Long userId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException("Card not found with id: " + cardId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        boolean isOwner = card.getOwner().getId().equals(userId);
        boolean isAdmin = user.getRoles().contains(Role.ROLE_ADMIN);

        if (!isOwner && !isAdmin) {
            throw new CardAccessDeniedException("Access denied to card");
        }

        return card.getBalance();
    }

    @Transactional(readOnly = true)
    public Page<CardResponse> filterUserCards(Long userId, CardStatus status, Pageable pageable) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        if (status == null) {
            return cardRepository.findByOwner(owner, pageable)
                    .map(this::mapToResponse);
        }

        return cardRepository.findByOwnerAndStatus(owner, status, pageable)
                .map(this::mapToResponse);
    }

    private CardResponse mapToResponse(Card card) {
        return CardResponse.builder()
                .id(card.getId())
                .maskedCardNumber(cardNumberMasker.maskCardNumber(card.getCardNumberLastFour()))
                .cardHolderName(card.getCardHolderName())
                .expirationDate(card.getExpirationDate())
                .status(card.getActualStatus())
                .balance(card.getBalance())
                .ownerId(card.getOwner().getId())
                .ownerName(card.getOwner().getFullName())
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }

    private CardBlockRequestResponse mapToBlockRequestResponse(CardBlockRequest blockRequest) {
        return CardBlockRequestResponse.builder()
                .id(blockRequest.getId())
                .cardId(blockRequest.getCard().getId())
                .maskedCardNumber(cardNumberMasker.maskCardNumber(blockRequest.getCard().getCardNumberLastFour()))
                .requestedById(blockRequest.getRequestedBy().getId())
                .requestedByUsername(blockRequest.getRequestedBy().getUsername())
                .reason(blockRequest.getReason())
                .status(blockRequest.getStatus())
                .processedById(blockRequest.getProcessedBy() != null ? blockRequest.getProcessedBy().getId() : null)
                .processedByUsername(blockRequest.getProcessedBy() != null ? blockRequest.getProcessedBy().getUsername() : null)
                .adminComment(blockRequest.getAdminComment())
                .requestedAt(blockRequest.getRequestedAt())
                .processedAt(blockRequest.getProcessedAt())
                .build();
    }
}