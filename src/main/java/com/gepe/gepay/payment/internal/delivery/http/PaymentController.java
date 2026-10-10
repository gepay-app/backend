package com.gepe.gepay.payment.internal.delivery.http;

import com.gepe.gepay.payment.api.PaymentApi;
import com.gepe.gepay.payment.api.dtos.CreatePaymentCommand;
import com.gepe.gepay.payment.internal.delivery.http.req.CreatePaymentRequest;
import com.gepe.gepay.payment.internal.delivery.http.res.CreatePaymentRes;
import com.gepe.gepay.payment.internal.delivery.http.res.PaymentRes;
import com.gepe.gepay.platform.web.response.ApiResponse;
import com.gepe.gepay.platform.web.response.CursorPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Generic payment engine (payin) — earnings, attempts, fees")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentApi paymentApi;

    @PostMapping
    @Operation(summary = "Create a payment and initiate a provider charge (idempotent via idempotencyKey)")
    public ResponseEntity<ApiResponse<CreatePaymentRes>> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        CreatePaymentCommand command = new CreatePaymentCommand(
                request.idempotencyKey(),
                request.type(),
                request.userId(),
                request.payerId(),
                request.channelCode(),
                request.amount(),
                request.customerName(),
                request.customerEmail(),
                request.metadata()
        );

        CreatePaymentRes result = CreatePaymentRes.from(paymentApi.createPayment(command));
        return ResponseEntity.ok(new ApiResponse<>("Payment initiated successfully", result));
    }

    @GetMapping
    @Operation(summary = "List my payments (earnings), newest first, cursor-paginated")
    public ResponseEntity<ApiResponse<CursorPage<PaymentRes>>> listPayments(
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(new ApiResponse<>(
                "Payments retrieved successfully",
                paymentApi.listPayments(cursor, size).map(PaymentRes::from)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a payment by id")
    public ResponseEntity<ApiResponse<PaymentRes>> getPayment(@PathVariable("id") UUID paymentId) {
        PaymentRes response = PaymentRes.from(paymentApi.getPayment(paymentId));
        return ResponseEntity.ok(new ApiResponse<>("Payment retrieved successfully", response));
    }
}
