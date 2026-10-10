package com.gepe.gepay.payment.internal.delivery.http;

import com.gepe.gepay.payment.api.WithdrawalApi;
import com.gepe.gepay.payment.api.dtos.PayoutDestinationCreateCommand;
import com.gepe.gepay.payment.api.dtos.PayoutDestinationResponse;
import com.gepe.gepay.payment.internal.delivery.http.req.CreatePayoutDestinationRequest;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payout-destinations")
@RequiredArgsConstructor
public class PayoutDestinationController {

    private final WithdrawalApi withdrawalApi;
    private final MessageHelper messageHelper;

    @PostMapping
    public ResponseEntity<ApiResponse<PayoutDestinationResponse>> create(
            @Valid @RequestBody CreatePayoutDestinationRequest request) {
        PayoutDestinationResponse response = withdrawalApi.createPayoutDestination(
                new PayoutDestinationCreateCommand(
                        request.channelId(),
                        request.accountNumber(),
                        request.accountName(),
                        request.bankCode()
                )
        );
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("payment.payout_destination_created"), response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PayoutDestinationResponse>>> list() {
        return ResponseEntity.ok(new ApiResponse<>(null, withdrawalApi.listPayoutDestinations()));
    }

    @PostMapping("/{id}/default")
    public ResponseEntity<ApiResponse<PayoutDestinationResponse>> setDefault(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("common.updated"), withdrawalApi.setDefaultPayoutDestination(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        withdrawalApi.deactivatePayoutDestination(id);
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("payment.payout_destination_deleted"), null));
    }
}
