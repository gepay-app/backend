package com.gepe.starter.identity.internal.service;

import com.gepe.starter.identity.api.IdentityApi;
import com.gepe.starter.identity.api.dtos.GrantRoleCommand;
import com.gepe.starter.identity.api.dtos.Role;
import com.gepe.starter.identity.api.dtos.UserPrincipal;
import com.gepe.starter.identity.api.dtos.UserResponse;
import com.gepe.starter.identity.api.dtos.UserStatus;
import com.gepe.starter.identity.internal.cache.PrincipalCache;
import com.gepe.starter.identity.internal.config.IdentityCacheConfig;
import com.gepe.starter.identity.internal.entity.User;
import com.gepe.starter.identity.internal.entity.UserRole;
import com.gepe.starter.identity.internal.exception.IdentityError;
import com.gepe.starter.identity.internal.repository.UserRepository;
import com.gepe.starter.identity.internal.repository.UserRoleRepository;
import com.gepe.starter.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class IdentityServiceImpl implements IdentityApi {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PrincipalCache principalCache;

    /** Principal apa adanya (termasuk DISABLED) — filter yang memutuskan tolak/terima. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = IdentityCacheConfig.PRINCIPAL_BY_AUTH_ID, key = "#authId", unless = "#result == null")
    public UserPrincipal resolveByAuthId(String authId) {
        return userRepository.findByAuthId(authId)
                .map(this::toPrincipal)
                .orElse(null);
    }

    @Override
    @Transactional
    public UserPrincipal provisionOnFirstLogin(String authId, String email, String name, boolean emailVerified) {
        // Anti email-claiming: hanya email yang benar-benar dimiliki user
        // (terverifikasi Firebase) yang boleh dipakai untuk link/provision.
        if (!emailVerified || email == null || email.isBlank()) {
            log.debug("Login rejected — email missing or unverified: authId={}", authId);
            return null;
        }

        Optional<User> linked = userRepository.findByAuthId(authId);
        if (linked.isPresent()) {
            return toPrincipal(linked.get());
        }

        Optional<User> seeded = userRepository.findByEmailIgnoreCase(email);
        if (seeded.isPresent()) {
            User user = seeded.get();
            if (user.getStatus() == User.Status.DISABLED) {
                // Kembalikan principal DISABLED (bukan null) supaya filter bisa
                // membalas 403 dengan alasan jelas. Jangan link auth_id dulu.
                log.debug("Disabled user attempted login: id={}", user.getId());
                return toPrincipal(user);
            }
            if (user.getAuthId() != null && !user.getAuthId().equals(authId)) {
                log.warn("Login rejected — email already linked to another authId: id={}", user.getId());
                return null;
            }
            user.linkAuthId(authId);
            if (user.getName() == null) {
                user.updateProfile(name);
            }
            log.info("Pre-seeded user linked on first login: id={}, email={}", user.getId(), email);
            return toPrincipal(user);
        }

        User user = User.create(email, name);
        user.linkAuthId(authId);
        userRepository.saveAndFlush(user); // flush: unique email constraint gatuk di transaksi ini
        userRoleRepository.save(UserRole.grant(user.getId(), toInternalRole(Role.USER), null));
        log.info("User auto-provisioned on first login: id={}, email={}", user.getId(), email);
        return toPrincipal(user);
    }

    @Override
    @Transactional
    public UserResponse grantRole(GrantRoleCommand cmd, UUID grantedBy) {
        User user = findActiveUser(cmd.email());
        var internalRole = toInternalRole(cmd.role());
        if (userRoleRepository.existsByUserIdAndRole(user.getId(), internalRole)) {
            throw new ServiceException(IdentityError.ROLE_ALREADY_GRANTED, cmd.role());
        }
        userRoleRepository.save(UserRole.grant(user.getId(), internalRole, grantedBy));
        principalCache.evict(user.getAuthId());
        log.info("Role granted: userId={}, role={}, by={}", user.getId(), cmd.role(), grantedBy);
        return toUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse revokeRole(GrantRoleCommand cmd, UUID actorId) {
        User user = findActiveUser(cmd.email());
        if (user.getId().equals(actorId) && cmd.role() == Role.SUPER_ADMIN) {
            throw new ServiceException(IdentityError.ROLE_CHANGE_ON_SELF_NOT_ALLOWED, cmd.role());
        }
        var internalRole = toInternalRole(cmd.role());
        if (!userRoleRepository.existsByUserIdAndRole(user.getId(), internalRole)) {
            throw new ServiceException(IdentityError.ROLE_NOT_GRANTED, cmd.role());
        }
        userRoleRepository.deleteByUserIdAndRole(user.getId(), internalRole);
        principalCache.evict(user.getAuthId());
        log.info("Role revoked: userId={}, role={}, by={}", user.getId(), cmd.role(), actorId);
        return toUserResponse(user);
    }

    private User findActiveUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ServiceException(IdentityError.USER_NOT_FOUND, email));
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new ServiceException(IdentityError.USER_DISABLED);
        }
        return user;
    }

    private UserPrincipal toPrincipal(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getName(),
                rolesOf(user.getId()),
                mapStatus(user.getStatus()));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                mapStatus(user.getStatus()),
                rolesOf(user.getId()));
    }

    private Set<Role> rolesOf(UUID userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> Role.valueOf(ur.getRole().name()))
                .collect(Collectors.toSet());
    }

    private UserStatus mapStatus(User.Status status) {
        return switch (status) {
            case ACTIVE -> UserStatus.ACTIVE;
            case DISABLED -> UserStatus.DISABLED;
        };
    }

    private static com.gepe.starter.identity.internal.entity.Role toInternalRole(Role role) {
        return com.gepe.starter.identity.internal.entity.Role.valueOf(role.name());
    }
}
