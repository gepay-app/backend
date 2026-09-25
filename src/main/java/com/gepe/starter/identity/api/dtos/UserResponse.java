package com.gepe.starter.identity.api.dtos;

import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        UserStatus status,
        Set<Role> roles
) {
}
