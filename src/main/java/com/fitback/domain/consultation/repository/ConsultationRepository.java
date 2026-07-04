package com.fitback.domain.consultation.repository;

import com.fitback.domain.consultation.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {
}
