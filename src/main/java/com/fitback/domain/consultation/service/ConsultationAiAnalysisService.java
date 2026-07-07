package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.dto.request.AiConsultationAnalyzeRequest;
import com.fitback.domain.consultation.dto.response.AiConsultationAnalyzeResponse;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.enums.ConsultationRegistrationStatus;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import com.fitback.domain.customer.entity.CustomerAiInsight;
import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.entity.FollowUpAiInsight;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.InflowPathOption;
import com.fitback.domain.customer.entity.NonConversionReason;
import com.fitback.domain.customer.enums.ActivityRelatedType;
import com.fitback.domain.customer.enums.CustomerActivityType;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.repository.CustomerActivityTimelineRepository;
import com.fitback.domain.customer.repository.CustomerAiInsightRepository;
import com.fitback.domain.customer.repository.FollowUpAiInsightRepository;
import com.fitback.domain.customer.repository.FollowUpRepository;
import com.fitback.domain.customer.repository.NonConversionReasonRepository;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.store.entity.Store;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ConsultationAiAnalysisService {

    private final ConsultationRepository consultationRepository;
    private final AiConsultationClient aiConsultationClient;
    private final CustomerAiInsightRepository customerAiInsightRepository;
    private final NonConversionReasonRepository nonConversionReasonRepository;
    private final FollowUpRepository followUpRepository;
    private final FollowUpAiInsightRepository followUpAiInsightRepository;
    private final CustomerActivityTimelineRepository customerActivityTimelineRepository;

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
            AiConsultationAnalyzeResponse response = aiConsultationClient.analyzeConsultation(request);
            saveAnalysisSuccess(consultation, response);
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

    private void saveAnalysisSuccess(Consultation consultation, AiConsultationAnalyzeResponse response) {
        OffsetDateTime now = OffsetDateTime.now();
        Customer customer = consultation.getCustomer();

        consultation.completeAiAnalysis(response.getSummary(), now);
        upsertCustomerAiInsight(customer, response.getCustomerInsight(), now);
        replaceNonConversionReasons(customer, consultation, response.getNonConversionReasons());

        FollowUp followUp = replaceActiveFollowUp(customer, consultation, response);
        saveFollowUpAiInsight(followUp, response, now);
        saveAiAnalysisCompletedTimeline(consultation, response, followUp, now);
        saveNextActionCreatedTimeline(consultation, followUp, response, now);
    }

    private void upsertCustomerAiInsight(
            Customer customer,
            AiConsultationAnalyzeResponse.CustomerInsight insight,
            OffsetDateTime analyzedAt
    ) {
        CustomerAiInsight customerAiInsight = customerAiInsightRepository.findById(customer.getId())
                .orElseGet(() -> CustomerAiInsight.builder()
                        .customer(customer)
                        .build());

        customerAiInsight.updateAnalysis(
                insight.getLeadTemperature(),
                insight.getTemperatureBasis(),
                insight.getPriorityScore(),
                analyzedAt
        );

        customerAiInsightRepository.save(customerAiInsight);
    }

    private void replaceNonConversionReasons(
            Customer customer,
            Consultation consultation,
            List<AiConsultationAnalyzeResponse.NonConversionReason> reasons
    ) {
        nonConversionReasonRepository.deleteAllByCustomerId(customer.getId());

        if (reasons == null || reasons.isEmpty()) {
            return;
        }

        List<NonConversionReason> entities = reasons.stream()
                .map(reason -> NonConversionReason.builder()
                        .customer(customer)
                        .consultation(consultation)
                        .reasonType(reason.getReasonType())
                        .role(reason.getRole())
                        .reasonBasis(reason.getReasonBasis())
                        .confidence(reason.getConfidence())
                        .build())
                .toList();

        nonConversionReasonRepository.saveAll(entities);
    }

    private FollowUp replaceActiveFollowUp(
            Customer customer,
            Consultation consultation,
            AiConsultationAnalyzeResponse response
    ) {
        followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customer.getId(), FollowUpStatus.PENDING)
                .ifPresent(FollowUp::markSuperseded);

        FollowUp followUp = FollowUp.builder()
                .customer(customer)
                .consultation(consultation)
                .recommendContactDate(response.getFollowUp().getRecommendContactDate())
                .status(FollowUpStatus.PENDING)
                .memo(resolveFollowUpMemo(response))
                .build();

        return followUpRepository.save(followUp);
    }

    private String resolveFollowUpMemo(AiConsultationAnalyzeResponse response) {
        String memo = response.getFollowUp().getMemo();
        if (hasText(memo)) {
            return memo;
        }
        return response.getNextBestAction().getTitle();
    }

    private void saveFollowUpAiInsight(
            FollowUp followUp,
            AiConsultationAnalyzeResponse response,
            OffsetDateTime analyzedAt
    ) {
        AiConsultationAnalyzeResponse.FollowUpInsight insight = response.getFollowUpInsight();
        Map<String, Object> persuasionPoint = insight != null && insight.getPersuasionPoint() != null
                ? insight.getPersuasionPoint()
                : Map.of();
        Map<String, Object> actionBasis = buildActionBasis(response);

        followUpAiInsightRepository.save(FollowUpAiInsight.builder()
                .followUp(followUp)
                .persuasionPoint(persuasionPoint)
                .cautionNote(insight != null ? insight.getCautionNote() : null)
                .actionBasis(actionBasis)
                .analyzedAt(analyzedAt)
                .build());
    }

    private Map<String, Object> buildActionBasis(AiConsultationAnalyzeResponse response) {
        Map<String, Object> actionBasis = new LinkedHashMap<>();
        AiConsultationAnalyzeResponse.FollowUpInsight insight = response.getFollowUpInsight();
        if (insight != null && insight.getActionBasis() != null) {
            actionBasis.putAll(insight.getActionBasis());
        }
        actionBasis.put("title", response.getNextBestAction().getTitle());
        actionBasis.put("description", response.getNextBestAction().getDescription());
        return actionBasis;
    }

    private void saveAiAnalysisCompletedTimeline(
            Consultation consultation,
            AiConsultationAnalyzeResponse response,
            FollowUp followUp,
            OffsetDateTime occurredAt
    ) {
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("leadTemperature", response.getCustomerInsight().getLeadTemperature());
        afterValue.put("priorityScore", response.getCustomerInsight().getPriorityScore());
        afterValue.put("primaryReasonType", findPrimaryReasonType(response.getNonConversionReasons()));
        afterValue.put("followUpId", followUp.getId());

        customerActivityTimelineRepository.save(CustomerActivityTimeline.builder()
                .store(consultation.getCustomer().getStore())
                .customer(consultation.getCustomer())
                .actorUser(consultation.getUser())
                .activityType(CustomerActivityType.AI_ANALYSIS_COMPLETED)
                .title("AI 분석이 완료되었습니다.")
                .description("상담요약, 고객온도, 주요 이탈요인, 다음 최적 액션이 갱신되었습니다.")
                .relatedType(ActivityRelatedType.CONSULTATION)
                .relatedId(consultation.getId())
                .afterValue(afterValue)
                .occurredAt(occurredAt)
                .build());
    }

    private String findPrimaryReasonType(List<AiConsultationAnalyzeResponse.NonConversionReason> reasons) {
        if (reasons == null) {
            return null;
        }
        return reasons.stream()
                .filter(reason -> "PRIMARY".equals(reason.getRole()))
                .map(AiConsultationAnalyzeResponse.NonConversionReason::getReasonType)
                .findFirst()
                .orElse(null);
    }

    private void saveNextActionCreatedTimeline(
            Consultation consultation,
            FollowUp followUp,
            AiConsultationAnalyzeResponse response,
            OffsetDateTime occurredAt
    ) {
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("followUpId", followUp.getId());
        afterValue.put("recommendContactDate", followUp.getRecommendContactDate());
        afterValue.put("status", followUp.getStatus());
        afterValue.put("nextActionTitle", response.getNextBestAction().getTitle());

        customerActivityTimelineRepository.save(CustomerActivityTimeline.builder()
                .store(consultation.getCustomer().getStore())
                .customer(consultation.getCustomer())
                .actorUser(consultation.getUser())
                .activityType(CustomerActivityType.NEXT_ACTION_CREATED)
                .title("다음 최적 액션이 생성되었습니다.")
                .description("AI 분석 결과를 바탕으로 후속 연락 일정과 실행 액션이 생성되었습니다.")
                .relatedType(ActivityRelatedType.FOLLOW_UP)
                .relatedId(followUp.getId())
                .afterValue(afterValue)
                .occurredAt(occurredAt)
                .build());
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
