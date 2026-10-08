package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.UserFeeOverride;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFeeOverrideRepository extends JpaRepository<UserFeeOverride, Long> {
}