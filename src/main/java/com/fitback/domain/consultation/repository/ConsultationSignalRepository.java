package com.fitback.domain.consultation.repository;

import com.fitback.domain.consultation.entity.ConsultationSignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsultationSignalRepository extends JpaRepository<ConsultationSignal, UUID> {

    List<ConsultationSignal> findByConsultationIdOrderByDisplayOrderAsc(UUID consultationId);
}
