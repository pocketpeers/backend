package com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.pbl.domain.model.entities.BadgeCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BadgeCatalogRepository extends JpaRepository<BadgeCatalog, Long> {
    Optional<BadgeCatalog> findByCode(String code);
    boolean existsByCode(String code);
}
