package com.fitback.domain.consultation.repository;

import com.fitback.domain.consultation.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    Optional<Consultation> findFirstByCustomerIdOrderBySessionNoDesc(UUID customerId);

    @Query("""
            select c
            from Consultation c
            join fetch c.consultedService
            join fetch c.user
            join fetch c.customer customer
            join fetch customer.store
            where c.id in :consultationIds
              and customer.id = :customerId
              and customer.store.id = :storeId
            """)
    List<Consultation> findAllTimelineDetailsByIdsAndCustomerIdAndStoreId(
            @Param("consultationIds") Collection<UUID> consultationIds,
            @Param("customerId") UUID customerId,
            @Param("storeId") UUID storeId
    );
}
