package com.gepe.gepay.ledger.internal.repository;

import com.gepe.gepay.ledger.internal.entity.Entry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EntryRepository extends JpaRepository<Entry, Long> {

    List<Entry> findByJournalId(Long journalId);

    List<Entry> findByAccountIdOrderById(Long accountId);

    @Query(
            "SELECT SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount ELSE -e.amount END) "
            + "FROM Entry e WHERE e.accountId = :accountId"
    )
    Long computeBalance(@Param("accountId") Long accountId);
}
