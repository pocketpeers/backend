package com.pocketpeers.backend.users.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.pocketpeers.backend.users.domain.model.commands.CreateUserInformationCommand;
import org.junit.jupiter.api.Test;

class UserInformationTests {

    @Test
    void constructorBuildsEmbeddedValues() {
        User user = new User("ana", "secret");
        UserInformation info = new UserInformation("Ana", "Lopez", "+51987654321", "photo.png", "ana@test.com", user);

        assertThat(info.getFullName()).isEqualTo("Ana Lopez");
        assertThat(info.getPhoneNumber()).isEqualTo("+51987654321");
        assertThat(info.getPhoto()).isEqualTo("photo.png");
        assertThat(info.getEmailAddress()).isEqualTo("ana@test.com");
        assertThat(info.getUser()).isSameAs(user);
    }

    @Test
    void commandConstructorCreatesInformationAndUserPlaceholder() {
        CreateUserInformationCommand command = new CreateUserInformationCommand(
                "Ana",
                "Lopez",
                "+51987654321",
                "photo.png",
                "ana@test.com",
                9L
        );

        UserInformation info = new UserInformation(command);

        assertThat(info.getFullName()).isEqualTo("Ana Lopez");
        assertThat(info.getUser()).isNotNull();
    }

    @Test
    void updateMethodsReplaceEmbeddedValues() {
        UserInformation info = new UserInformation("Ana", "Lopez", "+51987654321", "photo.png", "ana@test.com", new User());

        info.updateName("Luis", "Perez");
        info.updatePhoneNumber("+51911111111");
        info.updatePhoto("new.png");
        info.updateEmail("luis@test.com");

        assertThat(info.getFullName()).isEqualTo("Luis Perez");
        assertThat(info.getPhoneNumber()).isEqualTo("+51911111111");
        assertThat(info.getPhoto()).isEqualTo("new.png");
        assertThat(info.getEmailAddress()).isEqualTo("luis@test.com");
    }
}
