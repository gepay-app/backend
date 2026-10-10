package com.gepe.gepay.payment.internal.delivery.http;

import com.gepe.gepay.payment.api.WithdrawalApi;
import com.gepe.gepay.payment.api.dtos.PayoutDestinationCreateCommand;
import com.gepe.gepay.payment.internal.delivery.http.req.CreatePayoutDestinationRequest;
import com.gepe.gepay.payment.internal.delivery.http.res.PayoutDestinationRes;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasRole('CREATOR')")
@Tag(name = "Payout destinations", description = "Creator bank accounts for withdrawals (CREATOR only)")
@SecurityRequirement(name = "bearerAuth")
public class PayoutDestinationController {

    private final WithdrawalApi withdrawalApi;
    private final MessageHelper messageHelper;

    @PostMapping
    @Operation(summary = "Add a payout destination (first one becomes default)")
    public ResponseEntity<ApiResponse<PayoutDestinationRes>> create(
            @Valid @RequestBody CreatePayoutDestinationRequest request) {
        PayoutDestinationRes response = PayoutDestinationRes.from(withdrawalApi.createPayoutDestination(
                new PayoutDestinationCreateCommand(
                        request.channelId(),
                        request.accountNumber(),
                        request.accountName(),
                        request.bankCode()
                )
        ));
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("payment.payout_destination_created"), response));
    }

    @GetMapping
    @Operation(summary = "List my payout destinations, newest first")
    public ResponseEntity<ApiResponse<List<PayoutDestinationRes>>> list() {
        List<PayoutDestinationRes> response = withdrawalApi.listPayoutDestinations()
                .stream().map(PayoutDestinationRes::from).toList();
        return ResponseEntity.ok(new ApiResponse<>(null, response));
    }

    @PostMapping("/{id}/default")
    @Operation(summary = "Set the default payout destination")
    public ResponseEntity<ApiResponse<PayoutDestinationRes>> setDefault(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("common.updated"),
                PayoutDestinationRes.from(withdrawalApi.setDefaultPayoutDestination(id))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a payout destination")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        withdrawalApi.deactivatePayoutDestination(id);
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("payment.payout_destination_deleted"), null));
    }
}
