package com.gepe.starter.identity.api.dtos;

/** Grant atau revoke satu role, ditargetkan lewat email user. */
public record GrantRoleCommand(String email, Role role) {
}
