package com.example.bankcards.service;

import com.example.bankcards.dto.CardCreateRequest;
import com.example.bankcards.entity.*;
import com.example.bankcards.exception.*;
import com.example.bankcards.repository.CardBlockRequestRepository;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.util.CardNumberMasker;
import com.example.bankcards.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EncryptionUtil encryptionUtil;

    @Mock
    private CardNumberMasker cardNumberMasker;

    @Mock
    private CardBlockRequestRepository cardBlockRequestRepository;

    @InjectMocks
    private CardService cardService;

    private User testUser;
    private Card testCard;
    private CardCreateRequest createRequest;

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .roles(new HashSet<>(Set.of(Role.ROLE_USER)))
                .build();

        testCard = Card.builder()
                .id(1L)
                .cardNumberEncrypted("encrypted")
                .cardNumberHash("hash")
                .cardNumberLastFour("1111")
                .cardHolderName("Test User")
                .expirationDate(LocalDate.now().plusYears(1))
                .status(CardStatus.ACTIVE)
                .balance(BigDecimal.valueOf(1000))
                .cvvEncrypted("encryptedCVV")
                .owner(testUser)
                .build();

        createRequest = new CardCreateRequest(
                "4111111111111111",
                "Test User",
                LocalDate.now().plusYears(1),
                "123",
                1L,
                BigDecimal.valueOf(1000)
        );

        Field maxInitialBalanceField = CardService.class.getDeclaredField("maxInitialBalance");
        maxInitialBalanceField.setAccessible(true);
        maxInitialBalanceField.set(cardService, BigDecimal.valueOf(1000000.00));
    }

    @Test
    void createCard_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        when(encryptionUtil.encrypt("4111111111111111")).thenReturn("encrypted");
        when(encryptionUtil.encrypt("123")).thenReturn("encryptedCVV");
        when(encryptionUtil.hash("4111111111111111")).thenReturn("cardHash");

        when(cardRepository.findByCardNumberHash("cardHash")).thenReturn(Optional.empty());
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> {
            Card card = invocation.getArgument(0);
            card.setId(1L);
            card.setCardNumberLastFour("1111");
            return card;
        });
        when(cardNumberMasker.maskCardNumber("1111")).thenReturn("**** **** **** 1111");

        var result = cardService.createCard(createRequest);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    void createCard_UserNotFound_ThrowsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> cardService.createCard(createRequest));
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void createCard_CardAlreadyExists_ThrowsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash("4111111111111111")).thenReturn("cardHash");
        when(cardRepository.findByCardNumberHash("cardHash")).thenReturn(Optional.of(testCard));

        assertThrows(IllegalArgumentException.class,
                () -> cardService.createCard(createRequest));
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void getUserCards_Success() {
        Pageable pageable = Pageable.ofSize(10);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cardRepository.findByOwner(testUser, pageable))
                .thenReturn(new PageImpl<>(List.of(testCard)));
        when(cardNumberMasker.maskCardNumber("1111")).thenReturn("**** **** **** 1111");

        var result = cardService.getUserCards(1L, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(testCard.getId(), result.getContent().get(0).getId());
    }

    @Test
    void blockCard_Success() {
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);
        when(cardNumberMasker.maskCardNumber("1111")).thenReturn("**** **** **** 1111");

        var result = cardService.blockCard(1L);

        assertNotNull(result);
        assertEquals(CardStatus.BLOCKED, testCard.getStatus());
        verify(cardRepository).save(testCard);
    }

    @Test
    void blockCard_AlreadyBlocked_ThrowsException() {
        testCard.setStatus(CardStatus.BLOCKED);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));

        assertThrows(CardAlreadyBlockedException.class,
                () -> cardService.blockCard(1L));
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void requestCardBlock_Success() {
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cardBlockRequestRepository.findPendingRequestForCard(1L))
                .thenReturn(Optional.empty());
        when(cardBlockRequestRepository.save(any(CardBlockRequest.class)))
                .thenAnswer(invocation -> {
                    CardBlockRequest request = invocation.getArgument(0);
                    request.setId(1L);
                    return request;
                });
        when(cardNumberMasker.maskCardNumber("1111")).thenReturn("**** **** **** 1111");

        var result = cardService.requestCardBlock(1L, 1L, "Lost card");

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Lost card", result.getReason());
        assertEquals(BlockRequestStatus.PENDING, result.getStatus());
        verify(cardBlockRequestRepository).save(any(CardBlockRequest.class));
    }

    @Test
    void requestCardBlock_CardNotOwned_ThrowsException() {
        User otherUser = User.builder().id(2L).build();
        testCard.setOwner(otherUser);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(CardNotOwnedException.class,
                () -> cardService.requestCardBlock(1L, 1L, "Lost card"));
        verify(cardBlockRequestRepository, never()).save(any(CardBlockRequest.class));
    }

    @Test
    void getCardBalance_Success() {
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        var result = cardService.getCardBalance(1L, 1L);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(1000), result);
    }

    @Test
    void getCardBalance_AdminCanAccess() {
        User admin = User.builder()
                .id(2L)
                .roles(new HashSet<>(Set.of(Role.ROLE_ADMIN)))
                .build();
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        var result = cardService.getCardBalance(1L, 2L);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(1000), result);
    }

    @Test
    void getCardBalance_AccessDenied_ThrowsException() {
        User otherUser = User.builder()
                .id(2L)
                .roles(new HashSet<>(Set.of(Role.ROLE_USER)))
                .build();
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        assertThrows(CardAccessDeniedException.class,
                () -> cardService.getCardBalance(1L, 2L));
    }

    @Test
    void deleteCard_Success() {
        testCard.setBalance(BigDecimal.ZERO);
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));
        when(cardBlockRequestRepository.findPendingRequestForCard(1L))
                .thenReturn(Optional.empty());

        cardService.deleteCard(1L);

        verify(cardRepository).delete(testCard);
    }

    @Test
    void deleteCard_WithPositiveBalance_ThrowsException() {
        testCard.setBalance(BigDecimal.valueOf(100));
        when(cardRepository.findById(1L)).thenReturn(Optional.of(testCard));

        assertThrows(IllegalArgumentException.class,
                () -> cardService.deleteCard(1L));
        verify(cardRepository, never()).delete(any(Card.class));
    }

    @Test
    void getCardById_NotOwnerAndNotAdmin_ThrowsException() {
        User otherUser = User.builder()
                .id(2L)
                .roles(new HashSet<>(Set.of(Role.ROLE_USER)))
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        when(cardRepository.findByIdWithOwner(1L)).thenReturn(Optional.of(testCard));

        assertThrows(CardAccessDeniedException.class,
                () -> cardService.getCardById(1L, 2L));
    }

    @Test
    void getCardById_Admin_Success() {
        User admin = User.builder()
                .id(3L)
                .roles(new HashSet<>(Set.of(Role.ROLE_ADMIN)))
                .build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(cardRepository.findByIdWithOwner(1L)).thenReturn(Optional.of(testCard));
        when(cardNumberMasker.maskCardNumber("1111")).thenReturn("**** **** **** 1111");

        var result = cardService.getCardById(1L, 3L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void createCard_WithExpiredDate_ThrowsException() {
        CardCreateRequest request = new CardCreateRequest(
                "4111111111111111",
                "Test User",
                LocalDate.now().minusDays(1),
                "123",
                1L,
                BigDecimal.valueOf(1000)
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash("4111111111111111")).thenReturn("cardHash");
        when(cardRepository.findByCardNumberHash("cardHash")).thenReturn(Optional.empty());

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> cardService.createCard(request));

        assertTrue(exception.getMessage().contains("Expiration date must be at least 1 month in the future") ||
                exception.getMessage().contains("expiration date") ||
                exception.getMessage().contains("future"));
    }

    @Test
    void createCard_WithDateLessThanOneMonth_ThrowsException() {
        CardCreateRequest request = new CardCreateRequest(
                "4111111111111111",
                "Test User",
                LocalDate.now().plusDays(15),
                "123",
                1L,
                BigDecimal.valueOf(1000)
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash("4111111111111111")).thenReturn("cardHash");
        when(cardRepository.findByCardNumberHash("cardHash")).thenReturn(Optional.empty());

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> cardService.createCard(request));

        assertTrue(exception.getMessage().contains("Expiration date must be at least 1 month in the future"));
    }

    @Test
    void createCard_WithValidFutureDate_Success() {
        CardCreateRequest request = new CardCreateRequest(
                "4111111111111111",
                "Test User",
                LocalDate.now().plusMonths(2),
                "123",
                1L,
                BigDecimal.valueOf(1000)
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(encryptionUtil.hash("4111111111111111")).thenReturn("cardHash");
        when(cardRepository.findByCardNumberHash("cardHash")).thenReturn(Optional.empty());
        when(encryptionUtil.encrypt("4111111111111111")).thenReturn("encrypted");
        when(encryptionUtil.encrypt("123")).thenReturn("encryptedCVV");
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> {
            Card card = invocation.getArgument(0);
            card.setId(1L);
            card.setCardNumberLastFour("1111");
            return card;
        });
        when(cardNumberMasker.maskCardNumber("1111")).thenReturn("**** **** **** 1111");

        var result = cardService.createCard(request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(cardRepository).save(any(Card.class));
    }
}