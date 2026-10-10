package com.gepe.gepay.payment.internal.config;

import com.gepe.gepay.payment.internal.job.SettlementJob;
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
 * Mendaftarkan job settlement harian pada scheduler Quartz clustered
 * (lihat {@code AGENTS.md} §11.1). Cron & timezone bisa dioverride lewat
 * {@code payment.settlement.cron} (default 03:00 Asia/Jakarta).
 */
@Configuration(proxyBeanMethods = false)
public class SettlementScheduler {

    static final String JOB_KEY = "settlementJob";
    static final String TRIGGER_KEY = "settlementTrigger";
    static final TimeZone JAKARTA = TimeZone.getTimeZone("Asia/Jakarta");

    @Bean
    JobDetail settlementJobDetail() {
        return JobBuilder.newJob(SettlementJob.class)
                .withIdentity(JOB_KEY)
                .storeDurably(true)
                .build();
    }

    @Bean
    Trigger settlementTrigger(
            JobDetail settlementJobDetail,
            @Value("${payment.settlement.cron}") String cron
    ) {
        return TriggerBuilder.newTrigger()
                .forJob(settlementJobDetail)
                .withIdentity(TRIGGER_KEY)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .inTimeZone(JAKARTA)
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
