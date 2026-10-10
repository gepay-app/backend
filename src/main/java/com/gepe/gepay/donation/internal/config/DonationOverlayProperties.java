package com.gepe.gepay.donation.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfigurasi overlay donation ({@code donation.overlay.*}). Ada default di kode
 * supaya aman bila nilai yaml tidak lengkap.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "donation.overlay")
public class DonationOverlayProperties {

    private Youtube youtube = new Youtube();
    private Text text = new Text();

    private int ackTimeoutSeconds = 60;
    private boolean autoRequeueOnTimeout = true;
    private int presenceTtlSeconds = 30;
    private String watchdogCron = "0/30 * * * * ?";

    @Getter
    @Setter
    public static class Youtube {
        /** Rupiah per detik yang "dibeli" donasi. */
        private long ratePerSecond = 500;
        private int minSeconds = 10;
        private int maxSeconds = 1800;
    }

    @Getter
    @Setter
    public static class Text {
        private int maxCharacters = 350;
        /** Kecepatan baca manusia (karakter per detik) untuk formula durasi. */
        private int charsPerSecond = 12;
        private int minSeconds = 10;
        private int maxSeconds = 60;
    }
}
