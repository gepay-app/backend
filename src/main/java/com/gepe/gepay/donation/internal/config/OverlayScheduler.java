package com.gepe.gepay.donation.internal.config;

import com.gepe.gepay.donation.internal.job.OverlayAckWatchdogJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Menjadwalkan watchdog ack overlay pada scheduler Quartz clustered. Cron bisa
 * dioverride lewat {@code donation.overlay.watchdog-cron} (default tiap 30 detik).
 */
@Configuration(proxyBeanMethods = false)
public class OverlayScheduler {

    static final String JOB_KEY = "overlayAckWatchdogJob";
    static final String TRIGGER_KEY = "overlayAckWatchdogTrigger";
    static final TimeZone JAKARTA = TimeZone.getTimeZone("Asia/Jakarta");

    @Bean
    JobDetail overlayAckWatchdogJobDetail() {
        return JobBuilder.newJob(OverlayAckWatchdogJob.class)
                .withIdentity(JOB_KEY)
                .storeDurably(true)
                .build();
    }

    @Bean
    Trigger overlayAckWatchdogTrigger(
            JobDetail overlayAckWatchdogJobDetail,
            @Value("${donation.overlay.watchdog-cron:0/30 * * * * ?}") String cron
    ) {
        return TriggerBuilder.newTrigger()
                .forJob(overlayAckWatchdogJobDetail)
                .withIdentity(TRIGGER_KEY)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .inTimeZone(JAKARTA)
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
