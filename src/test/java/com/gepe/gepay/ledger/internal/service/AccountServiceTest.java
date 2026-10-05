package com.gepe.gepay.ledger.internal.service;

import com.gepe.gepay.ledger.api.dtos.AccountDto;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountType;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import com.gepe.gepay.ledger.internal.entity.Account;
import com.gepe.gepay.ledger.internal.mapper.LedgerMapper;
import com.gepe.gepay.ledger.internal.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerMapper mapper;

    @InjectMocks
    private AccountService accountService;

    private Account acc1;
    private AccountDto acc1Dto;

    @BeforeEach
    void setUp() {
        acc1 = Account.create(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS");
        setAccountId(acc1, 1L);

        acc1Dto = new AccountDto(
                1L, AccountCode.PG_CLEARING_RECEIVABLE, "PG Clearing Receivable",
                AccountType.ASSET, EntryDirection.DEBIT, "PAYMENT_PROVIDER", "MIDTRANS",
                "IDR", 0, true
        );
        
        lenient().when(mapper.toDto(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            return new AccountDto(
                    a.getId(), a.getCode(), a.getName(), a.getType(), a.getNormalBalance(),
                    a.getOwnerType() != null ? a.getOwnerType().name() : null,
                    a.getOwnerRef(), a.getCurrency(), a.getBalance(), a.isActive()
            );
        });
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

    @Test
    @DisplayName("getOrCreateAccount returns existing account without creating new")
    void getOrCreateAccount_Existing_ReturnsExisting() {
        when(accountRepository.findByCodeAndOwnerTypeAndOwnerRef(
                eq(AccountCode.PG_CLEARING_RECEIVABLE), any(), eq("MIDTRANS")))
                .thenReturn(Optional.of(acc1));

        AccountDto result = accountService.getOrCreateAccount(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS");

        assertThat(result).isEqualTo(acc1Dto);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("getOrCreateAccount creates new account when not found")
    void getOrCreateAccount_NotFound_CreatesNew() {
        when(accountRepository.findByCodeAndOwnerTypeAndOwnerRef(
                eq(AccountCode.PG_CLEARING_RECEIVABLE), any(), eq("MIDTRANS")))
                .thenReturn(Optional.empty());
        when(accountRepository.saveAndFlush(any(Account.class))).thenReturn(acc1);

        AccountDto result = accountService.getOrCreateAccount(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.code()).isEqualTo(AccountCode.PG_CLEARING_RECEIVABLE);
        assertThat(result.ownerRef()).isEqualTo("MIDTRANS");
        verify(accountRepository).saveAndFlush(any(Account.class));
    }

    @Test
    @DisplayName("getOrCreateAccount handles concurrent insert race by returning winner")
    void getOrCreateAccount_RaceCondition_ReturnsWinner() {
        when(accountRepository.findByCodeAndOwnerTypeAndOwnerRef(
                eq(AccountCode.PG_CLEARING_RECEIVABLE), any(), eq("MIDTRANS")))
                .thenReturn(Optional.empty(), Optional.of(acc1));
        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        AccountDto result = accountService.getOrCreateAccount(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS");

        assertThat(result).isEqualTo(acc1Dto);
    }

    @Test
    @DisplayName("getOrCreateAccount throws when ownerRef required but missing")
    void getOrCreateAccount_MissingOwnerRef_Throws() {
        assertThatThrownBy(() -> accountService.getOrCreateAccount(AccountCode.PG_CLEARING_RECEIVABLE, null))
                .hasMessageContaining("owner-ref-required");
        assertThatThrownBy(() -> accountService.getOrCreateAccount(AccountCode.PG_CLEARING_RECEIVABLE, ""))
                .hasMessageContaining("owner-ref-required");
    }

    @Test
    @DisplayName("getOrCreateAccount ignores ownerRef for global accounts")
    void getOrCreateAccount_GlobalAccount_IgnoresOwnerRef() {
        when(accountRepository.findByCodeAndOwnerTypeAndOwnerRef(
                eq(AccountCode.VAT_PAYABLE), any(), eq((String) null)))
                .thenReturn(Optional.empty());
        Account globalAcc = Account.create(AccountCode.VAT_PAYABLE, null);
        setAccountId(globalAcc, 2L);
        when(accountRepository.saveAndFlush(any(Account.class))).thenReturn(globalAcc);

        AccountDto result = accountService.getOrCreateAccount(AccountCode.VAT_PAYABLE, "some-ref");

        assertThat(result.ownerRef()).isNull();
        assertThat(result.code()).isEqualTo(AccountCode.VAT_PAYABLE);
    }

    @Test
    @DisplayName("getAccount throws when account not found")
    void getAccount_NotFound_Throws() {
        when(accountRepository.findByCodeAndOwnerTypeAndOwnerRef(
                eq(AccountCode.PG_CLEARING_RECEIVABLE), any(), eq("MIDTRANS")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS"))
                .hasMessageContaining("account-not-found");
    }

    @Test
    @DisplayName("getBalanceAmount returns balance from getBalance")
    void getBalanceAmount_ReturnsBalance() {
        when(accountRepository.findByCodeAndOwnerTypeAndOwnerRef(
                eq(AccountCode.PG_CLEARING_RECEIVABLE), any(), eq("MIDTRANS")))
                .thenReturn(Optional.of(acc1));

        long balance = accountService.getBalanceAmount(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS");

        assertThat(balance).isEqualTo(0L);
    }
}