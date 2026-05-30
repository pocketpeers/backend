package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.entities.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    public Optional<Receipt> findById(Long receiptId);
}
