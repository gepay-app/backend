package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.ReconciliationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItem, UUID> {
}