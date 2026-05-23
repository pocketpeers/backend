package com.pocketpeers.backend.users.domain.model.valueobjects;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;

@Embeddable
public record Photo(
        @NotBlank(message = "Photo cannot be null or blank")
        String photo
) {
    public Photo {
        if (photo == null || photo.isBlank()) {
            throw new IllegalArgumentException("Photo cannot be null or blank");
        }
    }

    /**
     * Factory method to create an instance with a default photo if necessary.
     * @return a Photo instance with a default value.
     */
    public static Photo defaultPhoto() {
        return new Photo("default_photo_string");
    }

    /**
     * Returns the photo string.
     * @return the photo string.
     */
    public String getPhoto() {
        return photo;
    }
}
