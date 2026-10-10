package com.gepe.gepay.payment.internal.delivery.http;

import com.gepe.gepay.payment.api.WithdrawalApi;
import com.gepe.gepay.payment.api.dtos.WithdrawalCreateCommand;
import com.gepe.gepay.payment.internal.delivery.http.req.CreateWithdrawalRequest;
import com.gepe.gepay.payment.internal.delivery.http.res.WithdrawalConfigRes;
import com.gepe.gepay.payment.internal.delivery.http.res.WithdrawalRes;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import com.gepe.gepay.platform.web.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CREATOR')")
public class WithdrawalController {

    private final WithdrawalApi withdrawalApi;
    private final MessageHelper messageHelper;

    @PostMapping
    public ResponseEntity<ApiResponse<WithdrawalRes>> create(
            @Valid @RequestBody CreateWithdrawalRequest request) {
        WithdrawalRes response = WithdrawalRes.from(withdrawalApi.createWithdrawal(
                new WithdrawalCreateCommand(
                        request.idempotencyKey(),
                        request.destinationId(),
                        request.requestedAmount()
                )
        ));
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("payment.withdrawal_created"), response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WithdrawalRes>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(new ApiResponse<>(
                null, withdrawalApi.listWithdrawals(page, size).map(WithdrawalRes::from)));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<WithdrawalConfigRes>> config(
            @RequestParam UUID destinationId) {
        return ResponseEntity.ok(new ApiResponse<>(
                null, WithdrawalConfigRes.from(withdrawalApi.getWithdrawalConfig(destinationId))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WithdrawalRes>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(null, WithdrawalRes.from(withdrawalApi.getWithdrawal(id))));
    }
}
