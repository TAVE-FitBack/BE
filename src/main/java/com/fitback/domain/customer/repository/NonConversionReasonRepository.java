package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.entity.NonConversionReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NonConversionReasonRepository extends JpaRepository<NonConversionReason, UUID> {

    List<NonConversionReason> findAllByCustomerIdOrderByUpdatedAtDesc(UUID customerId);
}
