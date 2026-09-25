package com.gepe.starter.identity.internal.delivery.http;

import com.gepe.starter.identity.api.CurrentUser;
import com.gepe.starter.identity.api.IdentityApi;
import com.gepe.starter.identity.api.dtos.GrantRoleCommand;
import com.gepe.starter.identity.api.dtos.UserPrincipal;
import com.gepe.starter.identity.api.dtos.UserResponse;
import com.gepe.starter.identity.internal.delivery.http.req.GrantRoleReq;
import com.gepe.starter.platform.i18n.MessageHelper;
import com.gepe.starter.platform.web.response.ApiResponse;
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
    public ResponseEntity<ApiResponse<UserPrincipal>> me() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(messageHelper.get("common.success"), currentUser.get()));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> grantRole(@Valid @RequestBody GrantRoleReq req) {
        UserResponse res = identityApi.grantRole(
                new GrantRoleCommand(req.email(), req.role()), currentUser.userId());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(messageHelper.get("common.success"), res));
    }

    @DeleteMapping("/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> revokeRole(@Valid @RequestBody GrantRoleReq req) {
        UserResponse res = identityApi.revokeRole(
                new GrantRoleCommand(req.email(), req.role()), currentUser.userId());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponse<>(messageHelper.get("common.success"), res));
    }
}
