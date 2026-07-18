package com.fitback.domain.consultation.repository;

import com.fitback.domain.consultation.entity.ConsultationMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsultationMaterialRepository extends JpaRepository<ConsultationMaterial, UUID> {

    List<ConsultationMaterial> findAllByConsultationIdOrderByCreatedAtAsc(UUID consultationId);

    List<ConsultationMaterial> findAllByInquiryIdOrderByCreatedAtAsc(UUID inquiryId);
}
