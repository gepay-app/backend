package com.gepe.gepay.payment.internal.exception;

import com.gepe.gepay.platform.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PaymentError implements ErrorCode {

    INVALID_BUSINESS_DAYS(HttpStatus.BAD_REQUEST, "payment.invalid_business_days");

    private final HttpStatus httpStatus;
    private final String messageKey;

    PaymentError(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
