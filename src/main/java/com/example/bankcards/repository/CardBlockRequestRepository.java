package com.example.bankcards.repository;

import com.example.bankcards.entity.BlockRequestStatus;
import com.example.bankcards.entity.CardBlockRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CardBlockRequestRepository extends JpaRepository<CardBlockRequest, Long> {

    Page<CardBlockRequest> findByStatus(BlockRequestStatus status, Pageable pageable);

    Page<CardBlockRequest> findByRequestedBy_Id(Long userId, Pageable pageable);

    @Query("SELECT r FROM CardBlockRequest r WHERE r.card.id = :cardId AND r.status = 'PENDING'")
    Optional<CardBlockRequest> findPendingRequestForCard(@Param("cardId") Long cardId);
}