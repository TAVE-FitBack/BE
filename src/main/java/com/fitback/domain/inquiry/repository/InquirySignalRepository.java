package com.fitback.domain.inquiry.repository;

import com.fitback.domain.inquiry.entity.InquirySignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InquirySignalRepository extends JpaRepository<InquirySignal, UUID> {

    List<InquirySignal> findByInquiryIdOrderByDisplayOrderAsc(UUID inquiryId);
}
