package com.gepe.starter.identity.internal.delivery.http.req;

import com.gepe.starter.identity.api.dtos.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GrantRoleReq(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull Role role
) {
}
