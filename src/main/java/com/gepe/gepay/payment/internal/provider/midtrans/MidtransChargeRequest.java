package com.gepe.gepay.payment.internal.provider.midtrans;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MidtransChargeRequest(
        String payment_type,
        TransactionDetails transaction_details,
//            List<ItemDetail> item_details,
        CustomerDetails customer_details,
        QrisConfig qris,
        BankTransferConfig bank_transfer
) {
    public record TransactionDetails(
            String order_id,
            long gross_amount
    ) {
    }

//        record ItemDetail(
//                String id,
//                long price,
//                int quantity,
//                String name
//        ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomerDetails(
            String first_name,
            String last_name,
            String email,
            String phone
    ) {
    }

    public record QrisConfig(
            String acquirer
    ) {
    }

    public record BankTransferConfig(
            String bank
    ) {
    }
}
