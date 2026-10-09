package com.gepe.gepay.payment.internal.delivery.http;

import com.gepe.gepay.payment.api.PaymentApi;
import com.gepe.gepay.payment.api.dtos.CreatePaymentCommand;
import com.gepe.gepay.payment.api.dtos.CreatePaymentResult;
import com.gepe.gepay.payment.api.dtos.PaymentResponse;
import com.gepe.gepay.payment.internal.delivery.http.req.CreatePaymentRequest;
import com.gepe.gepay.platform.web.response.ApiResponse;
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
    public ResponseEntity<ApiResponse<CreatePaymentResult>> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
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

        CreatePaymentResult result = paymentApi.createPayment(command);
        return ResponseEntity.ok(new ApiResponse<>("Payment initiated successfully", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable("id") UUID paymentId) {
        PaymentResponse response = paymentApi.getPayment(paymentId);
        return ResponseEntity.ok(new ApiResponse<>("Payment retrieved successfully", response));
    }
}
