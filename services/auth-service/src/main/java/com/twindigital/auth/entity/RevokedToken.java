package com.twindigital.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "td_revoked_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RevokedToken {

    @Id
    @Column(length = 64)
    private String jti;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private OffsetDateTime revokedAt;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @PrePersist
    void prePersist() {
        revokedAt = OffsetDateTime.now();
    }
}
