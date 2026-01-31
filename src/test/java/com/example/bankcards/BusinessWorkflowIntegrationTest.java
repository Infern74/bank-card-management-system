package com.example.bankcards;

import com.example.bankcards.dto.*;
import com.example.bankcards.entity.*;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.scheduler.CardStatusScheduler;
import com.example.bankcards.service.CardService;
import com.example.bankcards.service.TransferService;
import com.example.bankcards.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BusinessWorkflowIntegrationTest {

    @Autowired
    private CardService cardService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private CardStatusScheduler cardStatusScheduler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private EncryptionUtil encryptionUtil;

    private User testUser;
    private User adminUser;
    private Card card1;
    private Card card2;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("workflowuser")
                .password("password")
                .email("workflow@bank.com")
                .fullName("Workflow User")
                .roles(new HashSet<>(Set.of(Role.ROLE_USER)))
                .active(true)
                .build();
        testUser = userRepository.save(testUser);

        adminUser = User.builder()
                .username("workflowadmin")
                .password("password")
                .email("workflowadmin@bank.com")
                .fullName("Workflow Admin")
                .roles(new HashSet<>(Set.of(Role.ROLE_ADMIN)))
                .active(true)
                .build();
        adminUser = userRepository.save(adminUser);
    }

    private void createTestCards() {
        CardCreateRequest card1Request = new CardCreateRequest(
                "5555444433332222",
                "Workflow User",
                LocalDate.now().plusYears(1),
                "123",
                testUser.getId(),
                BigDecimal.valueOf(1000)
        );

        CardCreateRequest card2Request = new CardCreateRequest(
                "6666555544443333",
                "Workflow User",
                LocalDate.now().plusYears(1),
                "456",
                testUser.getId(),
                BigDecimal.valueOf(500)
        );

        CardResponse card1Response = cardService.createCard(card1Request);
        CardResponse card2Response = cardService.createCard(card2Request);

        card1 = cardRepository.findById(card1Response.getId()).orElseThrow();
        card2 = cardRepository.findById(card2Response.getId()).orElseThrow();
    }

    @Test
    void fullBlockRequestWorkflow_ShouldPreventTransfers() {
        createTestCards();

        CardBlockRequestCreateRequest blockRequest = new CardBlockRequestCreateRequest(
                card1.getId(),
                "Lost card"
        );

        CardBlockRequestResponse blockResponse = cardService.requestCardBlock(
                blockRequest.getCardId(),
                testUser.getId(),
                blockRequest.getReason()
        );

        assertEquals(BlockRequestStatus.PENDING, blockResponse.getStatus());

        CardBlockRequestResponse approvedResponse = cardService.approveBlockRequest(
                blockResponse.getId(),
                "Card reported lost",
                adminUser.getId()
        );

        assertEquals(BlockRequestStatus.APPROVED, approvedResponse.getStatus());

        CardResponse cardAfterBlock = cardService.getCardById(card1.getId(), testUser.getId());
        assertEquals(CardStatus.BLOCKED, cardAfterBlock.getStatus());

        TransferRequest transferRequest = new TransferRequest(
                "5555444433332222",
                "6666555544443333",
                BigDecimal.valueOf(100),
                "Test transfer"
        );

        Exception exception = assertThrows(RuntimeException.class, () -> {
            transferService.transferBetweenOwnCards(transferRequest, testUser.getId());
        });

        assertTrue(exception.getMessage().contains("not active"));
    }

    @Test
    void cardStatusScheduler_ShouldUpdateExpiredCards() {
        CardCreateRequest validCardRequest = new CardCreateRequest(
                "7777666655554444",
                "Expired User",
                LocalDate.now().plusMonths(2),
                "789",
                testUser.getId(),
                BigDecimal.valueOf(100)
        );

        CardResponse cardResponse = cardService.createCard(validCardRequest);
        Card card = cardRepository.findById(cardResponse.getId()).orElseThrow();

        assertEquals(CardStatus.ACTIVE, card.getStatus());

        card.setExpirationDate(LocalDate.now().minusDays(1));
        cardRepository.save(card);

        cardStatusScheduler.updateExpiredCards();

        Card updatedCard = cardRepository.findById(card.getId()).orElseThrow();
        assertEquals(CardStatus.EXPIRED, updatedCard.getActualStatus());
    }

    @Test
    void transferCancellationWorkflow_ShouldRefundMoney() {
        createTestCards();

        TransferRequest transferRequest = new TransferRequest(
                "5555444433332222",
                "6666555544443333",
                BigDecimal.valueOf(200),
                "Test transfer for cancellation"
        );

        TransferResponse transferResponse = transferService.transferBetweenOwnCards(
                transferRequest,
                testUser.getId()
        );

        assertEquals(TransferStatus.COMPLETED, transferResponse.getStatus());

        BigDecimal card1BalanceAfterTransfer = cardService.getCardBalance(card1.getId(), testUser.getId());
        BigDecimal card2BalanceAfterTransfer = cardService.getCardBalance(card2.getId(), testUser.getId());

        assertEquals(BigDecimal.valueOf(800), card1BalanceAfterTransfer);
        assertEquals(BigDecimal.valueOf(700), card2BalanceAfterTransfer);

        transferService.cancelTransfer(transferResponse.getId(), testUser.getId());

        BigDecimal card1BalanceAfterCancel = cardService.getCardBalance(card1.getId(), testUser.getId());
        BigDecimal card2BalanceAfterCancel = cardService.getCardBalance(card2.getId(), testUser.getId());

        assertEquals(BigDecimal.valueOf(1000), card1BalanceAfterCancel);
        assertEquals(BigDecimal.valueOf(500), card2BalanceAfterCancel);
    }
}