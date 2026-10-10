package com.gepe.gepay.identity.internal.delivery.http.res;

import com.gepe.gepay.identity.api.dtos.Role;
import com.gepe.gepay.identity.api.dtos.UserPrincipal;
import com.gepe.gepay.identity.api.dtos.UserStatus;

import java.util.Set;
import java.util.UUID;

/** View principal user yang sedang login (HTTP response untuk {@code GET /me}). */
public record MeRes(
        UUID userId,
        String email,
        String name,
        Set<Role> roles,
        UserStatus status
) {

    public static MeRes from(UserPrincipal p) {
        return new MeRes(p.userId(), p.email(), p.name(), p.roles(), p.status());
    }
}
