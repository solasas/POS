package com.pos.registers.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.registers.entity.Register;

public interface RegisterRepository extends JpaRepository<Register, UUID> {

    Optional<Register> findByCodeIgnoreCaseAndActiveTrue(String code);
}
