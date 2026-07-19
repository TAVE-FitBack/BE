package com.fitback.domain.inquiry.repository;

import com.fitback.domain.inquiry.entity.Inquiry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {

    Optional<Inquiry> findByIdAndStore_Id(UUID id, UUID storeId);

    @Query("""
            select i
            from Inquiry i
            join fetch i.service
            join fetch i.user
            left join fetch i.convertedConsultation
            where i.id in :inquiryIds
              and i.store.id = :storeId
            """)
    List<Inquiry> findAllTimelineDetailsByIdsAndStoreId(
            @Param("inquiryIds") Collection<UUID> inquiryIds,
            @Param("storeId") UUID storeId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i
            from Inquiry i
            where i.id = :inquiryId
              and i.store.id = :storeId
            """)
    Optional<Inquiry> findByIdAndStoreIdForUpdate(
            @Param("inquiryId") UUID inquiryId,
            @Param("storeId") UUID storeId
    );
}
