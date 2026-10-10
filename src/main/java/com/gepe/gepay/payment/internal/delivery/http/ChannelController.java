package com.gepe.gepay.payment.internal.delivery.http;

import com.gepe.gepay.payment.api.PaymentApi;
import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.internal.delivery.http.res.ChannelRes;
import com.gepe.gepay.platform.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Referensi channel aktif (katalog publik, tanpa auth): donor memilih metode
 * bayar ({@code PAYIN}), creator memilih tujuan pencairan ({@code PAYOUT}).
 */
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
@Tag(name = "Channels", description = "Public channel catalog (no auth)")
public class ChannelController {

    private final PaymentApi paymentApi;

    @GetMapping
    @Operation(summary = "List active channels (direction=PAYIN for donors, PAYOUT for creators)")
    public ResponseEntity<ApiResponse<List<ChannelRes>>> list(
            @RequestParam(defaultValue = "PAYOUT") ChannelDirection direction) {
        List<ChannelRes> response = paymentApi.listChannels(direction)
                .stream().map(ChannelRes::from).toList();
        return ResponseEntity.ok(new ApiResponse<>(null, response));
    }
}
