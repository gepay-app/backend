package com.gepe.gepay.payment.internal.provider.midtrans;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kredensial & endpoint Midtrans, di-bind dari prefix {@code pg.midtrans}
 * (lihat {@code application.yaml}). Diregistrasi sebagai bean lewat
 * {@link MidtransConfig}.
 */
@Validated
@ConfigurationProperties(prefix = "pg.midtrans")
public record MidtransProperties(
        @NotBlank String merchantId,
        @NotBlank String clientKey,
        @NotBlank String serverKey,
        @NotBlank String url
) {
}
