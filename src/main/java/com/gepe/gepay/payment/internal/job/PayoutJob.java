package com.gepe.gepay.payment.internal.job;

import com.gepe.gepay.payment.internal.service.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Job Quartz (clustered) yang memproses withdrawal {@code REQUESTED}/
 * {@code PROCESSING} menjadi payout via provider (Flip). Idempoten via
 * idempotency key disburse + {@code journals.idempotency_key}.
 */
@Slf4j
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class PayoutJob extends QuartzJobBean {

    private final PayoutService payoutService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        log.info("Running payout job");
        try {
            payoutService.processPayouts();
        } catch (Exception e) {
            log.error("Payout job failed", e);
        }
    }
}
