package com.pos.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.auth.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCaseAndActiveTrue(String email);

    List<User> findAllByActiveTrueAndPinHashIsNotNull();
}
