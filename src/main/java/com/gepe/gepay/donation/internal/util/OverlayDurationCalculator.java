package com.gepe.gepay.donation.internal.util;

import com.gepe.gepay.donation.internal.config.DonationOverlayProperties;
import com.gepe.gepay.donation.internal.entity.DonationType;
import org.springframework.stereotype.Component;

/**
 * Menghitung durasi tampil overlay (detik) sesuai aturan {@code todo.md} §3.3.
 *
 * <ul>
 *   <li>YOUTUBE: {@code max(min, min(amount / ratePerSecond, max))}.</li>
 *   <li>TEXT: {@code clamp(ceil(chars / charsPerSecond), min, max)}.</li>
 * </ul>
 */
@Component
public class OverlayDurationCalculator {

    private final DonationOverlayProperties properties;

    public OverlayDurationCalculator(DonationOverlayProperties properties) {
        this.properties = properties;
    }

    public int youtubeSeconds(long amount) {
        var y = properties.getYoutube();
        long rate = y.getRatePerSecond() > 0 ? y.getRatePerSecond() : 1;
        long requested = amount / rate;
        long capped = Math.min(requested, y.getMaxSeconds());
        return (int) Math.max(capped, y.getMinSeconds());
    }

    public int textSeconds(int characterCount) {
        var t = properties.getText();
        int cps = t.getCharsPerSecond() > 0 ? t.getCharsPerSecond() : 1;
        int raw = (int) Math.ceil((double) characterCount / cps);
        return Math.max(t.getMinSeconds(), Math.min(raw, t.getMaxSeconds()));
    }

    public int durationSeconds(DonationType type, long amount, int characterCount) {
        return type == DonationType.YOUTUBE ? youtubeSeconds(amount) : textSeconds(characterCount);
    }
}
