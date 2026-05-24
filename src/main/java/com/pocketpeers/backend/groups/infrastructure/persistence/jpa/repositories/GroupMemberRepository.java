package com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;
import com.pocketpeers.backend.users.domain.model.aggregates.UserInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    Optional<GroupMember> findByGroupIdAndUserInformationId(Long groupId, Long userInformationId);
    List<GroupMember> findAllByGroupId(Long groupId);
    List<GroupMember> findAllByUserInformationId(Long userInformationId);
    boolean existsByGroupAndUserInformation(Group group, UserInformation userInformation);
    void deleteByGroupId(Long groupId);
}
