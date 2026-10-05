package com.gepe.gepay.ledger.internal.repository;

import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import com.gepe.gepay.ledger.internal.entity.Journal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JournalRepository extends JpaRepository<Journal, Long> {

    Optional<Journal> findByIdempotencyKey(String idempotencyKey);

    boolean existsByReversesJournalId(Long reversesJournalId);

    List<Journal> findByReferenceTypeAndReferenceId(
            JournalReferenceType referenceType, String referenceId);

    @Query("SELECT j FROM Journal j WHERE j.referenceType = :refType AND j.referenceId = :refId ORDER BY j.id DESC")
    List<Journal> findByReferenceTypeAndReferenceIdOrderByIdDesc(
            @Param("refType") JournalReferenceType referenceType,
            @Param("refId") String referenceId);
}
