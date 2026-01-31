package com.example.bankcards.repository;

import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.CardStatus;
import com.example.bankcards.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByCardNumberHash(String cardNumberHash);

    Page<Card> findByOwner(User owner, Pageable pageable);

    @Query("SELECT c FROM Card c WHERE c.owner = :owner AND " +
            "(LOWER(c.cardHolderName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "c.cardNumberLastFour LIKE CONCAT('%', :search, '%') OR " +
            "c.id = :id)")
    Page<Card> findByOwnerAndSearch(@Param("owner") User owner,
                                    @Param("search") String search,
                                    @Param("id") Long id,
                                    Pageable pageable);

    @Modifying
    @Query("UPDATE Card c SET c.status = 'EXPIRED' WHERE c.expirationDate < CURRENT_DATE AND c.status != 'EXPIRED'")
    @Transactional
    int updateExpiredCards();

    Page<Card> findByOwnerAndStatus(User owner, CardStatus status, Pageable pageable);

    @Query("SELECT c.owner.id FROM Card c WHERE c.id = :cardId")
    Optional<Long> findOwnerIdById(@Param("cardId") Long cardId);

    @Query("SELECT c FROM Card c JOIN FETCH c.owner WHERE c.id = :cardId")
    Optional<Card> findByIdWithOwner(@Param("cardId") Long cardId);
}