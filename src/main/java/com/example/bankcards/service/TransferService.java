package com.example.bankcards.service;

import com.example.bankcards.dto.TransferRequest;
import com.example.bankcards.dto.TransferResponse;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.Transfer;
import com.example.bankcards.entity.TransferStatus;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.*;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.TransferRepository;
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
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final CardRepository cardRepository;
    private final CardNumberMasker cardNumberMasker;
    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;

    @Value("${app.transfer.max-amount:1000000.00}")
    private BigDecimal maxTransferAmount;

    @Transactional
    public TransferResponse transferBetweenOwnCards(TransferRequest request, Long userId) {
        User currentUser = getCurrentUser(userId);

        Card fromCard = findCardByNumber(request.getFromCardNumber(), currentUser);
        Card toCard = findCardByNumber(request.getToCardNumber(), currentUser);

        if (!fromCard.getOwner().getId().equals(toCard.getOwner().getId())) {
            throw new IllegalArgumentException("Both cards must belong to the same user");
        }

        validateTransfer(fromCard, toCard, request.getAmount());

        fromCard.setBalance(fromCard.getBalance().subtract(request.getAmount()));
        toCard.setBalance(toCard.getBalance().add(request.getAmount()));

        cardRepository.save(fromCard);
        cardRepository.save(toCard);

        Transfer transfer = Transfer.builder()
                .fromCard(fromCard)
                .toCard(toCard)
                .amount(request.getAmount())
                .description(request.getDescription())
                .status(TransferStatus.COMPLETED)
                .build();

        transfer = transferRepository.save(transfer);

        return mapToResponse(transfer);
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> getUserTransfers(Long userId, Pageable pageable) {
        User user = getCurrentUser(userId);

        return transferRepository.findByUser(user, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransferById(Long transferId, Long userId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found with id: " + transferId));

        User currentUser = getCurrentUser(userId);

        if (!transfer.getFromCard().getOwner().getId().equals(currentUser.getId()) &&
                !transfer.getToCard().getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Access denied");
        }

        return mapToResponse(transfer);
    }

    @Transactional
    public void cancelTransfer(Long transferId, Long userId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found with id: " + transferId));

        User currentUser = getCurrentUser(userId);

        if (!transfer.getFromCard().getOwner().getId().equals(currentUser.getId())) {
            throw new TransferCancellationException("Only transfer initiator can cancel transfer");
        }

        if (transfer.getTransferDate().isBefore(LocalDateTime.now().minusHours(24))) {
            throw new TransferCancellationException("Transfer cannot be cancelled after 24 hours");
        }

        if (transfer.getStatus() != TransferStatus.COMPLETED) {
            throw new TransferCancellationException("Only completed transfers can be cancelled");
        }

        Card fromCard = transfer.getFromCard();
        Card toCard = transfer.getToCard();

        if (toCard.getBalance().compareTo(transfer.getAmount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds on destination card to cancel transfer");
        }

        fromCard.setBalance(fromCard.getBalance().add(transfer.getAmount()));
        toCard.setBalance(toCard.getBalance().subtract(transfer.getAmount()));

        cardRepository.save(fromCard);
        cardRepository.save(toCard);

        transfer.setStatus(TransferStatus.CANCELLED);
        transferRepository.save(transfer);
    }

    private Card findCardByNumber(String cardNumber, User owner) {
        String cardHash = encryptionUtil.hash(cardNumber);

        Card card = cardRepository.findByCardNumberHash(cardHash)
                .orElseThrow(() -> new CardNotFoundException("Card not found"));

        if (!card.getOwner().getId().equals(owner.getId())) {
            throw new CardNotOwnedException("Card does not belong to user");
        }

        return card;
    }

    private void validateTransfer(Card fromCard, Card toCard, BigDecimal amount) {
        if (!fromCard.isActive()) {
            throw new RuntimeException("Source card is not active");
        }

        if (!toCard.isActive()) {
            throw new RuntimeException("Destination card is not active");
        }

        if (fromCard.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (amount.compareTo(maxTransferAmount) > 0) {
            throw new IllegalArgumentException("Maximum transfer amount exceeded: " + maxTransferAmount);
        }

        if (fromCard.getId().equals(toCard.getId())) {
            throw new IllegalArgumentException("Cannot transfer to the same card");
        }
    }

    private User getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private TransferResponse mapToResponse(Transfer transfer) {
        return TransferResponse.builder()
                .id(transfer.getId())
                .fromCardMasked(cardNumberMasker.maskCardNumber(transfer.getFromCard().getCardNumberLastFour()))
                .toCardMasked(cardNumberMasker.maskCardNumber(transfer.getToCard().getCardNumberLastFour()))
                .amount(transfer.getAmount())
                .description(transfer.getDescription())
                .status(transfer.getStatus())
                .transferDate(transfer.getTransferDate())
                .build();
    }
}