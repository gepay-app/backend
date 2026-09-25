package com.gepe.starter.identity.internal.repository;

import com.gepe.starter.identity.internal.entity.Role;
import com.gepe.starter.identity.internal.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    List<UserRole> findByUserId(UUID userId);

    boolean existsByUserIdAndRole(UUID userId, Role role);

    void deleteByUserIdAndRole(UUID userId, Role role);
}
