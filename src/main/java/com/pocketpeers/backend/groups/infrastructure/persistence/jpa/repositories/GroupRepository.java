package com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findByName(String name);
    boolean existsByName(String name);
    boolean existsByNameAndIdIsNot(String name, Long id);

    @Query("SELECT g FROM Group g JOIN g.members m WHERE m.userInformation.user.id= :userId")
    List<Group> findAllByUserId(Long userId);
}
