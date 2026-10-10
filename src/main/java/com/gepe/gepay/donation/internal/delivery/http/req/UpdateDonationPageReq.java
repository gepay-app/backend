package com.gepe.gepay.donation.internal.delivery.http.req;

import jakarta.validation.constraints.Size;

public record UpdateDonationPageReq(
        @Size(max = 120) String displayName,
        @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Size(max = 500) String imageUrl,
        @Size(max = 60) String slug
) {
}
