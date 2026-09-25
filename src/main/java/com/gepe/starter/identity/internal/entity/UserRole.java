package com.gepe.starter.identity.internal.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Grant satu role ke satu user. Tanpa branch — relasi murni user↔role.
 * Unik per {@code (user_id, role)} supaya grant bersifat idempotent di level DB.
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "user_roles", schema = "identity")
public class UserRole {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    /** Null = self (auto-provision) atau system; terisi saat admin yang grant. */
    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    public static UserRole grant(UUID userId, Role role, UUID grantedBy) {
        UserRole r = new UserRole();
        r.id = UuidCreator.getTimeOrderedEpoch();
        r.userId = userId;
        r.role = role;
        r.grantedBy = grantedBy;
        r.grantedAt = Instant.now();
        return r;
    }
}
