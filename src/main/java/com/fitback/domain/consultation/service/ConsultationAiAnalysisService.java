package com.fitback.domain.consultation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class ConsultationAiAnalysisService {

    public void analyzeConsultation(UUID consultationId) {
        log.debug("AI consultation analysis requested. consultationId={}", consultationId);
    }
}
