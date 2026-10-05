package com.gepe.gepay.ledger.api.dtos;

public record PostJournalResult(
        long journalId,
        String idempotencyKey,
        boolean newlyCreated   // false if idempotent replay (already existed)
) {
}
