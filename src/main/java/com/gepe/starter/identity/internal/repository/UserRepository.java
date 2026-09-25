package com.gepe.starter.identity.internal.repository;

import com.gepe.starter.identity.internal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByAuthId(String authId);
    Optional<User> findByEmailIgnoreCase(String email);
}
