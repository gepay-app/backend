package com.gepe.starter.identity.internal.service;

import com.gepe.starter.identity.api.IdentityApi;
import com.gepe.starter.identity.api.dtos.GrantRoleCommand;
import com.gepe.starter.identity.api.dtos.Role;
import com.gepe.starter.identity.api.dtos.UserPrincipal;
import com.gepe.starter.identity.api.dtos.UserResponse;
import com.gepe.starter.identity.api.dtos.UserStatus;
import com.gepe.starter.identity.internal.entity.User;
import com.gepe.starter.identity.internal.entity.UserRole;
import com.gepe.starter.identity.internal.repository.UserRepository;
import com.gepe.starter.identity.internal.repository.UserRoleRepository;
import com.gepe.starter.platform.exception.ServiceException;
import com.gepe.starter.platform.security.TestFirebaseAuthConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestFirebaseAuthConfig.class)
@Transactional
class IdentityServiceImplTest {

    private static final com.gepe.starter.identity.internal.entity.Role INTERNAL_CREATOR =
            com.gepe.starter.identity.internal.entity.Role.CREATOR;
    private static final com.gepe.starter.identity.internal.entity.Role INTERNAL_SUPER_ADMIN =
            com.gepe.starter.identity.internal.entity.Role.SUPER_ADMIN;

    @Autowired
    private IdentityApi identityApi;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void autoProvisionsNewUserAsUserOnFirstVerifiedLogin() {
        String authId = "uid-" + UUID.randomUUID();
        String email = randomEmail();

        UserPrincipal principal = identityApi.provisionOnFirstLogin(authId, email, "New User", true);

        assertThat(principal).isNotNull();
        assertThat(principal.roles()).containsExactly(Role.USER);
        assertThat(principal.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(principal.email()).isEqualTo(email);
        assertThat(userRepository.findByAuthId(authId)).isPresent();
    }

    @Test
    void rejectsUnverifiedLoginAndDoesNotProvision() {
        String authId = "uid-" + UUID.randomUUID();

        assertThat(identityApi.provisionOnFirstLogin(authId, randomEmail(), "X", false)).isNull();
        assertThat(userRepository.findByAuthId(authId)).isEmpty();
    }

    @Test
    void linksPreSeededUserAndPreservesPrivilegedRole() {
        // Baris "pre-seeded" (auth_id null) dengan role istimewa — dibuat di test
        // supaya tidak bergantung pada state seeder di DB.
        User seeded = User.create(randomEmail(), "Pre Seeded");
        userRepository.saveAndFlush(seeded);
        userRoleRepository.save(UserRole.grant(seeded.getId(), INTERNAL_CREATOR, null));

        String authId = "uid-" + UUID.randomUUID();
        UserPrincipal principal = identityApi.provisionOnFirstLogin(authId, seeded.getEmail(), null, true);

        assertThat(principal).isNotNull();
        assertThat(principal.roles()).contains(Role.CREATOR);
        assertThat(userRepository.findById(seeded.getId()).orElseThrow().getAuthId()).isEqualTo(authId);
    }

    @Test
    void resolvesDisabledUserWithDisabledStatus() {
        String authId = "uid-" + UUID.randomUUID();
        UserPrincipal created = identityApi.provisionOnFirstLogin(authId, randomEmail(), null, true);

        User user = userRepository.findById(created.userId()).orElseThrow();
        user.disable();
        userRepository.saveAndFlush(user);

        UserPrincipal resolved = identityApi.resolveByAuthId(authId);

        assertThat(resolved).isNotNull();
        assertThat(resolved.status()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void grantsAndRevokesRole() {
        String email = randomEmail();
        UserPrincipal created = identityApi.provisionOnFirstLogin("uid-" + UUID.randomUUID(), email, null, true);

        UserResponse granted = identityApi.grantRole(
                new GrantRoleCommand(email, Role.CREATOR), created.userId());
        assertThat(granted.roles()).contains(Role.USER, Role.CREATOR);

        UserResponse revoked = identityApi.revokeRole(
                new GrantRoleCommand(email, Role.CREATOR), created.userId());
        assertThat(revoked.roles()).containsExactly(Role.USER);
    }

    @Test
    void forbidsRevokingOwnSuperAdmin() {
        User admin = User.create(randomEmail(), "Admin");
        userRepository.saveAndFlush(admin);
        userRoleRepository.save(UserRole.grant(admin.getId(), INTERNAL_SUPER_ADMIN, null));

        assertThatThrownBy(() -> identityApi.revokeRole(
                new GrantRoleCommand(admin.getEmail(), Role.SUPER_ADMIN), admin.getId()))
                .isInstanceOf(ServiceException.class);
    }

    private static String randomEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
