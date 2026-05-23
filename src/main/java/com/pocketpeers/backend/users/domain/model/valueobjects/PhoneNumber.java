package com.pocketpeers.backend.users.domain.model.valueobjects;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Embeddable
public record PhoneNumber(
        @NotBlank
        @Size(min = 9, max = 13)
        @Pattern(regexp = "^\\+?[0-9]*$", message = "Phone number must contain only digits and optionally start with a '+'")
        String number
) {
    public PhoneNumber {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be null or blank");
        }
        if (!number.matches("^\\+?[0-9]*$")) {
            throw new IllegalArgumentException("Phone number must contain only digits and optionally start with a '+'");
        }
        if (number.length() < 9 || number.length() > 13) {
            throw new IllegalArgumentException("Phone number must be between 9 and 13 characters long");
        }
    }


    public static PhoneNumber defaultPhoneNumber() {
        return new PhoneNumber("+0000000000");
    }

    public String getPhoneNumber() {
        return number;
    }
}
