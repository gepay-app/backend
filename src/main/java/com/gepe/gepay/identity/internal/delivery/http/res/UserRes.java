package com.gepe.gepay.identity.internal.delivery.http.res;

import com.gepe.gepay.identity.api.dtos.Role;
import com.gepe.gepay.identity.api.dtos.UserResponse;
import com.gepe.gepay.identity.api.dtos.UserStatus;

import java.util.Set;
import java.util.UUID;

/** View user (HTTP response, mis. hasil grant/revoke role; admin-only). */
public record UserRes(
        UUID id,
        String authId,
        String name,
        String email,
        UserStatus status,
        Set<Role> roles
) {

    public static UserRes from(UserResponse u) {
        return new UserRes(u.id(), u.authId(), u.name(), u.email(), u.status(), u.roles());
    }
}
