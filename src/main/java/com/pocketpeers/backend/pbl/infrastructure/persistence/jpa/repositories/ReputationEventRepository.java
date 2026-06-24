package com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.pbl.domain.model.entities.ReputationEvent;
import com.pocketpeers.backend.pbl.domain.model.valueobjects.ReputationEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReputationEventRepository extends JpaRepository<ReputationEvent, Long> {
    List<ReputationEvent> findAllByUser_IdAndOccurredAtAfterOrderByOccurredAtAsc(Long userId, LocalDateTime from);
    List<ReputationEvent> findAllByUser_IdAndOccurredAtAfterOrderByOccurredAtDesc(Long userId, LocalDateTime from);
    boolean existsByPaymentIdAndType(Long paymentId, ReputationEventType type);
}
