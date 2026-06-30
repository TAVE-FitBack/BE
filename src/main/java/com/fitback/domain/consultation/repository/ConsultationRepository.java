package com.fitback.domain.consultation.repository;

import com.fitback.domain.consultation.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    @Query("select coalesce(max(c.sessionNo), 0) from Consultation c where c.customer.id = :customerId")
    int findMaxSessionNoByCustomerId(UUID customerId);
}
