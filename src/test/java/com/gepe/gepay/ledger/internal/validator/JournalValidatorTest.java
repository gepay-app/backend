package com.gepe.gepay.ledger.internal.validator;

import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import com.gepe.gepay.platform.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JournalValidatorTest {

    @Test
    @DisplayName("Valid journal lines (equal debit and credit) should pass validation")
    void validJournal_ShouldPass() {
        List<JournalLine> lines = List.of(
                new JournalLine(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS", EntryDirection.DEBIT, 100_000),
                new JournalLine(AccountCode.CREATOR_PAYABLE_PENDING, "USER-1", EntryDirection.CREDIT, 100_000)
        );

        assertDoesNotThrow(() -> JournalValidator.validate(lines));
    }

    @Test
    @DisplayName("Journal with fewer than 2 lines should throw ServiceException")
    void lessThanTwoLines_ShouldThrowException() {
        List<JournalLine> lines = List.of(
                new JournalLine(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS", EntryDirection.DEBIT, 100_000)
        );

        assertThatThrownBy(() -> JournalValidator.validate(lines))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("Unbalanced journal (debit != credit) should throw ServiceException")
    void unbalancedJournal_ShouldThrowException() {
        List<JournalLine> lines = List.of(
                new JournalLine(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS", EntryDirection.DEBIT, 100_000),
                new JournalLine(AccountCode.CREATOR_PAYABLE_PENDING, "USER-1", EntryDirection.CREDIT, 90_000)
        );

        assertThatThrownBy(() -> JournalValidator.validate(lines))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("Journal with non-positive amount should throw ServiceException")
    void zeroOrNegativeAmount_ShouldThrowException() {
        List<JournalLine> lines = List.of(
                new JournalLine(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS", EntryDirection.DEBIT, 0),
                new JournalLine(AccountCode.CREATOR_PAYABLE_PENDING, "USER-1", EntryDirection.CREDIT, 0)
        );

        assertThatThrownBy(() -> JournalValidator.validate(lines))
                .isInstanceOf(ServiceException.class);
    }
}
