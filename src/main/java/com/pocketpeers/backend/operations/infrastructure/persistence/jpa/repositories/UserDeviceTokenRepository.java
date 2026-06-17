package com.pocketpeers.backend.operations.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.operations.domain.model.entities.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, Long> {
    Optional<UserDeviceToken> findByToken(String token);

    List<UserDeviceToken> findByUser_Id(Long userId);

    void deleteByToken(String token);
}
