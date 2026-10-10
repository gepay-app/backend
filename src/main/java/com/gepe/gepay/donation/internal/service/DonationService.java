package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.config.DonationOverlayProperties;
import com.gepe.gepay.donation.internal.dto.CreateDonationCommand;
import com.gepe.gepay.donation.internal.dto.DonationResponse;
import com.gepe.gepay.donation.internal.entity.Donation;
import com.gepe.gepay.donation.internal.entity.DonationPage;
import com.gepe.gepay.donation.internal.entity.DonationType;
import com.gepe.gepay.donation.internal.exception.DonationError;
import com.gepe.gepay.donation.internal.repository.DonationRepository;
import com.gepe.gepay.donation.internal.util.YouTubeUrlParser;
import com.gepe.gepay.payment.api.PaymentApi;
import com.gepe.gepay.payment.api.dtos.CreatePaymentCommand;
import com.gepe.gepay.payment.api.dtos.CreatePaymentResult;
import com.gepe.gepay.platform.exception.ServiceException;
import com.gepe.gepay.platform.exception.ValidationException;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ValidationError;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orkestrator pembuatan/lihat donasi. <strong>Tidak</strong> {@code @Transactional}
 * pada {@link #createDonation}: panggilan {@code PaymentApi.createPayment} adalah
 * remote I/O. Penulisan DB didelegasikan ke {@link DonationWriter}.
 */
@Service
@RequiredArgsConstructor
public class DonationService {

    private static final String PAYMENT_TYPE = "DONATION";

    private final DonationRepository donationRepository;
    private final DonationPageService donationPageService;
    private final DonationWriter donationWriter;
    private final PaymentApi paymentApi;
    private final DonationOverlayProperties overlayProperties;
    private final MessageHelper messageHelper;

    public DonationResponse createDonation(CreateDonationCommand command) {
        Validated validated = validateAndParse(command);

        Donation donation = donationRepository.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
        if (donation != null && donation.getPaymentId() != null) {
            return toResponse(donation);
        }

        if (donation == null) {
            DonationPage page = donationPageService.requireByCreatorId(command.creatorId());
            Donation draft = Donation.create(
                    page.getId(),
                    page.getCreatorId(),
                    command.idempotencyKey(),
                    command.channelCode(),
                    command.donorName(),
                    command.donorEmail(),
                    command.amount(),
                    validated.type(),
                    validated.message(),
                    validated.videoId(),
                    command.isAnonymous());
            try {
                donation = donationWriter.insertPending(draft);
            } catch (DataIntegrityViolationException e) {
                donation = donationRepository.findByIdempotencyKey(command.idempotencyKey())
                        .orElseThrow(() -> new ServiceException(DonationError.DONATION_NOT_FOUND, command.idempotencyKey()));
                if (donation.getPaymentId() != null) {
                    return toResponse(donation);
                }
            }
        }

        CreatePaymentCommand paymentCommand = new CreatePaymentCommand(
                "DONATION:" + donation.getId(),
                PAYMENT_TYPE,
                donation.getCreatorId(),
                null,
                donation.getChannelCode(),
                donation.getAmount(),
                donation.getDonorName(),
                donation.getDonorEmail(),
                Map.of("donationId", donation.getId().toString(), "donationType", donation.getType().name()));

        CreatePaymentResult result = paymentApi.createPayment(paymentCommand);

        Donation updated = donationWriter.attachPayment(
                donation.getId(),
                result.payment().id(),
                result.attempt().paymentReferenceNumber(),
                result.attempt().expiresAt(),
                result.payment().totalChargedAmount());
        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public DonationResponse getDonation(UUID donationId) {
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ServiceException(DonationError.DONATION_NOT_FOUND, donationId));
        return toResponse(donation);
    }

    private Validated validateAndParse(CreateDonationCommand command) {
        List<ValidationError> errors = new ArrayList<>();

        DonationType type = parseType(command.type(), errors);
        String message = trimToNull(command.message());
        String videoId = null;

        int maxChars = overlayProperties.getText().getMaxCharacters();
        if (message != null && message.length() > maxChars) {
            errors.add(new ValidationError("message", messageHelper.get("donation.message_too_long", maxChars)));
        }

        if (type == DonationType.TEXT) {
            if (message == null) {
                errors.add(new ValidationError("message", messageHelper.get("donation.message_required")));
            }
            if (trimToNull(command.videoUrl()) != null) {
                errors.add(new ValidationError("videoUrl", messageHelper.get("donation.video_not_allowed_for_text")));
            }
        } else if (type == DonationType.YOUTUBE) {
            String videoUrl = trimToNull(command.videoUrl());
            if (videoUrl == null) {
                errors.add(new ValidationError("videoUrl", messageHelper.get("donation.video_required")));
            } else {
                var parsed = YouTubeUrlParser.parse(videoUrl);
                if (parsed.isEmpty()) {
                    errors.add(new ValidationError("videoUrl", messageHelper.get("donation.invalid_youtube_url")));
                } else {
                    videoId = parsed.get().videoId();
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Validated(type, message, videoId);
    }

    private DonationType parseType(String raw, List<ValidationError> errors) {
        String value = trimToNull(raw);
        if (value != null) {
            try {
                return DonationType.valueOf(value.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fallthrough
            }
        }
        errors.add(new ValidationError("type", messageHelper.get("donation.invalid_type")));
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DonationResponse toResponse(Donation donation) {
        return new DonationResponse(
                donation.getId(),
                donation.getPaymentId(),
                donation.getCreatorId(),
                donation.getStatus().name(),
                donation.getType().name(),
                donation.getAmount(),
                donation.getTotalChargedAmount(),
                donation.getChannelCode(),
                donation.getMessage(),
                donation.getVideoId(),
                donation.getPaymentReferenceNumber(),
                donation.getPaymentExpiresAt(),
                donation.getCreatedAt(),
                donation.getPaidAt());
    }

    private record Validated(DonationType type, String message, String videoId) {
    }
}
