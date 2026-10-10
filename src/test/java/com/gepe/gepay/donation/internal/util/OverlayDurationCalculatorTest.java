package com.gepe.gepay.donation.internal.util;

import com.gepe.gepay.donation.internal.config.DonationOverlayProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OverlayDurationCalculatorTest {

    private final OverlayDurationCalculator calculator =
            new OverlayDurationCalculator(new DonationOverlayProperties());

    @Test
    void youtubeDurationUsesRateWithFloorAndCap() {
        assertThat(calculator.youtubeSeconds(1_000)).isEqualTo(10);   // 2s -> floor 10
        assertThat(calculator.youtubeSeconds(5_000)).isEqualTo(10);   // 10s
        assertThat(calculator.youtubeSeconds(100_000)).isEqualTo(200);
        assertThat(calculator.youtubeSeconds(900_000)).isEqualTo(1800); // exactly cap
        assertThat(calculator.youtubeSeconds(1_000_000)).isEqualTo(1800); // above cap
    }

    @Test
    void textDurationFollowsReadabilityFormulaWithBounds() {
        assertThat(calculator.textSeconds(0)).isEqualTo(10);
        assertThat(calculator.textSeconds(12)).isEqualTo(10);   // ceil(1) -> floor 10
        assertThat(calculator.textSeconds(120)).isEqualTo(10);  // ceil(10) -> floor 10
        assertThat(calculator.textSeconds(350)).isEqualTo(30);  // ceil(29.2) -> 30
        assertThat(calculator.textSeconds(1_000)).isEqualTo(60); // cap
    }
}
