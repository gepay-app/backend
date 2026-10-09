package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    Optional<Provider> findByCode(String code);
}