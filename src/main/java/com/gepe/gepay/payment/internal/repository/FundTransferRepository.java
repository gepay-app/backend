package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.FundTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FundTransferRepository extends JpaRepository<FundTransfer, UUID> {
}