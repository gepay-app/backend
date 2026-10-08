package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.Withdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {
}