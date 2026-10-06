package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    boolean existsByDate(LocalDate date);

}
