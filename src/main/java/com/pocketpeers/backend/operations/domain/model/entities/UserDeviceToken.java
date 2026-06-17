package com.pocketpeers.backend.operations.domain.model.entities;

import com.pocketpeers.backend.shared.domain.model.entities.AuditableModel;
import com.pocketpeers.backend.users.domain.model.aggregates.User;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(
        name = "user_device_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_device_token",
                columnNames = {"token"}
        )
)
public class UserDeviceToken extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 512)
    private String token;

    private String platform;

    public UserDeviceToken() {
    }

    public UserDeviceToken(User user, String token, String platform) {
        this.user = user;
        this.token = token;
        this.platform = platform;
    }

    public void updateOwner(User user, String platform) {
        this.user = user;
        this.platform = platform;
    }
}
