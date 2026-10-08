package com.gepe.gepay.ledger.internal.service;

import com.gepe.gepay.ledger.api.dtos.AccountDto;
import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.dtos.PostJournalResult;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import com.gepe.gepay.ledger.internal.entity.Account;
import com.gepe.gepay.ledger.internal.entity.Entry;
import com.gepe.gepay.ledger.internal.entity.Journal;
import com.gepe.gepay.ledger.internal.exception.LedgerError;
import com.gepe.gepay.ledger.internal.repository.AccountRepository;
import com.gepe.gepay.ledger.internal.repository.EntryRepository;
import com.gepe.gepay.ledger.internal.repository.JournalRepository;
import com.gepe.gepay.ledger.internal.validator.JournalValidator;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class LedgerService {

    private final JournalRepository journalRepository;
    private final EntryRepository entryRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;

    // ---------------------------------------------------------------------
    // Primary: full control (used by payment module for idempotency)
    // ---------------------------------------------------------------------
    @Transactional
    public PostJournalResult postJournal(
            String idempotencyKey,
            JournalReferenceType referenceType,
            String referenceId,
            String description,
            Instant occurredAt,
            List<JournalLine> lines,
            Long reversesJournalId
    ) {
        // 1. Check idempotency pre-check
        var existingJournal = journalRepository.findByIdempotencyKey(idempotencyKey);
        if (existingJournal.isPresent()) {
            Journal existing = existingJournal.get();
            log.debug("Idempotent replay (pre-check): key={}, journalId={}", idempotencyKey, existing.getId());
            return new PostJournalResult(existing.getId(), idempotencyKey, false);
        }

        // 2. Validate double-entry rules: ΣDEBIT = ΣCREDIT, min 2 lines, amount > 0
        JournalValidator.validate(lines);

        // 3. Resolve / get-or-create distinct accounts WITHOUT locking
        Map<AccountKey, Account> resolvedAccounts = new HashMap<>();
        for (JournalLine line : lines) {
            AccountKey key = new AccountKey(line.accountCode(), line.ownerRef());
            if (!resolvedAccounts.containsKey(key)) {
                AccountDto accountDto = accountService.getOrCreateAccount(line.accountCode(), line.ownerRef());
                Account acc = accountRepository.findById(accountDto.id())
                        .orElseThrow(() -> new ServiceException(LedgerError.ACCOUNT_NOT_FOUND,
                                line.accountCode().getCode(), line.ownerRef()));
                resolvedAccounts.put(key, acc);
            }
        }

        // 4. Deterministic sorting & batched pessimistic locking (DEADLOCK PREVENTION)
        List<Long> sortedAccountIds = resolvedAccounts.values().stream()
                .map(Account::getId)
                .distinct()
                .sorted()
                .toList();

        Map<Long, Account> lockedAccountMap = new HashMap<>();
        if (!sortedAccountIds.isEmpty()) {
            List<Account> lockedList = accountRepository.lockAllByIds(sortedAccountIds);
            lockedAccountMap = lockedList.stream()
                    .collect(Collectors.toMap(Account::getId, Function.identity()));
        }

        // 5. Create Journal Entity — validate reversal & catch DB unique constraint on idempotency_key for race handling
        Journal journal = Journal.create(idempotencyKey, referenceType, referenceId, description, occurredAt);
        if (reversesJournalId != null) {
            if (!journalRepository.existsById(reversesJournalId)) {
                throw new ServiceException(LedgerError.REVERSES_JOURNAL_NOT_FOUND, reversesJournalId);
            }
            if (journalRepository.existsByReversesJournalId(reversesJournalId)) {
                throw new ServiceException(LedgerError.JOURNAL_ALREADY_REVERSED, reversesJournalId);
            }

            journal = Journal.createReversal(idempotencyKey, referenceType, referenceId, description, occurredAt, reversesJournalId);
        }

        Journal savedJournal;
        try {
            savedJournal = journalRepository.saveAndFlush(journal);
        } catch (DataIntegrityViolationException e) {
            Journal existing = journalRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new ServiceException(LedgerError.IDEMPOTENCY_CONFLICT, idempotencyKey));
            log.debug("Idempotent replay (DB race): key={}, journalId={}", idempotencyKey, existing.getId());
            return new PostJournalResult(existing.getId(), idempotencyKey, false);
        }

        // 6. Create Entries + update Account balances & evict stale cache
        List<Entry> entries = new ArrayList<>(lines.size());
        for (JournalLine line : lines) {
            AccountKey key = new AccountKey(line.accountCode(), line.ownerRef());
            Account unlockedAcc = resolvedAccounts.get(key);
            Account lockedAcc = lockedAccountMap.get(unlockedAcc.getId());

            if (!lockedAcc.isActive()) {
                throw new ServiceException(LedgerError.ACCOUNT_INACTIVE, line.accountCode().getCode());
            }

            Entry entry = Entry.of(savedJournal.getId(), lockedAcc.getId(), line.direction(), line.amount());
            entries.add(entry);

            // Mutate balance
            lockedAcc.apply(line.direction(), line.amount());
        }

        entryRepository.saveAll(entries);
        accountRepository.saveAll(lockedAccountMap.values()); // Triggers optimistic version increment

        log.info("Posted journal successfully: id={}, refType={}, refId={}, lines={}, idemKey={}",
                savedJournal.getId(), referenceType, referenceId, lines.size(), idempotencyKey);

        return new PostJournalResult(savedJournal.getId(), idempotencyKey, true);
    }

    private record AccountKey(AccountCode code, String ownerRef) {}

    // Convenience overload matching LedgerApi default method
    @Transactional
    public PostJournalResult postJournal(
            JournalReferenceType referenceType,
            String referenceId,
            String description,
            Instant occurredAt,
            List<JournalLine> lines,
            Long reversesJournalId
    ) {
        String idemKey = referenceType.name() + ":" + referenceId + ":" + Integer.toHexString(description.hashCode());
        return postJournal(idemKey, referenceType, referenceId, description, occurredAt, lines, reversesJournalId);
    }
}
