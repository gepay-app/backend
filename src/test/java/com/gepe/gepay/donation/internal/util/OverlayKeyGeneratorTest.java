package com.gepe.gepay.donation.internal.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OverlayKeyGeneratorTest {

    private final OverlayKeyGenerator generator = new OverlayKeyGenerator();

    @Test
    void generatesUrlSafeKeysOfFixedLength() {
        String key = generator.generate();
        assertThat(key).hasSize(43).matches("[0-9A-Za-z]+");
    }

    @Test
    void generatesUniqueKeys() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            keys.add(generator.generate());
        }
        assertThat(keys).hasSize(1_000);
    }
}
