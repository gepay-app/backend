package com.gepe.starter.platform.security;

/**
 * Hasil verifikasi Firebase ID token. {@code name} boleh null — Firebase
 * tidak menjamin displayName tersedia untuk semua provider/metode login.
 */
public record FirebasePrincipal(String authId, String email, String name, boolean emailVerified) {
}
