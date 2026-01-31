package com.example.bankcards;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class IntegrationTest {

    @Test
    void contextLoads() {
        // Тест проверяет, что контекст Spring успешно поднимается
        assertThat(true).isTrue();
    }
}