package com.gepe.gepay.payment.internal.delivery.http;

import com.gepe.gepay.payment.api.PaymentApi;
import com.gepe.gepay.payment.api.dtos.CreatePaymentCommand;
import com.gepe.gepay.payment.internal.delivery.http.req.CreatePaymentRequest;
import com.gepe.gepay.payment.internal.delivery.http.res.CreatePaymentRes;
import com.gepe.gepay.payment.internal.delivery.http.res.PaymentRes;
import com.gepe.gepay.platform.web.response.ApiResponse;
import com.gepe.gepay.platform.web.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentApi paymentApi;

    @PostMapping
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
    public ResponseEntity<ApiResponse<PageResponse<PaymentRes>>> listPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(new ApiResponse<>(
                "Payments retrieved successfully",
                paymentApi.listPayments(page, size).map(PaymentRes::from)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentRes>> getPayment(@PathVariable("id") UUID paymentId) {
        PaymentRes response = PaymentRes.from(paymentApi.getPayment(paymentId));
        return ResponseEntity.ok(new ApiResponse<>("Payment retrieved successfully", response));
    }
}
