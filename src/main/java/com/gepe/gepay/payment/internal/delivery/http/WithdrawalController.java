package com.gepe.gepay.payment.internal.delivery.http;

import com.gepe.gepay.payment.api.WithdrawalApi;
import com.gepe.gepay.payment.api.dtos.WithdrawalCreateCommand;
import com.gepe.gepay.payment.api.dtos.WithdrawalResponse;
import com.gepe.gepay.payment.internal.delivery.http.req.CreateWithdrawalRequest;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalApi withdrawalApi;
    private final MessageHelper messageHelper;

    @PostMapping
    public ResponseEntity<ApiResponse<WithdrawalResponse>> create(
            @Valid @RequestBody CreateWithdrawalRequest request) {
        WithdrawalResponse response = withdrawalApi.createWithdrawal(
                new WithdrawalCreateCommand(
                        request.idempotencyKey(),
                        request.destinationId(),
                        request.requestedAmount()
                )
        );
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("payment.withdrawal_created"), response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(null, withdrawalApi.getWithdrawal(id)));
    }
}
