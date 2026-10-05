package com.gepe.gepay.ledger.api.dtos;

import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountType;
import com.gepe.gepay.ledger.api.enums.EntryDirection;

import java.util.Objects;

public record AccountDto(
        long id,
        AccountCode code,
        String name,
        AccountType type,
        EntryDirection normalBalance,
        String ownerType,      // USER, PAYMENT_PROVIDER, PAYOUT_PROVIDER, BANK, or null
        String ownerRef,
        String currency,
        long balance,          // in normal balance direction
        boolean active
) {
    public AccountDto {
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        Objects.requireNonNull(normalBalance);
        Objects.requireNonNull(currency);
    }
}
