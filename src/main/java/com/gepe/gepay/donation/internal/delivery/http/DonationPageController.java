package com.gepe.gepay.donation.internal.delivery.http;

import com.gepe.gepay.donation.internal.delivery.http.req.UpdateDonationPageReq;
import com.gepe.gepay.donation.internal.delivery.http.res.DonationPageRes;
import com.gepe.gepay.donation.internal.delivery.http.res.PublicDonationPageRes;
import com.gepe.gepay.donation.internal.delivery.http.res.RotateOverlayKeyRes;
import com.gepe.gepay.donation.internal.dto.UpdateDonationPageCommand;
import com.gepe.gepay.donation.internal.service.DonationPageService;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint halaman donasi: milik sendiri ({@code /donations/me/page}, auth) dan
 * publik ({@code /donation-pages/{slug}}, tanpa auth).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Donation pages", description = "Creator donation page + public page")
public class DonationPageController {

    private final DonationPageService donationPageService;
    private final MessageHelper messageHelper;

    @GetMapping("/donations/me/page")
    @Operation(summary = "Get (or auto-create) my donation page, including the secret overlayKey")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<DonationPageRes>> myPage() {
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("common.success"),
                DonationPageRes.from(donationPageService.getOrCreateMyPage())));
    }

    @PutMapping("/donations/me/page")
    @Operation(summary = "Update my donation page (displayName, title, description, imageUrl, slug)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<DonationPageRes>> updateMyPage(@Valid @RequestBody UpdateDonationPageReq req) {
        DonationPageRes response = DonationPageRes.from(donationPageService.updateMyPage(
                new UpdateDonationPageCommand(
                        req.displayName(), req.title(), req.description(), req.imageUrl(), req.slug())));
        return ResponseEntity.ok(new ApiResponse<>(messageHelper.get("donation.page_updated"), response));
    }

    @PostMapping("/donations/me/page/overlay-key/rotate")
    @Operation(summary = "Rotate my overlay key (invalidates the previous one)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<RotateOverlayKeyRes>> rotateOverlayKey() {
        RotateOverlayKeyRes response = new RotateOverlayKeyRes(donationPageService.rotateOverlayKey());
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("donation.overlay_key_rotated"), response));
    }

    @GetMapping("/donation-pages/{slug}")
    @Operation(summary = "Get a public donation page by username/slug (no auth, no overlayKey)")
    public ResponseEntity<ApiResponse<PublicDonationPageRes>> publicPage(@PathVariable("slug") String slug) {
        return ResponseEntity.ok(new ApiResponse<>(
                messageHelper.get("common.success"),
                PublicDonationPageRes.from(donationPageService.getPublicPageBySlug(slug))));
    }
}
