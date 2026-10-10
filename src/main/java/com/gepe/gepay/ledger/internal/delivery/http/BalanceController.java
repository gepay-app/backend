package com.gepe.gepay.ledger.internal.delivery.http;

import com.gepe.gepay.identity.api.CurrentUser;
import com.gepe.gepay.ledger.api.LedgerApi;
import com.gepe.gepay.ledger.internal.delivery.http.res.BalanceRes;
import com.gepe.gepay.platform.web.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saldo user (pending / available / hold). Selalu ter-scope ke current user;
 * akun ledger di-provision lazy di service.
 */
@RestController
@RequestMapping("/api/v1/balance")
@RequiredArgsConstructor
public class BalanceController {

    private final LedgerApi ledgerApi;
    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<ApiResponse<BalanceRes>> getBalance() {
        return ResponseEntity.ok(new ApiResponse<>(
                null, BalanceRes.from(ledgerApi.getUserBalance(currentUser.userId().toString()))));
    }
}
