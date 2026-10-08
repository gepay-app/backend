package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}