package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
}