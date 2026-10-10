package com.gepe.gepay.donation.internal.listener;

import com.gepe.gepay.donation.internal.dto.DonationPaidResult;
import com.gepe.gepay.donation.internal.service.DonationPaidService;
import com.gepe.gepay.donation.internal.service.DonationStatusStreamService;
import com.gepe.gepay.donation.internal.service.OverlayDispatcher;
import com.gepe.gepay.payment.api.event.PaymentPaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Listener domain event payment. {@code @ApplicationModuleListener} = setelah
 * commit + async + transaksi sendiri; at-least-once sehingga penanganan wajib
 * idempotent (dijamin {@link DonationPaidService}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DonationPaidListener {

    private static final String DONATION_TYPE = "DONATION";

    private final DonationPaidService donationPaidService;
    private final OverlayDispatcher overlayDispatcher;
    private final DonationStatusStreamService statusStreamService;

    @ApplicationModuleListener
    public void onPaymentPaid(PaymentPaidEvent event) {
        if (!DONATION_TYPE.equals(event.type())) {
            return;
        }
        donationPaidService.handlePaid(event.paymentId(), event.paidAt())
                .ifPresent(this::afterCommit);
    }

    /**
     * Dispatch + notifikasi SSE hanya setelah transaksi handler commit, supaya
     * display/donor tidak menerima sinyal untuk transaksi yang ternyata rollback.
     */
    private void afterCommit(DonationPaidResult result) {
        Runnable action = () -> {
            overlayDispatcher.dispatch(result.creatorId());
            statusStreamService.publishPaid(result.donationId());
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
