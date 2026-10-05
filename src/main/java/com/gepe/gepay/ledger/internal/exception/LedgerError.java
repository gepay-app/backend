package com.gepe.gepay.ledger.internal.exception;

import com.gepe.gepay.platform.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum LedgerError implements ErrorCode {
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ledger.account-not-found"),
    UNBALANCED_JOURNAL(HttpStatus.BAD_REQUEST, "ledger.unbalanced-journal"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "ledger.idempotency-conflict"),
    JOURNAL_MIN_LINES(HttpStatus.BAD_REQUEST, "ledger.journal-min-lines"),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "ledger.invalid-amount"),
    REVERSES_JOURNAL_NOT_FOUND(HttpStatus.NOT_FOUND, "ledger.reverses-journal-not-found"),
    JOURNAL_ALREADY_REVERSED(HttpStatus.CONFLICT, "ledger.journal-already-reversed"),
    ACCOUNT_INACTIVE(HttpStatus.CONFLICT, "ledger.account-inactive"),
    CONCURRENT_BALANCE_UPDATE(HttpStatus.CONFLICT, "ledger.concurrent-balance-update"),
    ACCOUNT_CODE_INVALID(HttpStatus.BAD_REQUEST, "ledger.account-code-invalid"),
    OWNER_REF_REQUIRED(HttpStatus.BAD_REQUEST, "ledger.owner-ref-required");
    private final HttpStatus httpStatus;
    private final String messageKey;

    LedgerError(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
