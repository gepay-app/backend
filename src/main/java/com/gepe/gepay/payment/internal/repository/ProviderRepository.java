package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
}