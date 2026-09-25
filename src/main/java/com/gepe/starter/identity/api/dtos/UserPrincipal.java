package com.gepe.starter.identity.api.dtos;

import java.util.Set;
import java.util.UUID;

/**
 * Principal aplikasi (hasil resolve token Firebase). Value cache — HARUS
 * {@code record}. {@code name} boleh null (Firebase tidak menjaminnya).
 */
public record UserPrincipal(
        UUID userId,
        String email,
        String name,
        Set<Role> roles,
        UserStatus status
) {
    public UserPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
