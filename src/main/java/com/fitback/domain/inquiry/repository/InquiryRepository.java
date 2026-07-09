package com.fitback.domain.inquiry.repository;

import com.fitback.domain.inquiry.entity.Inquiry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {

    Optional<Inquiry> findByIdAndStore_Id(UUID id, UUID storeId);

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
