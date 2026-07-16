package com.fitback.domain.store.repository;

import com.fitback.domain.store.entity.InflowPathOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InflowPathOptionRepository extends JpaRepository<InflowPathOption, UUID> {

    List<InflowPathOption> findAllByStoreIdAndActiveTrueOrderByDisplayOrderAsc(UUID storeId);

    Optional<InflowPathOption> findByIdAndStoreIdAndActiveTrue(UUID id, UUID storeId);

    List<InflowPathOption> findAllByStoreIdOrderByDisplayOrder(UUID storeId);

    Optional<InflowPathOption> findByIdAndStoreId(UUID id, UUID storeId);
}
