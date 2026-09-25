package com.gepe.starter.identity.internal.exception;

import com.gepe.starter.platform.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum IdentityError implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "identity.user.not_found"),
    USER_DISABLED(HttpStatus.FORBIDDEN, "identity.user.disabled"),
    USER_ALREADY_LINKED(HttpStatus.CONFLICT, "identity.user.already_linked"),

    ROLE_ALREADY_GRANTED(HttpStatus.CONFLICT, "identity.role.already_granted"),
    ROLE_NOT_GRANTED(HttpStatus.NOT_FOUND, "identity.role.not_granted"),
    ROLE_CHANGE_ON_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "identity.role.change_on_self_not_allowed");

    private final HttpStatus httpStatus;
    private final String messageKey;

    IdentityError(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
