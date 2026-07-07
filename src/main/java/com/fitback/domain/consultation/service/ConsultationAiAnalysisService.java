package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.dto.request.AiConsultationAnalyzeRequest;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.enums.ConsultationRegistrationStatus;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.InflowPathOption;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.store.entity.Store;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ConsultationAiAnalysisService {

    private final ConsultationRepository consultationRepository;

    @Transactional
    public void analyzeConsultation(UUID consultationId) {
        if (consultationId == null) {
            log.warn("AI consultation analysis skipped. consultationId is null");
            return;
        }

        consultationRepository.findById(consultationId)
                .ifPresentOrElse(
                        this::prepareAnalysisRequest,
                        () -> log.warn("AI consultation analysis skipped. consultation not found. consultationId={}", consultationId)
                );
    }

    private void prepareAnalysisRequest(Consultation consultation) {
        try {
            AiConsultationAnalyzeRequest request = buildAnalyzeRequest(consultation);
            log.debug(
                    "AI consultation analysis target loaded. consultationId={}, customerId={}, serviceId={}",
                    request.getConsultation().getConsultationId(),
                    request.getCustomer().getCustomerId(),
                    request.getService().getServiceId()
            );
        } catch (RuntimeException e) {
            consultation.markAiAnalysisFailed();
            log.warn(
                    "AI consultation analysis target invalid. consultationId={}, status=FAILED",
                    consultation.getId(),
                    e
            );
        }
    }

    private AiConsultationAnalyzeRequest buildAnalyzeRequest(Consultation consultation) {
        Customer customer = require(consultation.getCustomer(), "customer");
        Service service = require(consultation.getConsultedService(), "service");
        Store store = require(customer.getStore(), "store");
        InflowPathOption inflowPathOption = require(customer.getInflowPathOption(), "inflowPathOption");

        return AiConsultationAnalyzeRequest.builder()
                .customer(AiConsultationAnalyzeRequest.CustomerInfo.builder()
                        .customerId(require(customer.getId(), "customerId"))
                        .name(require(customer.getName(), "customerName"))
                        .gender(require(customer.getGender(), "customerGender"))
                        .birthDate(require(customer.getBirthDate(), "customerBirthDate"))
                        .phoneNum(require(customer.getPhoneNum(), "customerPhoneNum"))
                        .preferredContactChannel(require(customer.getPreferredContactChannel(), "preferredContactChannel"))
                        .status(require(customer.getStatus(), "customerStatus"))
                        .inflowPathId(require(inflowPathOption.getId(), "inflowPathId"))
                        .inflowPathName(require(inflowPathOption.getName(), "inflowPathName"))
                        .build())
                .consultation(AiConsultationAnalyzeRequest.ConsultationInfo.builder()
                        .consultationId(require(consultation.getId(), "consultationId"))
                        .sessionNo(require(consultation.getSessionNo(), "sessionNo"))
                        .consultedAt(require(consultation.getConsultedAt(), "consultedAt"))
                        .consultedServiceId(require(service.getId(), "consultedServiceId"))
                        .stage(consultation.getStage())
                        .sourceType(require(consultation.getSourceType(), "sourceType"))
                        .rawText(require(consultation.getRawText(), "rawText"))
                        .build())
                .service(AiConsultationAnalyzeRequest.ServiceInfo.builder()
                        .serviceId(require(service.getId(), "serviceId"))
                        .serviceName(require(service.getName(), "serviceName"))
                        .description(service.getDescription())
                        .price(service.getPrice())
                        .build())
                .storeContext(AiConsultationAnalyzeRequest.StoreContext.builder()
                        .storeId(require(store.getId(), "storeId"))
                        .storeType(require(store.getStoreType(), "storeType"))
                        .registrationStatus(resolveRegistrationStatus(require(customer.getStatus(), "customerStatus")))
                        .build())
                .build();
    }

    private ConsultationRegistrationStatus resolveRegistrationStatus(CustomerStatus customerStatus) {
        return switch (customerStatus) {
            case REGISTERED -> ConsultationRegistrationStatus.REGISTERED;
            case PENDING -> ConsultationRegistrationStatus.PENDING;
            case SCHEDULED -> ConsultationRegistrationStatus.SCHEDULED;
            case LOST -> ConsultationRegistrationStatus.LOST;
            case NO_SHOW -> throw new IllegalStateException("NO_SHOW is not valid for new consultation AI analysis");
        };
    }

    private <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("Required AI analysis target field is missing: " + fieldName);
        }
        return value;
    }
}
