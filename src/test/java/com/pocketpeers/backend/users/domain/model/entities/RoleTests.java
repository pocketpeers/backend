package com.pocketpeers.backend.users.domain.model.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.pocketpeers.backend.users.domain.model.valueobjects.Roles;
import org.junit.jupiter.api.Test;

class RoleTests {

    @Test
    void defaultRoleIsUser() {
        Role role = Role.getDefaultRole();

        assertThat(role.getName()).isEqualTo(Roles.ROLE_USER);
        assertThat(role.getStringName()).isEqualTo("ROLE_USER");
    }

    @Test
    void createsRoleFromName() {
        Role role = Role.toRoleFromName("ROLE_ADMIN");

        assertThat(role.getName()).isEqualTo(Roles.ROLE_ADMIN);
    }

    @Test
    void validateRoleSetReturnsDefaultWhenMissing() {
        assertThat(Role.validateRoleSet(null))
                .extracting(Role::getName)
                .containsExactly(Roles.ROLE_USER);

        assertThat(Role.validateRoleSet(List.of()))
                .extracting(Role::getName)
                .containsExactly(Roles.ROLE_USER);
    }

    @Test
    void validateRoleSetKeepsProvidedRoles() {
        List<Role> roles = List.of(new Role(Roles.ROLE_ADMIN));

        assertThat(Role.validateRoleSet(roles)).isSameAs(roles);
    }
}
