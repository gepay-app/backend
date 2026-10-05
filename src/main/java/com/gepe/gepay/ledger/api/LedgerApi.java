package com.gepe.gepay.ledger.api;

import com.gepe.gepay.ledger.api.dtos.AccountDto;
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

//    // Convenience overload: auto-generates idempotencyKey from referenceType + referenceId + description hash
//    default PostJournalResult postJournal(
//            JournalReferenceType referenceType,
//            String referenceId,
//            String description,
//            Instant occurredAt,
//            List<JournalLine> lines,
//            Long reversesJournalId
//    ) {
//        String idemKey = referenceType.name() + ":" + referenceId + ":" + Integer.toHexString(description.hashCode());
//        return postJournal(idemKey, referenceType, referenceId, description, occurredAt, lines, reversesJournalId);
//    }
}
