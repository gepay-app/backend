package com.gepe.gepay.payment.internal.config;

/**
 * Value object (record) penampung nama consumer unik per instance aplikasi.
 * Penggunaan record bertipe khusus ini mencegah bentrokan (conflict) dengan
 * bean {@link String} lain di Spring ApplicationContext.
 */
public record WebhookConsumerName(String value) {
}
