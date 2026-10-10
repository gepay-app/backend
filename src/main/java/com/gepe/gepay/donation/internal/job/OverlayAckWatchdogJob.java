package com.gepe.gepay.donation.internal.job;

import com.gepe.gepay.donation.internal.config.DonationOverlayProperties;
import com.gepe.gepay.donation.internal.service.OverlayDispatcher;
import com.gepe.gepay.donation.internal.service.OverlayWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.UUID;

/**
 * Watchdog ack overlay: event {@code PLAYING} yang lewat timeout (display putus
 * / OBS ditutup di tengah animasi) dikembalikan ke antrean (atau FAILED),
 * lalu creator terkait di-dispatch ulang.
 */
@Slf4j
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class OverlayAckWatchdogJob extends QuartzJobBean {

    private final DonationOverlayProperties properties;
    private final OverlayWriter overlayWriter;
    private final OverlayDispatcher overlayDispatcher;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        try {
            var creators = overlayWriter.recoverTimedOut(
                    properties.getAckTimeoutSeconds(), properties.isAutoRequeueOnTimeout());
            for (UUID creatorId : creators) {
                overlayDispatcher.dispatch(creatorId);
            }
            if (!creators.isEmpty()) {
                log.info("Overlay watchdog recovered {} creator queue(s)", creators.size());
            }
        } catch (Exception e) {
            log.error("Overlay ack watchdog failed", e);
        }
    }
}
