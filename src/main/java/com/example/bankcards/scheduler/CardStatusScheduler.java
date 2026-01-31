package com.example.bankcards.scheduler;

import com.example.bankcards.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardStatusScheduler {

    private final CardRepository cardRepository;

    @Scheduled(cron = "0 0 0 * * *")
    public void updateExpiredCards() {
        log.info("Starting expired cards status update...");
        try {
            int updated = cardRepository.updateExpiredCards();
            log.info("Expired cards status update completed. Updated {} cards", updated);
        } catch (Exception e) {
            log.error("Error updating expired cards: {}", e.getMessage());
        }
    }
}