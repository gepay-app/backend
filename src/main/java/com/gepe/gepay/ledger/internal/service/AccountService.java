package com.gepe.gepay.ledger.internal.service;

import com.gepe.gepay.ledger.api.dtos.AccountDto;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountOwnerType;
import com.gepe.gepay.ledger.internal.entity.Account;
import com.gepe.gepay.ledger.internal.exception.LedgerError;
import com.gepe.gepay.ledger.internal.mapper.LedgerMapper;
import com.gepe.gepay.ledger.internal.repository.AccountRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerMapper mapper;

    @Transactional
    public AccountDto getOrCreateAccount(AccountCode code, String ownerRef) {
        if (code == null) {
            throw new ServiceException(LedgerError.ACCOUNT_CODE_INVALID, "null");
        }

        // Validate ownerRef requirement based on AccountCode metadata
        if (code.getOwnerType() != null && (ownerRef == null || ownerRef.isBlank())) {
            throw new ServiceException(LedgerError.OWNER_REF_REQUIRED, code.getCode());
        }
        if (code.isGlobal() && ownerRef != null && !ownerRef.isBlank()) {
            ownerRef = null;
        }

        AccountOwnerType ownerType = code.getOwnerType();
        String codeStr = code.getCode();

        // Fast path: non-locking read for existing accounts
        var existing = accountRepository.findByCodeAndOwnerTypeAndOwnerRef(code, ownerType, ownerRef);
        if (existing.isPresent()) {
            return mapper.toDto(existing.get());
        }

        // Not found — INSERT and let DB unique constraint handle race conditions
        Account account = Account.create(code, ownerRef);
        try {
            Account saved = accountRepository.saveAndFlush(account);
            log.debug("Created ledger account: code={}, name={}, ownerType={}, ownerRef={}, id={}",
                    codeStr, code.getDisplayName(), ownerType, ownerRef, saved.getId());
            return mapper.toDto(saved);
        } catch (DataIntegrityViolationException e) {
            String finalOwnerRef = ownerRef;
            Account winner = accountRepository.findByCodeAndOwnerTypeAndOwnerRef(code, ownerType, finalOwnerRef)
                    .orElseThrow(() -> new ServiceException(LedgerError.ACCOUNT_NOT_FOUND, codeStr, finalOwnerRef));
            return mapper.toDto(winner);
        }
    }

    @Transactional(readOnly = true)
    public AccountDto getAccount(AccountCode code, String ownerRef) {
        if (code == null) {
            throw new ServiceException(LedgerError.ACCOUNT_CODE_INVALID, "null");
        }
        String finalOwnerRef = code.isGlobal() ? null : ownerRef;
        return accountRepository.findByCodeAndOwnerTypeAndOwnerRef(code, code.getOwnerType(), finalOwnerRef)
                .map(mapper::toDto)
                .orElseThrow(() -> new ServiceException(LedgerError.ACCOUNT_NOT_FOUND, code.getCode(), finalOwnerRef));
    }

    @Transactional(readOnly = true)
    public AccountDto getBalance(AccountCode code, String ownerRef) {
        return getAccount(code, ownerRef);
    }

    @Transactional(readOnly = true)
    public long getBalanceAmount(AccountCode code, String ownerRef) {
        return getBalance(code, ownerRef).balance();
    }
}
