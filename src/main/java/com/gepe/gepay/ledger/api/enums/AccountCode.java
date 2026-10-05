package com.gepe.gepay.ledger.api.enums;


import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sumber kebenaran Chart of Accounts. Type, normal balance, dan owner
 * diturunkan dari sini, jadi tidak mungkin salah input di service.
 * Akun baru = tambah konstanta di sini + seed di migration.
 */
@Getter
@RequiredArgsConstructor
public enum AccountCode {

    // ASSET
    PG_CLEARING_RECEIVABLE("1100", "PG Clearing Receivable", AccountType.ASSET, AccountOwnerType.PAYMENT_PROVIDER),
    PAYIN_PROVIDER_BALANCE("1150", "Payin Provider Balance", AccountType.ASSET, AccountOwnerType.PAYMENT_PROVIDER),
    BANK_OPERATING("1200", "Bank Operating", AccountType.ASSET, AccountOwnerType.BANK),
    PAYOUT_PROVIDER_FLOAT("1300", "Payout Provider Float", AccountType.ASSET, AccountOwnerType.PAYOUT_PROVIDER),
    FUND_TRANSFER_IN_TRANSIT("1400", "Fund Transfer In Transit", AccountType.ASSET, null),

    // LIABILITY
    CREATOR_PAYABLE_PENDING("2100", "Creator Payable - Pending", AccountType.LIABILITY, AccountOwnerType.USER),
    CREATOR_PAYABLE_AVAILABLE("2110", "Creator Payable - Available", AccountType.LIABILITY, AccountOwnerType.USER),
    WITHDRAWAL_PAYABLE("2200", "Withdrawal Payable", AccountType.LIABILITY, AccountOwnerType.USER),
    VAT_PAYABLE("2300", "VAT Payable", AccountType.LIABILITY, null),

    // REVENUE
    PLATFORM_FEE_REVENUE("4000", "Platform Fee Revenue", AccountType.REVENUE, null),
    WITHDRAWAL_FEE_REVENUE("4100", "Withdrawal Fee Revenue", AccountType.REVENUE, null),

    // EXPENSE
    PG_FEE_EXPENSE("5000", "Payment Gateway Fee Expense", AccountType.EXPENSE, null),
    PAYOUT_FEE_EXPENSE("5100", "Payout Fee Expense", AccountType.EXPENSE, null),
    BANK_TRANSFER_FEE_EXPENSE("5150", "Bank / Fund Transfer Fee Expense", AccountType.EXPENSE, null),
    REFUND_CHARGEBACK_LOSS("5200", "Refund / Chargeback Loss", AccountType.EXPENSE, null),
    FUND_TRANSFER_VARIANCE("5900", "Fund Transfer Variance", AccountType.EXPENSE, null),

    // ASSET (clawback ke creator; kode 5300 sesuai design.md)
    CREATOR_NEGATIVE_BALANCE("5300", "Creator Negative Balance", AccountType.ASSET, AccountOwnerType.USER);

    private final String code;
    private final String displayName;
    private final AccountType type;
    /** null = akun global (tanpa owner). */
    private final AccountOwnerType ownerType;

    // Fail-fast saat startup kalau ada kode ganda.
    private static final Map<String, AccountCode> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(AccountCode::getCode, Function.identity()));

    public boolean isGlobal() {
        return ownerType == null;
    }

    public EntryDirection normalBalance() {
        return type.getNormalBalance();
    }

    public static AccountCode fromCode(String code) {
        AccountCode c = BY_CODE.get(code);
        if (c == null) throw new IllegalArgumentException("Unknown account code: " + code);
        return c;
    }

    /** Simpan sebagai '1100' di DB, bukan nama enum. */
    @Converter
    public static class JpaConverter implements AttributeConverter<AccountCode, String> {
        @Override
        public String convertToDatabaseColumn(AccountCode attribute) {
            return attribute == null ? null : attribute.code;
        }

        @Override
        public AccountCode convertToEntityAttribute(String dbData) {
            return dbData == null ? null : fromCode(dbData);
        }
    }
}
