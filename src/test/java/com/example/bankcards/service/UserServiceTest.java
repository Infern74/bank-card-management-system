package com.example.bankcards.service;

import com.example.bankcards.dto.UserResponse;
import com.example.bankcards.entity.Role;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.UserNotFoundException;
import com.example.bankcards.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@bank.com")
                .fullName("Test User")
                .active(true)
                .roles(Set.of(Role.ROLE_USER))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        adminUser = User.builder()
                .id(2L)
                .username("admin")
                .email("admin@bank.com")
                .fullName("Admin User")
                .active(true)
                .roles(Set.of(Role.ROLE_ADMIN))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getAllUsers_ShouldReturnPageOfUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        List<User> users = List.of(testUser, adminUser);
        Page<User> userPage = new PageImpl<>(users, pageable, users.size());

        when(userRepository.findAll(pageable)).thenReturn(userPage);

        Page<UserResponse> result = userService.getAllUsers(pageable);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals("testuser", result.getContent().get(0).getUsername());
        assertEquals("admin", result.getContent().get(1).getUsername());
        verify(userRepository).findAll(pageable);
    }

    @Test
    void getAllUsers_WithEmptyResult_ShouldReturnEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> emptyPage = Page.empty();

        when(userRepository.findAll(pageable)).thenReturn(emptyPage);

        Page<UserResponse> result = userService.getAllUsers(pageable);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userRepository).findAll(pageable);
    }

    @Test
    void blockUser_ShouldSetActiveToFalse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        userService.blockUser(1L);

        assertFalse(testUser.isActive());
        verify(userRepository).findById(1L);
        verify(userRepository).save(testUser);
    }

    @Test
    void blockUser_UserNotFound_ShouldThrowException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.blockUser(999L));
        verify(userRepository).findById(999L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void activateUser_ShouldSetActiveToTrue() {
        testUser.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        userService.activateUser(1L);

        assertTrue(testUser.isActive());
        verify(userRepository).findById(1L);
        verify(userRepository).save(testUser);
    }

    @Test
    void activateUser_UserNotFound_ShouldThrowException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.activateUser(999L));
        verify(userRepository).findById(999L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void activateUser_AlreadyActive_ShouldRemainActive() {
        testUser.setActive(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        userService.activateUser(1L);

        assertTrue(testUser.isActive());
        verify(userRepository).findById(1L);
        verify(userRepository).save(testUser);
    }
}