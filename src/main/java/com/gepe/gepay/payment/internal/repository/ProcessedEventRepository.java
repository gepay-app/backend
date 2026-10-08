package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}