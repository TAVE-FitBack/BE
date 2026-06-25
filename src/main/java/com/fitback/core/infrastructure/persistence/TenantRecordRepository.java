package com.fitback.core.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRecordRepository extends JpaRepository<TenantRecord, UUID> {
    List<TenantRecord> findByTenantIdAndCollection(String tenantId, String collection);

    List<TenantRecord> findByCollection(String collection);

    Optional<TenantRecord> findByIdAndTenantIdAndCollection(UUID id, String tenantId, String collection);
}
