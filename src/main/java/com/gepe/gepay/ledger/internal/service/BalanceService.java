package com.gepe.gepay.ledger.internal.service;

import com.gepe.gepay.ledger.api.dtos.BalanceResponse;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Baca saldo user sebagai komposisi akun liabilitas ber-owner {@code USER}.
 * Akun di-provision lazy lewat {@code getOrCreateAccount} (idempoten, aman-race),
 * jadi user baru balik {@code 0} — bukan 404.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BalanceService {

    private final AccountService accountService;

    @Transactional(readOnly = true)
    public BalanceResponse getUserBalance(String ownerRef) {
        long pending = accountService.getOrCreateAccount(AccountCode.CREATOR_PAYABLE_PENDING, ownerRef).balance();
        long available = accountService.getOrCreateAccount(AccountCode.CREATOR_PAYABLE_AVAILABLE, ownerRef).balance();
        long withdrawalPayable = accountService.getOrCreateAccount(AccountCode.WITHDRAWAL_PAYABLE, ownerRef).balance();
        return new BalanceResponse(pending, available, withdrawalPayable, "IDR");
    }
}
