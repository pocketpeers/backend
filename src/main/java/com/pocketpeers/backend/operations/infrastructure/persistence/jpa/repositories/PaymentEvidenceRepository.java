package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.entities.PaymentEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentEvidenceRepository extends JpaRepository<PaymentEvidence, Long> {
}
