package com.gepe.gepay.ledger.api;

import com.gepe.gepay.ledger.api.dtos.AccountDto;
import com.gepe.gepay.ledger.api.dtos.BalanceResponse;
import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.dtos.PostJournalResult;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;

import java.time.Instant;
import java.util.List;

public interface LedgerApi {
    // Account — lazy get-or-create (called by payment module)
    AccountDto getOrCreateAccount(AccountCode code, String ownerRef);
    AccountDto getAccount(AccountCode code, String ownerRef);

    // ---------------------------------------------------------------------
    // Journal — core write path
    // ---------------------------------------------------------------------
    /**
     * Posts a balanced journal. Idempotent via idempotencyKey.
     * Throws LedgerError.UNBALANCED_JOURNAL if Σdebit ≠ Σcredit.
     * Throws LedgerError.IDEMPOTENCY_CONFLICT if key already exists.
     *
     * @param idempotencyKey   format: "{referenceType}:{referenceId}:{action}" e.g. "PAYMENT:PAY-1:PAID"
     * @param referenceType    PAYMENT | SETTLEMENT | WITHDRAWAL | PAYOUT | FUND_TRANSFER | REFUND | ADJUSTMENT
     * @param referenceId      business ID (paymentId, settlementId, withdrawalId, payoutId, fundTransferId)
     * @param description      human-readable description for audit
     * @param occurredAt       business timestamp (when event happened, not system time)
     * @param lines            min 2 lines, ΣDEBIT = ΣCREDIT, amount > 0
     * @param reversesJournalId  nullable, for reversal/correction journals
     */
    PostJournalResult postJournal(
            String idempotencyKey,
            JournalReferenceType referenceType,
            String referenceId,
            String description,
            Instant occurredAt,
            List<JournalLine> lines,
            Long reversesJournalId  // nullable, for reversal journals
    );

    // Balance queries (read path, cached)
    AccountDto getBalance(AccountCode code, String ownerRef);
    long getBalanceAmount(AccountCode code, String ownerRef);

    /**
     * Saldo user terkomposisi (pending / available / hold) dari akun liabilitas
     * ber-owner {@code USER}. Akun di-provision lazy, jadi user baru balik 0.
     */
    BalanceResponse getUserBalance(String ownerRef);
}
