package com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.pbl.domain.model.aggregates.UserReputation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserReputationRepository extends JpaRepository<UserReputation, Long> {
    Optional<UserReputation> findByUser_Id(Long userId);
}
