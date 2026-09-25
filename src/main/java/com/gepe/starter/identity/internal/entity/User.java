package com.gepe.starter.identity.internal.entity;

import com.gepe.starter.identity.internal.exception.IdentityError;
import com.gepe.starter.platform.exception.ServiceException;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * User aplikasi. Dibuat lewat auto-provision saat login pertama
 * ({@code provisionOnFirstLogin}) atau lewat seeder/grant role.
 *
 * <p>{@code authId} = UID Firebase. {@code null} selama user belum pernah
 * login; begitu di-link nilainya tidak pernah berubah.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "users", schema = "identity")
public class User {

    public enum Status { ACTIVE, DISABLED }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "auth_id", unique = true, length = 128)
    private String authId;

    @Column(nullable = false, length = 320, columnDefinition = "citext")
    private String email;

    /** Nullable: Firebase tidak menjamin displayName tersedia. */
    @Column(length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static User create(String email, String name) {
        User u = new User();
        u.id = UuidCreator.getTimeOrderedEpoch();
        u.email = email.toLowerCase(Locale.ROOT);
        u.name = normalizeName(name);
        u.status = Status.ACTIVE;
        u.createdAt = Instant.now();
        u.updatedAt = u.createdAt;
        return u;
    }

    /**
     * Link akun Firebase ke user ini. Idempotent untuk authId yang sama, dan
     * menolak authId berbeda supaya baris pre-seeded (mis. SUPER_ADMIN) tidak
     * bisa "dibajak" oleh UID Firebase lain.
     */
    public void linkAuthId(String authId) {
        if (this.authId != null && !this.authId.equals(authId)) {
            throw new ServiceException(IdentityError.USER_ALREADY_LINKED);
        }
        this.authId = authId;
        this.updatedAt = Instant.now();
    }

    public void updateProfile(String name) {
        this.name = normalizeName(name);
        this.updatedAt = Instant.now();
    }

    public void disable() {
        this.status = Status.DISABLED;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.status = Status.ACTIVE;
        this.updatedAt = Instant.now();
    }

    private static String normalizeName(String name) {
        return (name == null || name.isBlank()) ? null : name.trim();
    }
}
