package com.gepe.starter.identity.api;

import com.gepe.starter.identity.api.dtos.GrantRoleCommand;
import com.gepe.starter.identity.api.dtos.UserPrincipal;
import com.gepe.starter.identity.api.dtos.UserResponse;

import java.util.UUID;

public interface IdentityApi {

    /**
     * Resolve principal by Firebase UID. {@code null} kalau belum pernah link
     * ATAU user DISABLED. Di-cache (TTL pendek) — dipanggil tiap request oleh
     * enrichment filter.
     */
    UserPrincipal resolveByAuthId(String authId);

    /**
     * Auto-provision saat user login pertama kali:
     * <ul>
     *   <li>cari by {@code authId} — sudah pernah link, kembalikan;</li>
     *   <li>kalau tidak, cari by {@code email}: baris pre-seeded (mis.
     *       SUPER_ADMIN/creator) di-link tanpa mengubah role-nya;</li>
     *   <li>kalau email belum dikenal, buat user baru + role {@code USER}.</li>
     * </ul>
     * Mengembalikan {@code null} (→ 401) kalau email tidak ada, belum
     * terverifikasi, atau user DISABLED. Wajib {@code emailVerified} supaya
     * email orang lain tidak bisa diklaim.
     */
    UserPrincipal provisionOnFirstLogin(String authId, String email, String name, boolean emailVerified);

    /** Grant role ke user (by email). Hanya dipanggil SUPER_ADMIN. */
    UserResponse grantRole(GrantRoleCommand cmd, UUID grantedBy);

    /** Revoke role dari user (by email). Hanya dipanggil SUPER_ADMIN. */
    UserResponse revokeRole(GrantRoleCommand cmd, UUID actorId);
}
