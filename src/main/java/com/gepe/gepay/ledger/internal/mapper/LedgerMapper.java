package com.gepe.gepay.ledger.internal.mapper;

import com.gepe.gepay.ledger.api.dtos.AccountDto;
import com.gepe.gepay.ledger.internal.entity.Account;
import org.springframework.stereotype.Component;

@Component
public class LedgerMapper {

    public AccountDto toDto(Account account) {
        return new AccountDto(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                account.getNormalBalance(),
                account.getOwnerType() != null ? account.getOwnerType().name() : null,
                account.getOwnerRef(),
                account.getCurrency(),
                account.getBalance(),
                account.isActive()
        );
    }
}
