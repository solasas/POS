package com.pos.common.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.common.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
