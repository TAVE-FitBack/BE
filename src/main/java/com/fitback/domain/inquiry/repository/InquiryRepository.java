package com.fitback.domain.inquiry.repository;

import com.fitback.domain.inquiry.entity.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {

    Optional<Inquiry> findByIdAndStore_Id(UUID id, UUID storeId);
}
