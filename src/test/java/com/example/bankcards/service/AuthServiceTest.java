package com.example.bankcards.service;

import com.example.bankcards.dto.LoginRequest;
import com.example.bankcards.entity.Role;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.EmailAlreadyExistsException;
import com.example.bankcards.exception.UsernameAlreadyExistsException;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("encodedPassword")
                .email("test@bank.com")
                .fullName("Test User")
                .roles(new HashSet<>(Set.of(Role.ROLE_USER)))
                .build();

        loginRequest = new LoginRequest("testuser", "password");
    }

    @Test
    void authenticateUser_Success() {
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
        when(jwtUtils.generateJwtToken(authentication)).thenReturn("jwtToken");

        var result = authService.authenticateUser(loginRequest);

        assertNotNull(result);
        assertEquals("jwtToken", result.getToken());
        assertEquals("Bearer", result.getType());
        assertEquals(1L, result.getId());
        assertEquals("testuser", result.getUsername());
        assertEquals("test@bank.com", result.getEmail());
        assertTrue(result.getRoles().contains("ROLE_USER"));
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void registerUser_Success() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@bank.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = authService.registerUser("newuser", "password123", "new@bank.com", "New User");

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        assertEquals("encodedPassword", result.getPassword());
        assertEquals("new@bank.com", result.getEmail());
        assertEquals("New User", result.getFullName());
        assertTrue(result.getRoles().contains(Role.ROLE_USER));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUser_UsernameAlreadyExists_ThrowsException() {
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThrows(UsernameAlreadyExistsException.class,
                () -> authService.registerUser("existinguser", "password123", "new@bank.com", "New User"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_EmailAlreadyExists_ThrowsException() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@bank.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class,
                () -> authService.registerUser("newuser", "password123", "existing@bank.com", "New User"));
        verify(userRepository, never()).save(any(User.class));
    }
}