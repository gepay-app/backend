package com.gepe.gepay.ledger.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountType {
    ASSET(EntryDirection.DEBIT),
    EXPENSE(EntryDirection.DEBIT),
    LIABILITY(EntryDirection.CREDIT),
    EQUITY(EntryDirection.CREDIT),
    REVENUE(EntryDirection.CREDIT);

    private final EntryDirection normalBalance;
}
