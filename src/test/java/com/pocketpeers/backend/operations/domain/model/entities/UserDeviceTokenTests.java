package com.pocketpeers.backend.operations.domain.model.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.pocketpeers.backend.users.domain.model.aggregates.User;
import org.junit.jupiter.api.Test;

class UserDeviceTokenTests {

    @Test
    void storesTokenAndCanUpdateOwnerPlatform() {
        User originalOwner = new User("ana", "secret");
        User newOwner = new User("luis", "secret");
        UserDeviceToken deviceToken = new UserDeviceToken(originalOwner, "token-1", "android");

        deviceToken.updateOwner(newOwner, "ios");

        assertThat(deviceToken.getToken()).isEqualTo("token-1");
        assertThat(deviceToken.getUser()).isSameAs(newOwner);
        assertThat(deviceToken.getPlatform()).isEqualTo("ios");
    }
}
