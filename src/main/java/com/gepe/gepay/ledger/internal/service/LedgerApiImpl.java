package com.gepe.gepay.ledger.internal.service;

import com.gepe.gepay.ledger.api.LedgerApi;
import com.gepe.gepay.ledger.api.dtos.AccountDto;
import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.dtos.PostJournalResult;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerApiImpl implements LedgerApi {

    private final AccountService accountService;
    private final LedgerService ledgerService;

    @Override
    public AccountDto getOrCreateAccount(AccountCode code, String ownerRef) {
        return accountService.getOrCreateAccount(code, ownerRef);
    }

    @Override
    public AccountDto getAccount(AccountCode code, String ownerRef) {
        return accountService.getAccount(code, ownerRef);
    }

    @Override
    public PostJournalResult postJournal(
            String idempotencyKey,
            JournalReferenceType referenceType,
            String referenceId,
            String description,
            Instant occurredAt,
            List<JournalLine> lines,
            Long reversesJournalId
    ) {
        return ledgerService.postJournal(idempotencyKey, referenceType, referenceId, description, occurredAt, lines, reversesJournalId);
    }

    @Override
    public AccountDto getBalance(AccountCode code, String ownerRef) {
        return accountService.getBalance(code, ownerRef);
    }

    @Override
    public long getBalanceAmount(AccountCode code, String ownerRef) {
        return accountService.getBalanceAmount(code, ownerRef);
    }
}
