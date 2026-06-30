package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.entity.InterestService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InterestServiceRepository extends JpaRepository<InterestService, UUID> {

    Optional<InterestService> findByCustomerIdAndServiceId(UUID customerId, UUID serviceId);
}
