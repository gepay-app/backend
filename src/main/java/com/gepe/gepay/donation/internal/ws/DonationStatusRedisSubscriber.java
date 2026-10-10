package com.gepe.gepay.donation.internal.ws;

import com.gepe.gepay.donation.internal.service.DonationStatusStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Meneruskan sinyal PAID dari Redis ke koneksi SSE lokal pada node ini. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DonationStatusRedisSubscriber implements MessageListener {

    private final DonationStatusStreamService streamService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        int idx = channel.lastIndexOf(':');
        if (idx < 0) {
            return;
        }
        try {
            streamService.deliverPaid(UUID.fromString(channel.substring(idx + 1)));
        } catch (IllegalArgumentException ignored) {
            log.debug("Ignoring payment status message on channel {}", channel);
        }
    }
}
