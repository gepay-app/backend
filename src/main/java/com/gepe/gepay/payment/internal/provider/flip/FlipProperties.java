package com.gepe.gepay.payment.internal.provider.flip;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kredensial & endpoint Flip, di-bind dari prefix {@code pg.flip} (lihat
 * {@code application.yaml}). Diregistrasi sebagai bean lewat {@link FlipConfig}.
 */
@Validated
@ConfigurationProperties(prefix = "pg.flip")
public record FlipProperties(
        @NotBlank String secretKey,
        @NotBlank String validationKey,
        @NotBlank String url
) {
}
