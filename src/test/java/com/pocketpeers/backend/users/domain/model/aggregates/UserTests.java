package com.pocketpeers.backend.users.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.pocketpeers.backend.users.domain.model.entities.Role;
import com.pocketpeers.backend.users.domain.model.valueobjects.Roles;
import org.junit.jupiter.api.Test;

class UserTests {

    @Test
    void defaultConstructorInitializesRoles() {
        User user = new User();

        assertThat(user.getRoles()).isEmpty();
    }

    @Test
    void constructorStoresCredentialsAndDefaultRoleWhenRolesMissing() {
        User user = new User("ana", "secret", null);

        assertThat(user.getUsername()).isEqualTo("ana");
        assertThat(user.getPassword()).isEqualTo("secret");
        assertThat(user.getRoles()).extracting(Role::getName).containsExactly(Roles.ROLE_USER);
    }

    @Test
    void addRoleAndAddRolesAreChainable() {
        User user = new User("ana", "secret");
        Role admin = new Role(Roles.ROLE_ADMIN);

        assertThat(user.addRole(admin)).isSameAs(user);
        assertThat(user.addRoles(List.of(new Role(Roles.ROLE_USER)))).isSameAs(user);
        assertThat(user.getRoles()).extracting(Role::getName)
                .containsExactlyInAnyOrder(Roles.ROLE_ADMIN, Roles.ROLE_USER);
    }
}
