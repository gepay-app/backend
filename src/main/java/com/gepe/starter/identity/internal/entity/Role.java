package com.gepe.starter.identity.internal.entity;

/**
 * Role internal (entity layer). Sengaja terpisah dari
 * {@code identity.api.dtos.Role} supaya {@code api} tidak pernah
 * mereferensikan tipe {@code internal} (lihat {@code agents.md} §2.1).
 *
 * <p>Semua role bersifat global — tidak ada branch-scoping.
 */
public enum Role {
    SUPER_ADMIN,
    ADMIN,
    CREATOR,
    USER
}
