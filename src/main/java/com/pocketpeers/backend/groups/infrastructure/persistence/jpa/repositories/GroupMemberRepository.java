package com.pocketpeers.backend.groups.infrastructure.persistence.jpa.repositories;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import com.pocketpeers.backend.users.domain.model.aggregates.UserInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    Optional<GroupMember> findByGroupIdAndUser_Id(Long groupId, Long userId);
    List<GroupMember> findAllByGroupId(Long groupId);
    @Query("SELECT gm FROM GroupMember gm JOIN FETCH gm.user u LEFT JOIN FETCH u.userInformation WHERE gm.group.id = :groupId")
    List<GroupMember> findAllMembersByGroupId(Long groupId);
    List<GroupMember> findAllByUser_Id(Long userId);
    boolean existsGroupMemberByGroupAndUser(Group group, User user);
    boolean existsByGroupIdAndUser_IdAndRole(Long groupId, Long userId, GroupRole role);
    void deleteByGroupId(Long groupId);
}
