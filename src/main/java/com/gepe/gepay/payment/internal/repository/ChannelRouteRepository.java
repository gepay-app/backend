package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.ChannelRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChannelRouteRepository extends JpaRepository<ChannelRoute, Long> {

    /**
     * Route aktif dengan prioritas tertinggi (angka terkecil) untuk sebuah channel.
     * Nominal tidak ikut dipilih di sini — validasi min/max dilakukan pemanggil
     * agar alasan kegagalan bisa dibedakan (lihat {@code PaymentService}).
     * Satu channel bisa dilayani beberapa provider; pemenangnya ditentukan priority.
     */
    @Query("""
        SELECT r FROM ChannelRoute r
        WHERE r.channelId = :channelId
          AND r.isActive = true
        ORDER BY r.priority ASC
        LIMIT 1
    """)
    Optional<ChannelRoute> findActiveRoute(@Param("channelId") Long channelId);
}