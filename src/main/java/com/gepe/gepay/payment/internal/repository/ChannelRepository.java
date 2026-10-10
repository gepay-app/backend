package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.internal.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByCode(String code);

    List<Channel> findByDirectionAndIsActiveTrue(ChannelDirection direction);
}
