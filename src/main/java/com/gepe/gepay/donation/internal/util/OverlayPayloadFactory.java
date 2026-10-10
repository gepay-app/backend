package com.gepe.gepay.donation.internal.util;

import com.gepe.gepay.donation.internal.entity.Donation;
import com.gepe.gepay.donation.internal.entity.OverlayEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Menyusun payload overlay yang dikirim ke display (bentuk di {@code todo.md} §7.2).
 * Nama donor disanitasi di sini (anonim -> "Anonymous").
 */
public final class OverlayPayloadFactory {

    private OverlayPayloadFactory() {
    }

    public static Map<String, Object> build(OverlayEvent event, Donation donation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("overlayEventId", event.getId().toString());
        payload.put("donationId", donation.getId().toString());
        payload.put("type", donation.getType().name());
        payload.put("donorName", displayName(donation));
        payload.put("isAnonymous", donation.isAnonymous());
        payload.put("amount", donation.getAmount());
        payload.put("durationSeconds", event.getDurationSeconds());
        payload.put("message", donation.getMessage());
        payload.put("videoId", donation.getVideoId());
        payload.put("canonicalUrl", donation.getVideoId() == null
                ? null
                : "https://www.youtube.com/watch?v=" + donation.getVideoId());
        return payload;
    }

    private static String displayName(Donation donation) {
        if (donation.isAnonymous() || donation.getDonorName() == null) {
            return "Anonymous";
        }
        return donation.getDonorName();
    }
}
