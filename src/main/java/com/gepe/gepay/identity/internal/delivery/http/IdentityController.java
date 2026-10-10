package com.gepe.gepay.identity.internal.delivery.http;

import com.gepe.gepay.identity.api.CurrentUser;
import com.gepe.gepay.identity.api.IdentityApi;
import com.gepe.gepay.identity.api.dtos.GrantRoleCommand;
import com.gepe.gepay.identity.internal.delivery.http.req.GrantRoleReq;
import com.gepe.gepay.identity.internal.delivery.http.res.MeRes;
import com.gepe.gepay.identity.internal.delivery.http.res.UserRes;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/identities")
public class IdentityController {
    private final IdentityApi identityApi;
    private final CurrentUser currentUser;
    private final MessageHelper messageHelper;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeRes>> me() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(messageHelper.get("common.success"), MeRes.from(currentUser.get())));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserRes>> grantRole(@Valid @RequestBody GrantRoleReq req) {
        UserRes res = UserRes.from(identityApi.grantRole(
                new GrantRoleCommand(req.email(), req.role()), currentUser.userId()));
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(messageHelper.get("common.success"), res));
    }

    @DeleteMapping("/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserRes>> revokeRole(@Valid @RequestBody GrantRoleReq req) {
        UserRes res = UserRes.from(identityApi.revokeRole(
                new GrantRoleCommand(req.email(), req.role()), currentUser.userId()));
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(messageHelper.get("common.success"), res));
    }
}
