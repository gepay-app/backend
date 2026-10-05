package com.gepe.gepay.ledger.internal.exception;

import com.gepe.gepay.platform.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum LedgerError implements ErrorCode {
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ledger.account_not_found"),
    UNBALANCED_JOURNAL(HttpStatus.BAD_REQUEST, "ledger.unbalanced_journal"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "ledger.idempotency_conflict"),
    JOURNAL_MIN_LINES(HttpStatus.BAD_REQUEST, "ledger.journal_min_lines"),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "ledger.invalid_amount"),
    REVERSES_JOURNAL_NOT_FOUND(HttpStatus.NOT_FOUND, "ledger.reverses_journal_not_found"),
    JOURNAL_ALREADY_REVERSED(HttpStatus.CONFLICT, "ledger.journal_already_reversed"),
    ACCOUNT_INACTIVE(HttpStatus.CONFLICT, "ledger.account_inactive"),
    CONCURRENT_BALANCE_UPDATE(HttpStatus.CONFLICT, "ledger.concurrent_balance_update"),
    ACCOUNT_CODE_INVALID(HttpStatus.BAD_REQUEST, "ledger.account_code_invalid"),
    OWNER_REF_REQUIRED(HttpStatus.BAD_REQUEST, "ledger.owner_ref_required");
    private final HttpStatus httpStatus;
    private final String messageKey;

    LedgerError(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
