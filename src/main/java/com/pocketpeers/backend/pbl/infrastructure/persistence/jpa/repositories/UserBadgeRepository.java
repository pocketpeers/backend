package com.pocketpeers.backend.pbl.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.pbl.domain.model.entities.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    List<UserBadge> findAllByUser_Id(Long userId);
    long countByUser_Id(Long userId);
    boolean existsByUser_IdAndBadge_Code(Long userId, String code);
}
