package com.pocketpeers.backend.groups.domain.model.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.pocketpeers.backend.groups.domain.model.aggregates.Group;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GroupMemberTests {

    @Test
    void adminMemberReportsAdminAndAdminId() {
        User user = new User("ana", "secret");
        ReflectionTestUtils.setField(user, "id", 7L);
        GroupMember member = new GroupMember(new Group(), user, GroupRole.ADMIN);

        assertThat(member.isAdmin()).isTrue();
        assertThat(member.getIdAdmin()).isEqualTo(7L);
        assertThat(member.getJoinedAt()).isNotNull();
    }

    @Test
    void regularMemberIsNotAdmin() {
        GroupMember member = new GroupMember(new Group(), new User("ana", "secret"), GroupRole.MEMBER);

        assertThat(member.isAdmin()).isFalse();
        assertThat(member.getIdAdmin()).isNull();
    }
}
