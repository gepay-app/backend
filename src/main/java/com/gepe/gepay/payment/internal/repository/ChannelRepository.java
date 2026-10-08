package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
}