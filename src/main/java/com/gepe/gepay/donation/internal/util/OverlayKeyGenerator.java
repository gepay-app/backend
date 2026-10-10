package com.gepe.gepay.donation.internal.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Pembuat {@code overlay_key} rahasia: string acak base62 panjang 43 karakter
 * (cukup untuk kolom {@code varchar(64)}).
 */
@Component
public class OverlayKeyGenerator {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int LENGTH = 43;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
