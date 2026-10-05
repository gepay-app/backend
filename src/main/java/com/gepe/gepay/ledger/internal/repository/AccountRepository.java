package com.gepe.gepay.ledger.internal.repository;

import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountOwnerType;
import com.gepe.gepay.ledger.internal.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    // NON-locking read by composite key (code, owner_type, owner_ref)
    Optional<Account> findByCodeAndOwnerTypeAndOwnerRef(
            AccountCode code, AccountOwnerType ownerType, String ownerRef);

    // Batched pessimistic lock in deterministic order (deadlock prevention)
    // Uses native query with array_position for exact lock order matching sorted IDs
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            SELECT * FROM ledger.accounts
            WHERE id = ANY(:ids)
            ORDER BY array_position(:ids::bigint[], id)
            FOR UPDATE
            """, nativeQuery = true)
    List<Account> lockAllByIds(@Param("ids") List<Long> ids);

    // Find all sub-accounts for a given owner (e.g. all accounts for a creator)
    List<Account> findByOwnerTypeAndOwnerRef(AccountOwnerType ownerType, String ownerRef);

    // Global accounts (owner_type IS NULL)
    List<Account> findByOwnerTypeIsNull();
}

