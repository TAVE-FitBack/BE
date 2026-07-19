package com.fitback.domain.customer.repository;

import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.enums.FollowUpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowUpRepository extends JpaRepository<FollowUp, UUID> {

    List<FollowUpStatus> ACTIVE_STATUSES = List.of(FollowUpStatus.PENDING, FollowUpStatus.SENT);

    Optional<FollowUp> findByIdAndCustomer_Store_Id(UUID id, UUID storeId);

    Optional<FollowUp> findFirstByCustomerIdOrderByCreatedAtDescIdDesc(UUID customerId);

    Optional<FollowUp> findFirstByCustomerIdAndStatusOrderByCreatedAtDescIdDesc(UUID customerId, FollowUpStatus status);

    Optional<FollowUp> findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(UUID customerId, FollowUpStatus status);

    Optional<FollowUp> findFirstByCustomerIdAndStatusInOrderByUpdatedAtDescCreatedAtDescIdDesc(
            UUID customerId,
            Collection<FollowUpStatus> statuses
    );

    default Optional<FollowUp> findActiveByCustomerId(UUID customerId) {
        return findFirstByCustomerIdAndStatusInOrderByUpdatedAtDescCreatedAtDescIdDesc(
                customerId,
                ACTIVE_STATUSES
        );
    }

    @Query("""
            select f
            from FollowUp f
            join fetch f.customer customer
            join fetch customer.store
            join fetch f.consultation consultation
            where f.id in :followUpIds
              and customer.id = :customerId
              and customer.store.id = :storeId
            """)
    List<FollowUp> findAllTimelineDetailsByIdsAndCustomerIdAndStoreId(
            @Param("followUpIds") Collection<UUID> followUpIds,
            @Param("customerId") UUID customerId,
            @Param("storeId") UUID storeId
    );
}
