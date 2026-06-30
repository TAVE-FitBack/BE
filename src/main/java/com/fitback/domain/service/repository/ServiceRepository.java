package com.fitback.domain.service.repository;

import com.fitback.domain.service.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRepository extends JpaRepository<Service, UUID> {

    List<Service> findAllByStoreIdAndActiveTrue(UUID storeId);

    Optional<Service> findByIdAndStoreId(UUID id, UUID storeId);
}
