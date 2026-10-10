package com.gepe.gepay.donation.internal.delivery.http;

import com.gepe.gepay.donation.internal.dto.CreateDonationCommand;
import com.gepe.gepay.donation.internal.delivery.http.req.CreateDonationReq;
import com.gepe.gepay.donation.internal.delivery.http.res.CreatorDonationRes;
import com.gepe.gepay.donation.internal.delivery.http.res.DonationRes;
import com.gepe.gepay.donation.internal.service.DonationService;
import com.gepe.gepay.donation.internal.service.DonationStatusStreamService;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import com.gepe.gepay.platform.web.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/** Endpoint donasi: buat (publik), lihat status (publik), SSE status (publik). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/donations")
public class DonationController {

    private final DonationService donationService;
    private final DonationStatusStreamService statusStreamService;
    private final MessageHelper messageHelper;

    @PostMapping
    public ResponseEntity<ApiResponse<DonationRes>> createDonation(@Valid @RequestBody CreateDonationReq req) {
        CreateDonationCommand command = new CreateDonationCommand(
                req.idempotencyKey(),
                req.creatorId(),
                req.amount(),
                req.donorName(),
                req.donorEmail(),
                req.channelCode(),
                req.type(),
                req.message(),
                req.videoUrl(),
                req.isAnonymous());
        DonationRes response = DonationRes.from(donationService.createDonation(command));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(messageHelper.get("donation.created"), response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<CreatorDonationRes>>> myDonations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("common.success"),
                donationService.listMyDonations(page, size).map(CreatorDonationRes::from)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DonationRes>> getDonation(@PathVariable("id") UUID donationId) {
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("common.success"),
                DonationRes.from(donationService.getDonation(donationId))));
    }

    /**
     * SSE status pembayaran. Boleh putus kapan saja — donor refresh & baca
     * {@code GET /donations/{id}} sebagai sumber kebenaran.
     */
    @GetMapping("/{id}/stream")
    public SseEmitter stream(@PathVariable("id") UUID donationId) {
        var donation = donationService.getDonation(donationId);
        SseEmitter emitter = statusStreamService.subscribe(donationId);
        if ("PAID".equals(donation.status())) {
            statusStreamService.deliverPaid(donationId);
        }
        return emitter;
    }
}
