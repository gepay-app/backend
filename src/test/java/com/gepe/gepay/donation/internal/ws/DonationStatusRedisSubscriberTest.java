package com.gepe.gepay.donation.internal.ws;

import com.gepe.gepay.donation.internal.service.DonationStatusStreamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonationStatusRedisSubscriberTest {

    @Mock
    private DonationStatusStreamService streamService;

    @Test
    void deliversPaidToLocalStream() {
        UUID donationId = UUID.randomUUID();
        byte[] channel = ("donation:payment:" + donationId).getBytes(StandardCharsets.UTF_8);
        byte[] body = "paid".getBytes(StandardCharsets.UTF_8);

        new DonationStatusRedisSubscriber(streamService).onMessage(new DefaultMessage(channel, body), null);

        verify(streamService).deliverPaid(donationId);
    }

    @Test
    void ignoresUnparsableChannel() {
        byte[] channel = "donation:payment:not-a-uuid".getBytes(StandardCharsets.UTF_8);
        byte[] body = "paid".getBytes(StandardCharsets.UTF_8);

        new DonationStatusRedisSubscriber(streamService).onMessage(new DefaultMessage(channel, body), null);

        verifyNoInteractions(streamService);
    }
}
