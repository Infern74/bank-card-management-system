package com.example.bankcards.service;

import com.example.bankcards.dto.TransferRequest;
import com.example.bankcards.entity.*;
import com.example.bankcards.exception.CardNotOwnedException;
import com.example.bankcards.exception.InsufficientFundsException;
import com.example.bankcards.exception.TransferCancellationException;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.TransferRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.util.CardNumberMasker;
import com.example.bankcards.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardNumberMasker cardNumberMasker;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EncryptionUtil encryptionUtil;

    @InjectMocks
    private TransferService transferService;

    private User testUser;
    private Card fromCard;
    private Card toCard;
    private TransferRequest transferRequest;

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .build();

        fromCard = Card.builder()
                .id(1L)
                .cardNumberHash("hash1")
                .cardNumberLastFour("1111")
                .owner(testUser)
                .balance(BigDecimal.valueOf(1000))
                .status(CardStatus.ACTIVE)
                .expirationDate(java.time.LocalDate.now().plusYears(1))
                .build();

        toCard = Card.builder()
                .id(2L)
                .cardNumberHash("hash2")
                .cardNumberLastFour("2222")
                .owner(testUser)
                .balance(BigDecimal.valueOf(500))
                .status(CardStatus.ACTIVE)
                .expirationDate(java.time.LocalDate.now().plusYears(1))
                .build();

        transferRequest = new TransferRequest(
                "4111111111111111",
                "4222222222222222",
                BigDecimal.valueOf(100),
                "Test transfer"
        );

        Field maxTransferAmountField = TransferService.class.getDeclaredField("maxTransferAmount");
        maxTransferAmountField.setAccessible(true);
        maxTransferAmountField.set(transferService, BigDecimal.valueOf(1000000.00));
    }

    @Test
    void transferBetweenOwnCards_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash1");
        when(encryptionUtil.hash("4222222222222222")).thenReturn("hash2");

        when(cardRepository.findByCardNumberHash("hash1")).thenReturn(Optional.of(fromCard));
        when(cardRepository.findByCardNumberHash("hash2")).thenReturn(Optional.of(toCard));

        when(cardRepository.save(any(Card.class))).thenReturn(fromCard, toCard);
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer transfer = invocation.getArgument(0);
            transfer.setId(1L);
            return transfer;
        });
        when(cardNumberMasker.maskCardNumber("1111")).thenReturn("**** **** **** 1111");
        when(cardNumberMasker.maskCardNumber("2222")).thenReturn("**** **** **** 2222");

        var result = transferService.transferBetweenOwnCards(transferRequest, 1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(BigDecimal.valueOf(100), result.getAmount());
        assertEquals(TransferStatus.COMPLETED, result.getStatus());
        assertEquals(BigDecimal.valueOf(900), fromCard.getBalance());
        assertEquals(BigDecimal.valueOf(600), toCard.getBalance());
        verify(cardRepository, times(2)).save(any(Card.class));
        verify(transferRepository).save(any(Transfer.class));
    }

    @Test
    void transferBetweenOwnCards_InsufficientFunds_ThrowsException() {
        transferRequest.setAmount(BigDecimal.valueOf(2000));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash1");
        when(encryptionUtil.hash("4222222222222222")).thenReturn("hash2");

        when(cardRepository.findByCardNumberHash("hash1")).thenReturn(Optional.of(fromCard));
        when(cardRepository.findByCardNumberHash("hash2")).thenReturn(Optional.of(toCard));

        assertThrows(InsufficientFundsException.class,
                () -> transferService.transferBetweenOwnCards(transferRequest, 1L));
        verify(cardRepository, never()).save(any(Card.class));
        verify(transferRepository, never()).save(any(Transfer.class));
    }

    @Test
    void transferBetweenOwnCards_CardNotOwned_ThrowsException() {
        User otherUser = User.builder().id(2L).build();
        toCard.setOwner(otherUser);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash1");
        when(encryptionUtil.hash("4222222222222222")).thenReturn("hash2");

        when(cardRepository.findByCardNumberHash("hash1")).thenReturn(Optional.of(fromCard));
        when(cardRepository.findByCardNumberHash("hash2")).thenReturn(Optional.of(toCard));

        assertThrows(CardNotOwnedException.class,
                () -> transferService.transferBetweenOwnCards(transferRequest, 1L));
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void transferBetweenOwnCards_SameCard_ThrowsException() {
        TransferRequest sameCardRequest = new TransferRequest(
                "4111111111111111",
                "4111111111111111",
                BigDecimal.valueOf(100),
                "Test transfer"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash1");

        when(cardRepository.findByCardNumberHash("hash1")).thenReturn(Optional.of(fromCard));

        assertThrows(IllegalArgumentException.class,
                () -> transferService.transferBetweenOwnCards(sameCardRequest, 1L));
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void transferBetweenOwnCards_CardExpired_ThrowsException() {
        fromCard.setExpirationDate(java.time.LocalDate.now().minusDays(1));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash1");
        when(encryptionUtil.hash("4222222222222222")).thenReturn("hash2");

        when(cardRepository.findByCardNumberHash("hash1")).thenReturn(Optional.of(fromCard));
        when(cardRepository.findByCardNumberHash("hash2")).thenReturn(Optional.of(toCard));

        Exception exception = assertThrows(RuntimeException.class,
                () -> transferService.transferBetweenOwnCards(transferRequest, 1L));

        assertTrue(exception.getMessage().contains("not active"));
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void cancelTransfer_Success() {
        Transfer transfer = Transfer.builder()
                .id(1L)
                .fromCard(fromCard)
                .toCard(toCard)
                .amount(BigDecimal.valueOf(100))
                .status(TransferStatus.COMPLETED)
                .transferDate(LocalDateTime.now().minusHours(12))
                .build();

        when(transferRepository.findById(1L)).thenReturn(Optional.of(transfer));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cardRepository.save(any(Card.class))).thenReturn(fromCard, toCard);

        transferService.cancelTransfer(1L, 1L);

        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());
        assertEquals(BigDecimal.valueOf(1100), fromCard.getBalance());
        assertEquals(BigDecimal.valueOf(400), toCard.getBalance());
        verify(cardRepository, times(2)).save(any(Card.class));
        verify(transferRepository).save(transfer);
    }

    @Test
    void cancelTransfer_TransferTooOld_ThrowsException() {
        Transfer transfer = Transfer.builder()
                .id(1L)
                .fromCard(fromCard)
                .toCard(toCard)
                .amount(BigDecimal.valueOf(100))
                .status(TransferStatus.COMPLETED)
                .transferDate(LocalDateTime.now().minusDays(2))
                .build();

        when(transferRepository.findById(1L)).thenReturn(Optional.of(transfer));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(TransferCancellationException.class,
                () -> transferService.cancelTransfer(1L, 1L));
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void cancelTransfer_InsufficientFundsToCancel_ThrowsException() {
        toCard.setBalance(BigDecimal.valueOf(50));
        Transfer transfer = Transfer.builder()
                .id(1L)
                .fromCard(fromCard)
                .toCard(toCard)
                .amount(BigDecimal.valueOf(100))
                .status(TransferStatus.COMPLETED)
                .transferDate(LocalDateTime.now().minusHours(12))
                .build();

        when(transferRepository.findById(1L)).thenReturn(Optional.of(transfer));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(InsufficientFundsException.class,
                () -> transferService.cancelTransfer(1L, 1L));
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void transferExceedsMaxAmount_ShouldFail() {
        fromCard.setBalance(BigDecimal.valueOf(2000000));

        TransferRequest request = new TransferRequest(
                "4111111111111111",
                "4222222222222222",
                BigDecimal.valueOf(1500000),
                "Large transfer"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash1");
        when(encryptionUtil.hash("4222222222222222")).thenReturn("hash2");
        when(cardRepository.findByCardNumberHash("hash1")).thenReturn(Optional.of(fromCard));
        when(cardRepository.findByCardNumberHash("hash2")).thenReturn(Optional.of(toCard));

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> transferService.transferBetweenOwnCards(request, 1L));

        assertTrue(exception.getMessage().contains("Maximum transfer amount exceeded"));
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void transferWithNegativeAmount_ShouldFail() {
        TransferRequest request = new TransferRequest(
                "4111111111111111",
                "4222222222222222",
                BigDecimal.valueOf(-100),
                "Negative transfer"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash1");
        when(encryptionUtil.hash("4222222222222222")).thenReturn("hash2");
        when(cardRepository.findByCardNumberHash("hash1")).thenReturn(Optional.of(fromCard));
        when(cardRepository.findByCardNumberHash("hash2")).thenReturn(Optional.of(toCard));

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> transferService.transferBetweenOwnCards(request, 1L));

        assertTrue(exception.getMessage().contains("Amount must be positive"));
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void transferWithZeroAmount_ShouldFail() {
        TransferRequest request = new TransferRequest(
                "4111111111111111",
                "4222222222222222",
                BigDecimal.ZERO,
                "Zero transfer"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash1");
        when(encryptionUtil.hash("4222222222222222")).thenReturn("hash2");
        when(cardRepository.findByCardNumberHash("hash1")).thenReturn(Optional.of(fromCard));
        when(cardRepository.findByCardNumberHash("hash2")).thenReturn(Optional.of(toCard));

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> transferService.transferBetweenOwnCards(request, 1L));

        assertTrue(exception.getMessage().contains("Amount must be positive") ||
                exception.getMessage().contains("Amount must be greater than 0"));
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void transferFromExpiredCard_ShouldFail() {
        fromCard.setExpirationDate(java.time.LocalDate.now().minusDays(1));
        TransferRequest request = new TransferRequest(
                "4111111111111111",
                "4222222222222222",
                BigDecimal.valueOf(100),
                "Transfer from expired card"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash1");
        when(encryptionUtil.hash("4222222222222222")).thenReturn("hash2");
        when(cardRepository.findByCardNumberHash("hash1")).thenReturn(Optional.of(fromCard));
        when(cardRepository.findByCardNumberHash("hash2")).thenReturn(Optional.of(toCard));

        Exception exception = assertThrows(RuntimeException.class,
                () -> transferService.transferBetweenOwnCards(request, 1L));

        assertTrue(exception.getMessage().contains("not active") ||
                exception.getMessage().contains("expired"));
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void transferToExpiredCard_ShouldFail() {
        toCard.setExpirationDate(java.time.LocalDate.now().minusDays(1));

        TransferRequest request = new TransferRequest(
                "4111111111111111",
                "4222222222222222",
                BigDecimal.valueOf(100),
                "Transfer to expired card"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash1");
        when(encryptionUtil.hash("4222222222222222")).thenReturn("hash2");
        when(cardRepository.findByCardNumberHash("hash1")).thenReturn(Optional.of(fromCard));
        when(cardRepository.findByCardNumberHash("hash2")).thenReturn(Optional.of(toCard));

        Exception exception = assertThrows(RuntimeException.class,
                () -> transferService.transferBetweenOwnCards(request, 1L));

        assertTrue(exception.getMessage().contains("not active") ||
                exception.getMessage().contains("expired"));
        verify(cardRepository, never()).save(any(Card.class));
    }
}