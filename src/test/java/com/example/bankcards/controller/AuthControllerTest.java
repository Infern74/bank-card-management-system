package com.example.bankcards.controller;

import com.example.bankcards.config.TestSecurityConfig;
import com.example.bankcards.dto.JwtResponse;
import com.example.bankcards.dto.LoginRequest;
import com.example.bankcards.dto.RegisterRequest;
import com.example.bankcards.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void login_Success() throws Exception {
        LoginRequest request = new LoginRequest("testuser", "password");
        JwtResponse response = new JwtResponse(
                "token",
                "Bearer",
                1L,
                "testuser",
                "test@bank.com",
                Set.of("ROLE_USER")
        );

        when(authService.authenticateUser(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));

        verify(authService).authenticateUser(any(LoginRequest.class));
    }

    @Test
    void login_InvalidRequest_ReturnsBadRequest() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).authenticateUser(any(LoginRequest.class));
    }

    @Test
    void register_Success() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "newuser",
                "password123",
                "new@bank.com",
                "New User"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("User registered successfully"));

        verify(authService).registerUser(
                "newuser",
                "password123",
                "new@bank.com",
                "New User"
        );
    }

    @Test
    void register_InvalidEmail_ReturnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "newuser",
                "password123",
                "invalid-email",
                "New User"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).registerUser(any(), any(), any(), any());
    }
}