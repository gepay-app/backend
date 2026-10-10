package com.gepe.gepay.donation.internal.delivery.http;

import com.gepe.gepay.donation.internal.delivery.http.req.UpdateDonationPageReq;
import com.gepe.gepay.donation.internal.dto.DonationPageResponse;
import com.gepe.gepay.donation.internal.dto.PublicDonationPageResponse;
import com.gepe.gepay.donation.internal.dto.RotateOverlayKeyResponse;
import com.gepe.gepay.donation.internal.dto.UpdateDonationPageCommand;
import com.gepe.gepay.donation.internal.service.DonationPageService;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoint halaman donasi: milik sendiri ({@code /donations/me/page}, auth) dan
 * publik ({@code /donation-pages/{creatorId}}, tanpa auth).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class DonationPageController {

    private final DonationPageService donationPageService;
    private final MessageHelper messageHelper;

    @GetMapping("/donations/me/page")
    public ResponseEntity<ApiResponse<DonationPageResponse>> myPage() {
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("common.success"),
                donationPageService.getOrCreateMyPage()));
    }

    @PutMapping("/donations/me/page")
    public ResponseEntity<ApiResponse<DonationPageResponse>> updateMyPage(@Valid @RequestBody UpdateDonationPageReq req) {
        DonationPageResponse response = donationPageService.updateMyPage(
                new UpdateDonationPageCommand(req.displayName(), req.title(), req.description()));
        return ResponseEntity.ok(new ApiResponse<>(messageHelper.get("donation.page_updated"), response));
    }

    @PostMapping("/donations/me/page/overlay-key/rotate")
    public ResponseEntity<ApiResponse<RotateOverlayKeyResponse>> rotateOverlayKey() {
        String overlayKey = donationPageService.rotateOverlayKey();
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("donation.overlay_key_rotated"),
                new RotateOverlayKeyResponse(overlayKey)));
    }

    @GetMapping("/donation-pages/{creatorId}")
    public ResponseEntity<ApiResponse<PublicDonationPageResponse>> publicPage(@PathVariable("creatorId") UUID creatorId) {
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("common.success"),
                donationPageService.getPublicPage(creatorId)));
    }
}
