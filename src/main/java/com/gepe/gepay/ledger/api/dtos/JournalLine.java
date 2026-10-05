package com.gepe.gepay.ledger.api.dtos;

import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.EntryDirection;

public record JournalLine(
        // e.g. PG_CLEARING_RECEIVABLE
        AccountCode accountCode,
        // providerId, userId, "BANK-1", or null for global
        String ownerRef,
        // DEBIT or CREDIT
        EntryDirection direction,
        long amount
) {
}
