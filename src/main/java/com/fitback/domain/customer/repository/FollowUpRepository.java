package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.enums.FollowUpStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FollowUpRepository extends JpaRepository<FollowUp, UUID> {

    Optional<FollowUp> findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(UUID customerId, FollowUpStatus status);
}
