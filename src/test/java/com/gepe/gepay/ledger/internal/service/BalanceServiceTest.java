package com.gepe.gepay.ledger.internal.service;

import com.gepe.gepay.ledger.api.dtos.AccountDto;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountType;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private AccountService accountService;

    private final String ownerRef = "user-123";

    @Test
    void getUserBalance_ComposesThreeUserAccounts() {
        when(accountService.getOrCreateAccount(AccountCode.CREATOR_PAYABLE_PENDING, ownerRef))
                .thenReturn(account(AccountCode.CREATOR_PAYABLE_PENDING, 12_000L));
        when(accountService.getOrCreateAccount(AccountCode.CREATOR_PAYABLE_AVAILABLE, ownerRef))
                .thenReturn(account(AccountCode.CREATOR_PAYABLE_AVAILABLE, 93_340L));
        when(accountService.getOrCreateAccount(AccountCode.WITHDRAWAL_PAYABLE, ownerRef))
                .thenReturn(account(AccountCode.WITHDRAWAL_PAYABLE, 5_000L));

        var balance = new BalanceService(accountService).getUserBalance(ownerRef);

        assertThat(balance.pending()).isEqualTo(12_000L);
        assertThat(balance.available()).isEqualTo(93_340L);
        assertThat(balance.withdrawalPayable()).isEqualTo(5_000L);
        assertThat(balance.currency()).isEqualTo("IDR");
    }

    private AccountDto account(AccountCode code, long balance) {
        return new AccountDto(1L, code, code.getDisplayName(), AccountType.LIABILITY,
                EntryDirection.CREDIT, "USER", ownerRef, "IDR", balance, true);
    }
}
