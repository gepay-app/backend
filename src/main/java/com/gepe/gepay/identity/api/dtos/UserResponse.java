package com.gepe.gepay.identity.api.dtos;

import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String authId,
        String name,
        String email,
        UserStatus status,
        Set<Role> roles
) {
}
