package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByCode(String code);
}