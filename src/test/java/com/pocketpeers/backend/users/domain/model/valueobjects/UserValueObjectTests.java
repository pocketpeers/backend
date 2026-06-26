package com.pocketpeers.backend.users.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserValueObjectTests {

    @Test
    void personNameBuildsFullNameAndRejectsBlankParts() {
        assertThat(new PersonName("Ana", "Lopez").getFullName()).isEqualTo("Ana Lopez");

        assertThatThrownBy(() -> new PersonName("", "Lopez"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("First name cannot be null or blank");

        assertThatThrownBy(() -> new PersonName("Ana", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Last name cannot be null or blank");
    }

    @Test
    void phoneNumberAcceptsValidValuesAndRejectsInvalidValues() {
        assertThat(new PhoneNumber("+51987654321").getPhoneNumber()).isEqualTo("+51987654321");
        assertThat(PhoneNumber.defaultPhoneNumber().getPhoneNumber()).isEqualTo("+0000000000");

        assertThatThrownBy(() -> new PhoneNumber(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Phone number cannot be null or blank");

        assertThatThrownBy(() -> new PhoneNumber("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Phone number must contain only digits and optionally start with a '+'");

        assertThatThrownBy(() -> new PhoneNumber("123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Phone number must be between 9 and 13 characters long");
    }

    @Test
    void photoAndEmailKeepProvidedValues() {
        assertThat(new Photo("photo.png").getPhoto()).isEqualTo("photo.png");
        assertThat(Photo.defaultPhoto().getPhoto()).isEqualTo("default_photo_string");
        assertThat(new EmailAddress("ana@test.com").email()).isEqualTo("ana@test.com");
    }
}
