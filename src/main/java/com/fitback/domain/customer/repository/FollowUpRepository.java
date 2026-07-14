package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.enums.FollowUpStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FollowUpRepository extends JpaRepository<FollowUp, UUID> {

    Optional<FollowUp> findByIdAndCustomer_Store_Id(UUID id, UUID storeId);

    Optional<FollowUp> findFirstByCustomerIdOrderByCreatedAtDescIdDesc(UUID customerId);

    Optional<FollowUp> findFirstByCustomerIdAndStatusOrderByCreatedAtDescIdDesc(UUID customerId, FollowUpStatus status);

    Optional<FollowUp> findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(UUID customerId, FollowUpStatus status);
}
