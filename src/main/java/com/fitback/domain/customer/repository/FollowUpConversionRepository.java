package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.entity.FollowUpConversion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FollowUpConversionRepository extends JpaRepository<FollowUpConversion, UUID> {

    boolean existsByCustomer_Id(UUID customerId);
}
