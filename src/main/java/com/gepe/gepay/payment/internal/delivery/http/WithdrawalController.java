package com.gepe.gepay.payment.internal.delivery.http;

import com.gepe.gepay.payment.api.WithdrawalApi;
import com.gepe.gepay.payment.api.dtos.WithdrawalCreateCommand;
import com.gepe.gepay.payment.internal.delivery.http.req.CreateWithdrawalRequest;
import com.gepe.gepay.payment.internal.delivery.http.res.WithdrawalConfigRes;
import com.gepe.gepay.payment.internal.delivery.http.res.WithdrawalRes;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import com.gepe.gepay.platform.web.response.CursorPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Withdrawals", description = "Creator payout/withdrawal (CREATOR only)")
@SecurityRequirement(name = "bearerAuth")
public class WithdrawalController {

    private final WithdrawalApi withdrawalApi;
    private final MessageHelper messageHelper;

    @PostMapping
    @Operation(summary = "Request a withdrawal (holds available balance; idempotent via idempotencyKey)")
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
    @Operation(summary = "List my withdrawals, newest first, cursor-paginated")
    public ResponseEntity<ApiResponse<CursorPage<WithdrawalRes>>> list(
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(new ApiResponse<>(
                null, withdrawalApi.listWithdrawals(cursor, size).map(WithdrawalRes::from)));
    }

    @GetMapping("/config")
    @Operation(summary = "Effective min/max amount and fee for a payout destination")
    public ResponseEntity<ApiResponse<WithdrawalConfigRes>> config(
            @RequestParam UUID destinationId) {
        return ResponseEntity.ok(new ApiResponse<>(
                null, WithdrawalConfigRes.from(withdrawalApi.getWithdrawalConfig(destinationId))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a withdrawal by id")
    public ResponseEntity<ApiResponse<WithdrawalRes>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(null, WithdrawalRes.from(withdrawalApi.getWithdrawal(id))));
    }
}
