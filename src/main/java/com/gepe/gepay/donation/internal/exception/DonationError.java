package com.gepe.gepay.donation.internal.exception;

import com.gepe.gepay.platform.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Error non-field modul donation. Aturan validasi field/kombinasi field
 * <strong>tidak</strong> di sini — pakai {@code ValidationException} + message
 * key {@code donation.*} (lihat {@code todo.md} §3.5/§9).
 */
@Getter
public enum DonationError implements ErrorCode {

    PAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "donation.page_not_found"),
    DONATION_NOT_FOUND(HttpStatus.NOT_FOUND, "donation.not_found"),
    OVERLAY_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "donation.overlay_event_not_found"),
    OVERLAY_NOT_OWNED(HttpStatus.FORBIDDEN, "donation.overlay_not_owned"),
    DONATION_ALREADY_PAID(HttpStatus.CONFLICT, "donation.already_paid"),
    INVALID_OVERLAY_COMMAND(HttpStatus.BAD_REQUEST, "donation.invalid_overlay_command");

    private final HttpStatus httpStatus;
    private final String messageKey;

    DonationError(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
