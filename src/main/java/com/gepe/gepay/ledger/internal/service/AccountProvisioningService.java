package com.gepe.gepay.ledger.internal.service;

import com.gepe.gepay.ledger.internal.entity.Account;
import com.gepe.gepay.ledger.internal.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Melakukan INSERT akun di <strong>transaksi terpisah</strong>
 * ({@code REQUIRES_NEW}). Kalau dua request membuat akun yang sama secara
 * bersamaan, yang kalah menabrak unique constraint
 * ({@code ux_accounts_code_owner}); karena kesalahan itu terjadi di transaksi
 * dalam yang punya sendiri, PostgreSQL hanya membatalkan transaksi dalam itu —
 * transaksi pemanggil (mis. {@code postJournal}) tetap hidup dan bisa membaca
 * pemenangnya. Kalau INSERT dilakukan di transaksi pemanggil, error constraint
 * akan meng-abort seluruh transaksi dan langkah re-read berikutnya gagal.
 */
@Service
@RequiredArgsConstructor
public class AccountProvisioningService {

    private final AccountRepository accountRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Account insert(Account account) {
        return accountRepository.saveAndFlush(account);
    }
}
