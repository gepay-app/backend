package com.gepe.gepay.donation.internal.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Pembuat {@code slug} default (username publik) untuk halaman donasi baru:
 * 10 karakter lowercase base36. Creator bisa menggantinya lewat update page.
 */
@Component
public class SlugGenerator {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
