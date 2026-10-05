package com.gepe.gepay.ledger.api.dtos;

import com.gepe.gepay.ledger.api.enums.JournalReferenceType;

import java.time.Instant;
import java.util.List;

public record PostJournalRequest(
        String idempotencyKey,
        JournalReferenceType referenceType,
        String referenceId,
        String description,
        Instant occurredAt,
        List<JournalLine> lines,
        Long reversesJournalId
) {}
