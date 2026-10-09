package com.gepe.gepay.payment.internal.exception;

import com.gepe.gepay.platform.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PaymentError implements ErrorCode {

    INVALID_BUSINESS_DAYS(HttpStatus.BAD_REQUEST, "payment.invalid_business_days"),
    INVALID_MIDTRANS_WEBHOOK(HttpStatus.BAD_REQUEST, "payment.invalid_midtrans_webhook"),
    PROVIDER_NOT_FOUND(HttpStatus.NOT_FOUND, "payment.provider_not_found"),
    PROVIDER_NOT_REGISTERED(HttpStatus.INTERNAL_SERVER_ERROR, "payment.provider_not_registered"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "payment.payment_not_found"),
    PAYMENT_ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "payment.attempt_not_found"),
    CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "payment.channel_not_found"),
    CHANNEL_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "payment.channel_route_not_found"),
    AMOUNT_BELOW_MIN(HttpStatus.BAD_REQUEST, "payment.amount_below_min"),
    AMOUNT_ABOVE_MAX(HttpStatus.BAD_REQUEST, "payment.amount_above_max"),
    FEE_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "payment.fee_config_not_found"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "payment.internal_error"),
    DUPLICATE_IDEMPOTENCY_KEY(HttpStatus.CONFLICT, "payment.duplicate_idempotency_key"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "payment.invalid_status_transition");

    private final HttpStatus httpStatus;
    private final String messageKey;

    PaymentError(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
