package com.pocketpeers.backend.groups.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.pocketpeers.backend.groups.domain.model.commands.CreateGroupCommand;
import com.pocketpeers.backend.groups.domain.model.entities.GroupMember;
import com.pocketpeers.backend.groups.domain.model.valueobjects.GroupRole;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import org.junit.jupiter.api.Test;

class GroupTests {

    @Test
    void defaultConstructorStartsWithEmptyValues() {
        Group group = new Group();

        assertThat(group.getName()).isEmpty();
        assertThat(group.getDescription()).isEmpty();
        assertThat(group.getGroupPhoto()).isEmpty();
        assertThat(group.getInvitationToken()).isEmpty();
        assertThat(group.getMembers()).isEmpty();
    }

    @Test
    void commandConstructorCopiesFields() {
        Group group = new Group(new CreateGroupCommand("Viaje", "photo.png", "Gastos del viaje", 1L));

        assertThat(group.getName()).isEqualTo("Viaje");
        assertThat(group.getGroupPhoto()).isEqualTo("photo.png");
        assertThat(group.getDescription()).isEqualTo("Gastos del viaje");
    }

    @Test
    void updateInformationAndPhotoMutateGroup() {
        Group group = new Group("Casa", "Gastos casa", "old.png");

        group.updateInformation("Depa", "Gastos compartidos");
        group.changePhoto("new.png");

        assertThat(group.getName()).isEqualTo("Depa");
        assertThat(group.getDescription()).isEqualTo("Gastos compartidos");
        assertThat(group.getGroupPhoto()).isEqualTo("new.png");
    }

    @Test
    void generateInvitationTokenCreatesValidToken() {
        Group group = new Group();

        group.generateInvitationToken();

        assertThat(group.getInvitationToken()).isNotBlank();
        assertThat(group.hasValidInvitationToken(group.getInvitationToken())).isTrue();
        assertThat(group.hasValidInvitationToken("otro-token")).isFalse();
    }

    @Test
    void addMemberLinksMemberToGroup() {
        Group group = new Group("Casa", "Gastos casa", "photo.png");
        GroupMember member = new GroupMember(null, new User("ana", "secret"), GroupRole.MEMBER);

        group.addMember(member);

        assertThat(group.getMembers()).containsExactly(member);
        assertThat(member.getGroup()).isSameAs(group);
    }
}
