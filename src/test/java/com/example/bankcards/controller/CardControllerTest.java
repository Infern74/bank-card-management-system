package com.example.bankcards.controller;

import com.example.bankcards.config.TestSecurityConfig;
import com.example.bankcards.dto.CardCreateRequest;
import com.example.bankcards.dto.CardResponse;
import com.example.bankcards.entity.CardStatus;
import com.example.bankcards.entity.Role;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.CardAccessDeniedException;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.security.JwtUtils;
import com.example.bankcards.service.CardService;
import com.example.bankcards.service.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CardController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class CardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CardService cardService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    private CardResponse createTestCardResponse() {
        return CardResponse.builder()
                .id(1L)
                .maskedCardNumber("**** **** **** 1111")
                .cardHolderName("Test User")
                .expirationDate(LocalDate.now().plusYears(1))
                .status(CardStatus.ACTIVE)
                .balance(BigDecimal.valueOf(1000))
                .ownerId(1L)
                .ownerName("Test User")
                .build();
    }

    @BeforeEach
    void setUp() {
        User mockUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("password")
                .email("test@bank.com")
                .fullName("Test User")
                .roles(Set.of(Role.ROLE_USER))
                .build();

        when(userRepository.findByUsername(anyString()))
                .thenReturn(Optional.of(mockUser));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCard_Admin_Success() throws Exception {
        CardCreateRequest request = new CardCreateRequest(
                "4111111111111111",
                "Test User",
                LocalDate.now().plusYears(1),
                "123",
                1L,
                BigDecimal.valueOf(1000)
        );

        CardResponse response = createTestCardResponse();
        when(cardService.createCard(any(CardCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.maskedCardNumber").value("**** **** **** 1111"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(cardService).createCard(any(CardCreateRequest.class));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createCard_User_AccessDenied() throws Exception {
        CardCreateRequest request = new CardCreateRequest(
                "4111111111111111",
                "Test User",
                LocalDate.now().plusYears(1),
                "123",
                1L,
                BigDecimal.valueOf(1000)
        );

        mockMvc.perform(post("/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(cardService, never()).createCard(any(CardCreateRequest.class));
    }

    @Test
    @WithMockUser(username = "user1")
    void getUserCards_Success() throws Exception {
        Page<CardResponse> page = new PageImpl<>(List.of(createTestCardResponse()));
        when(cardService.getUserCards(anyLong(), any())).thenReturn(page);

        mockMvc.perform(get("/cards/my")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].maskedCardNumber").value("**** **** **** 1111"));

        verify(cardService).getUserCards(anyLong(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllCards_Admin_Success() throws Exception {
        Page<CardResponse> page = new PageImpl<>(List.of(createTestCardResponse()));
        when(cardService.getAllCards(any())).thenReturn(page);

        mockMvc.perform(get("/cards")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L));

        verify(cardService).getAllCards(any());
    }

    @Test
    @WithMockUser(username = "user1")
    void getCardBalance_Success() throws Exception {
        when(cardService.getCardBalance(eq(1L), anyLong())).thenReturn(BigDecimal.valueOf(1000));

        mockMvc.perform(get("/cards/1/balance"))
                .andExpect(status().isOk())
                .andExpect(content().string("1000"));

        verify(cardService).getCardBalance(eq(1L), anyLong());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void blockCard_Admin_Success() throws Exception {
        CardResponse response = createTestCardResponse();
        response.setStatus(CardStatus.BLOCKED);
        when(cardService.blockCard(1L)).thenReturn(response);

        mockMvc.perform(put("/cards/1/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        verify(cardService).blockCard(1L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void blockCard_User_AccessDenied() throws Exception {
        mockMvc.perform(put("/cards/1/block"))
                .andExpect(status().isForbidden());

        verify(cardService, never()).blockCard(anyLong());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCard_Admin_Success() throws Exception {
        doNothing().when(cardService).deleteCard(1L);

        mockMvc.perform(delete("/cards/1"))
                .andExpect(status().isNoContent());

        verify(cardService).deleteCard(1L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteCard_User_AccessDenied() throws Exception {
        mockMvc.perform(delete("/cards/1"))
                .andExpect(status().isForbidden());

        verify(cardService, never()).deleteCard(anyLong());
    }

    @Test
    @WithMockUser(username = "user1")
    void searchUserCards_Success() throws Exception {
        Page<CardResponse> page = new PageImpl<>(List.of(createTestCardResponse()));
        when(cardService.searchUserCards(anyLong(), anyString(), any())).thenReturn(page);

        mockMvc.perform(get("/cards/my/search")
                        .param("query", "1111")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].maskedCardNumber").value("**** **** **** 1111"));

        verify(cardService).searchUserCards(anyLong(), eq("1111"), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateCard_Admin_Success() throws Exception {
        CardResponse response = createTestCardResponse();
        response.setStatus(CardStatus.ACTIVE);
        when(cardService.activateCard(1L)).thenReturn(response);

        mockMvc.perform(put("/cards/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(cardService).activateCard(1L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void activateCard_User_AccessDenied() throws Exception {
        mockMvc.perform(put("/cards/1/activate"))
                .andExpect(status().isForbidden());

        verify(cardService, never()).activateCard(anyLong());
    }

    @Test
    @WithMockUser(username = "user2")
    void getCardById_NotOwner_ReturnsForbidden() throws Exception {
        User otherUser = User.builder()
                .id(2L)
                .username("user2")
                .password("password")
                .email("user2@bank.com")
                .fullName("User Two")
                .roles(Set.of(Role.ROLE_USER))
                .build();
        when(userRepository.findByUsername("user2")).thenReturn(Optional.of(otherUser));

        when(cardService.getCardById(eq(1L), eq(2L)))
                .thenThrow(new CardAccessDeniedException("Access denied to card"));

        mockMvc.perform(get("/cards/1"))
                .andExpect(status().isForbidden());
    }
}