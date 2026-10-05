package com.gepe.gepay.ledger.internal.service;

import com.gepe.gepay.ledger.api.dtos.AccountDto;
import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.dtos.PostJournalResult;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountType;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import com.gepe.gepay.ledger.internal.entity.Account;
import com.gepe.gepay.ledger.internal.entity.Journal;
import com.gepe.gepay.ledger.internal.repository.AccountRepository;
import com.gepe.gepay.ledger.internal.repository.EntryRepository;
import com.gepe.gepay.ledger.internal.repository.JournalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private JournalRepository journalRepository;

    @Mock
    private EntryRepository entryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private LedgerService ledgerService;

    private Account acc1;
    private Account acc2;

    @BeforeEach
    void setUp() {
        acc1 = Account.create(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS");
        acc2 = Account.create(AccountCode.CREATOR_PAYABLE_PENDING, "USER-1");

        // Use reflection to set IDs
        setAccountId(acc1, 1L);
        setAccountId(acc2, 2L);
    }

    private void setAccountId(Account account, Long id) {
        try {
            var field = Account.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setJournalId(Journal journal, Long id) {
        try {
            var field = Journal.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(journal, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Idempotent replay should return existing journal without re-posting entries")
    void idempotentReplay_ShouldReturnExistingResult() {
        String idemKey = "PAYMENT:PAY-1:PAID";
        Journal existing = Journal.create(idemKey, JournalReferenceType.PAYMENT, "PAY-1", "Test", Instant.now());
        setJournalId(existing, 100L);

        when(journalRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.of(existing));

        PostJournalResult result = ledgerService.postJournal(
                idemKey,
                JournalReferenceType.PAYMENT,
                "PAY-1",
                "Test",
                Instant.now(),
                List.of(),
                null
        );

        assertThat(result.journalId()).isEqualTo(100L);
        assertThat(result.newlyCreated()).isFalse();
        verifyNoInteractions(accountService, entryRepository);
    }

    @Test
    @DisplayName("postJournal with valid lines should lock accounts in sorted order, save journal, entries, and update balances")
    void postJournal_ShouldSucceed() {
        String idemKey = "PAYMENT:PAY-1:PAID";
        when(journalRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.empty());

        AccountDto bal1 = new AccountDto(1L, AccountCode.PG_CLEARING_RECEIVABLE, "PG Clearing", AccountType.ASSET, EntryDirection.DEBIT, "PAYMENT_PROVIDER", "MIDTRANS", "IDR", 0, true);
        AccountDto bal2 = new AccountDto(2L, AccountCode.CREATOR_PAYABLE_PENDING, "Creator Payable", AccountType.LIABILITY, EntryDirection.CREDIT, "USER", "USER-1", "IDR", 0, true);

        when(accountService.getOrCreateAccount(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS")).thenReturn(bal1);
        when(accountService.getOrCreateAccount(AccountCode.CREATOR_PAYABLE_PENDING, "USER-1")).thenReturn(bal2);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(acc1));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(acc2));

        when(accountRepository.lockAllByIds(List.of(1L, 2L))).thenReturn(List.of(acc1, acc2));

        Journal savedJournal = Journal.create(idemKey, JournalReferenceType.PAYMENT, "PAY-1", "Test", Instant.now());
        setJournalId(savedJournal, 10L);
        when(journalRepository.saveAndFlush(any(Journal.class))).thenReturn(savedJournal);

        List<JournalLine> lines = List.of(
                new JournalLine(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS", EntryDirection.DEBIT, 100_000),
                new JournalLine(AccountCode.CREATOR_PAYABLE_PENDING, "USER-1", EntryDirection.CREDIT, 100_000)
        );

        PostJournalResult result = ledgerService.postJournal(
                idemKey,
                JournalReferenceType.PAYMENT,
                "PAY-1",
                "Test",
                Instant.now(),
                lines,
                null
        );

        assertThat(result.journalId()).isEqualTo(10L);
        assertThat(result.newlyCreated()).isTrue();

        verify(accountRepository).lockAllByIds(List.of(1L, 2L));
        verify(entryRepository).saveAll(any());
        verify(accountRepository).saveAll(any());

        assertThat(acc1.getBalance()).isEqualTo(100_000);
        assertThat(acc2.getBalance()).isEqualTo(100_000);
    }
}
