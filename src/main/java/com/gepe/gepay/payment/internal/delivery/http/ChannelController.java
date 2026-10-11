package com.gepe.gepay.payment.internal.delivery.http;

import com.gepe.gepay.payment.api.PaymentApi;
import com.gepe.gepay.payment.api.WithdrawalApi;
import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.internal.delivery.http.res.ChannelRes;
import com.gepe.gepay.payment.internal.delivery.http.res.PayinChannelRes;
import com.gepe.gepay.payment.internal.delivery.http.res.PayoutChannelRes;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Referensi channel aktif. Katalog dipisah per arah: {@code /payin} publik
 * untuk donor (fee ditanggung pembayar), {@code /payout} untuk creator (butuh
 * auth karena tarif bergantung override per user).
 */
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
@Tag(name = "Channels", description = "Channel catalog: public PAYIN, authenticated PAYOUT")
public class ChannelController {

    private final PaymentApi paymentApi;
    private final WithdrawalApi withdrawalApi;
    private final MessageHelper messageHelper;

    /**
     * @deprecated diganti {@link #listPayin(Long)} / {@link #listPayout()}.
     *     Dipertahankan sampai frontend migrasi.
     */
    @Deprecated(since = "channel-catalog-split", forRemoval = true)
    @GetMapping
    @Operation(summary = "List active channels by direction (deprecated: use /payin or /payout)")
    public ResponseEntity<ApiResponse<List<ChannelRes>>> list(
            @RequestParam(defaultValue = "PAYOUT") ChannelDirection direction) {
        List<ChannelRes> response = paymentApi.listChannels(direction)
                .stream().map(ChannelRes::from).toList();
        return ResponseEntity.ok(new ApiResponse<>(null, response));
    }

    @GetMapping("/payin")
    @Operation(summary = "Public PAYIN channel catalog with payer fee (optional amount)")
    public ResponseEntity<ApiResponse<List<PayinChannelRes>>> listPayin(
            @RequestParam(value = "amount", required = false) Long amount) {
        List<PayinChannelRes> response = paymentApi.listPayinChannels(amount)
                .stream().map(PayinChannelRes::from).toList();
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("payment.payin_channels_retrieved"), response));
    }

    @GetMapping("/payout")
    @Operation(summary = "Authenticated PAYOUT channel catalog with effective withdrawal fee")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<List<PayoutChannelRes>>> listPayout() {
        List<PayoutChannelRes> response = withdrawalApi.listPayoutChannels()
                .stream().map(PayoutChannelRes::from).toList();
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("payment.payout_channels_retrieved"), response));
    }
}
