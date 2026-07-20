package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.dto.request.AiConsultationAnalyzeRequest;
import com.fitback.domain.consultation.dto.request.AiConsultationGraphSyncRequest;
import com.fitback.domain.consultation.entity.ConsultationMaterial;
import com.fitback.domain.consultation.dto.response.AiConsultationAnalyzeResponse;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.enums.ConsultationRegistrationStatus;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.domain.consultation.repository.ConsultationMaterialRepository;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import com.fitback.domain.customer.entity.CustomerAiInsight;
import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.entity.FollowUpAiInsight;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.store.entity.InflowPathOption;
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
import com.fitback.global.exception.BaseErrorCode;
import com.fitback.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final ConsultationMaterialRepository consultationMaterialRepository;
    private final AiConsultationClient aiConsultationClient;
    private final CustomerAiInsightRepository customerAiInsightRepository;
    private final NonConversionReasonRepository nonConversionReasonRepository;
    private final FollowUpRepository followUpRepository;
    private final FollowUpAiInsightRepository followUpAiInsightRepository;
    private final CustomerActivityTimelineRepository customerActivityTimelineRepository;
    private final PlatformTransactionManager transactionManager;

    public void analyzeConsultation(UUID consultationId) {
        if (consultationId == null) {
            log.warn("AI consultation analysis skipped. consultationId is null");
            return;
        }

        try {
            AiConsultationAnalyzeRequest request = loadAnalysisRequest(consultationId);
            if (request == null) {
                return;
            }
            AiConsultationAnalyzeResponse response = aiConsultationClient.analyzeConsultation(request);
            SavedAnalysisResult savedAnalysisResult = saveAnalysisSuccess(consultationId, response);
            syncConsultationGraph(savedAnalysisResult);
        } catch (RuntimeException e) {
            log.warn(
                    "AI consultation analysis failed. consultationId={}, status=FAILED",
                    consultationId,
                    e
            );
            saveAnalysisFailure(consultationId, e);
        }
    }

    private AiConsultationAnalyzeRequest loadAnalysisRequest(UUID consultationId) {
        return transactionTemplate().execute(status -> consultationRepository.findById(consultationId)
                .map(this::prepareAnalysisRequest)
                .orElseGet(() -> {
                    log.warn("AI consultation analysis skipped. consultation not found. consultationId={}", consultationId);
                    return null;
                }));
    }

    private AiConsultationAnalyzeRequest prepareAnalysisRequest(Consultation consultation) {
        try {
            AiConsultationAnalyzeRequest request = buildAnalyzeRequest(consultation);
            log.debug(
                    "AI consultation analysis target loaded. consultationId={}, customerId={}, serviceId={}",
                    request.getConsultation().getConsultationId(),
                    request.getCustomer().getCustomerId(),
                    request.getService().getServiceId()
            );
            return request;
        } catch (RuntimeException e) {
            saveAnalysisFailure(consultation, e, OffsetDateTime.now());
            log.warn(
                    "AI consultation analysis target invalid. consultationId={}, status=FAILED",
                    consultation.getId(),
                    e
            );
            return null;
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
                .attachedMaterials(toAttachedMaterials(require(consultation.getId(), "consultationId")))
                .build();
    }

    private List<AiConsultationAnalyzeRequest.AttachedMaterialInfo> toAttachedMaterials(UUID consultationId) {
        return consultationMaterialRepository.findAllByConsultationIdOrderByCreatedAtAsc(consultationId)
                .stream()
                .map(this::toAttachedMaterialInfo)
                .toList();
    }

    private AiConsultationAnalyzeRequest.AttachedMaterialInfo toAttachedMaterialInfo(ConsultationMaterial material) {
        return AiConsultationAnalyzeRequest.AttachedMaterialInfo.builder()
                .materialType(require(material.getMaterialType(), "materialType").name())
                .title(require(material.getTitle(), "materialTitle"))
                .content(require(material.getContent(), "materialContent"))
                .build();
    }

    private SavedAnalysisResult saveAnalysisSuccess(UUID consultationId, AiConsultationAnalyzeResponse response) {
        return transactionTemplate().execute(status -> {
            Consultation consultation = consultationRepository.findById(consultationId)
                    .orElseThrow(() -> new IllegalStateException("consultation not found during AI analysis save"));
            OffsetDateTime now = OffsetDateTime.now();
            Customer customer = consultation.getCustomer();
            Service service = consultation.getConsultedService();

            consultation.completeAiAnalysis(response.getSummary(), now);
            CustomerAiInsight customerAiInsight = upsertCustomerAiInsight(customer, response.getCustomerInsight(), now);
            List<NonConversionReason> nonConversionReasons =
                    replaceNonConversionReasons(customer, consultation, response.getNonConversionReasons());

            FollowUp followUp = null;
            FollowUpAiInsight followUpAiInsight = null;
            if (shouldCreateFollowUp(customer)) {
                followUp = replaceActiveFollowUp(customer, consultation, response);
                followUpAiInsight = saveFollowUpAiInsight(followUp, response, now);
            }
            saveAiAnalysisCompletedTimeline(consultation, response, followUp, now);
            if (followUp != null) {
                saveNextActionCreatedTimeline(consultation, followUp, response, now);
            }

            SavedAnalysisResult savedAnalysisResult = new SavedAnalysisResult(
                    consultation,
                    customer,
                    service,
                    customerAiInsight,
                    nonConversionReasons,
                    followUp,
                    followUpAiInsight,
                    null
            );
            return savedAnalysisResult.withGraphSyncRequest(toGraphSyncRequest(savedAnalysisResult));
        });
    }

    private void syncConsultationGraph(SavedAnalysisResult savedAnalysisResult) {
        if (savedAnalysisResult == null || savedAnalysisResult.graphSyncRequest() == null) {
            return;
        }

        try {
            aiConsultationClient.syncConsultationGraph(savedAnalysisResult.graphSyncRequest());
        } catch (RuntimeException e) {
            UUID consultationId = savedAnalysisResult.consultation() != null
                    ? savedAnalysisResult.consultation().getId()
                    : null;
            log.warn("AuraDB graph sync failed. consultationId={}", consultationId, e);
        }
    }

    private boolean shouldCreateFollowUp(Customer customer) {
        return customer.getStatus() != CustomerStatus.REGISTERED
                && customer.getStatus() != CustomerStatus.LOST;
    }

    private void saveAnalysisFailure(UUID consultationId, RuntimeException cause) {
        try {
            transactionTemplate().executeWithoutResult(status -> consultationRepository.findById(consultationId)
                    .ifPresentOrElse(
                            consultation -> saveAnalysisFailure(consultation, cause, OffsetDateTime.now()),
                            () -> log.warn("AI consultation analysis failure status skipped. consultation not found. consultationId={}", consultationId)
                    ));
        } catch (RuntimeException failureSaveException) {
            log.error(
                    "AI consultation analysis failure status save failed. consultationId={}",
                    consultationId,
                    failureSaveException
            );
        }
    }

    private void saveAnalysisFailure(Consultation consultation, RuntimeException cause, OffsetDateTime occurredAt) {
        consultation.markAiAnalysisFailed();

        if (!canWriteTimeline(consultation)) {
            return;
        }

        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("status", "FAILED");
        afterValue.put("errorCode", resolveErrorCode(cause));

        customerActivityTimelineRepository.save(CustomerActivityTimeline.builder()
                .store(consultation.getCustomer().getStore())
                .customer(consultation.getCustomer())
                .actorUser(consultation.getUser())
                .activityType(CustomerActivityType.AI_ANALYSIS_FAILED)
                .title("AI 분석에 실패했습니다.")
                .description("AI 분석 중 오류가 발생했습니다. 잠시 후 다시 시도할 수 있습니다.")
                .relatedType(ActivityRelatedType.CONSULTATION)
                .relatedId(consultation.getId())
                .afterValue(afterValue)
                .occurredAt(occurredAt)
                .build());
    }

    private boolean canWriteTimeline(Consultation consultation) {
        return consultation.getCustomer() != null
                && consultation.getCustomer().getStore() != null
                && consultation.getUser() != null;
    }

    private String resolveErrorCode(RuntimeException cause) {
        if (cause instanceof BusinessException businessException) {
            BaseErrorCode errorCode = businessException.getErrorCode();
            if (errorCode instanceof Enum<?> enumErrorCode) {
                return enumErrorCode.name();
            }
        }
        return ConsultationErrorCode.AI_ANALYSIS_FAILED.name();
    }

    private CustomerAiInsight upsertCustomerAiInsight(
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
        return customerAiInsight;
    }

    private List<NonConversionReason> replaceNonConversionReasons(
            Customer customer,
            Consultation consultation,
            List<AiConsultationAnalyzeResponse.NonConversionReason> reasons
    ) {
        nonConversionReasonRepository.deleteAllByCustomerId(customer.getId());

        if (reasons == null || reasons.isEmpty()) {
            return List.of();
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
        return entities;
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

    private FollowUpAiInsight saveFollowUpAiInsight(
            FollowUp followUp,
            AiConsultationAnalyzeResponse response,
            OffsetDateTime analyzedAt
    ) {
        AiConsultationAnalyzeResponse.FollowUpInsight insight = response.getFollowUpInsight();
        Map<String, Object> persuasionPoint = insight != null && insight.getPersuasionPoint() != null
                ? insight.getPersuasionPoint()
                : Map.of();
        Map<String, Object> actionBasis = buildActionBasis(response);

        FollowUpAiInsight followUpAiInsight = FollowUpAiInsight.builder()
                .followUp(followUp)
                .persuasionPoint(persuasionPoint)
                .cautionNote(insight != null ? insight.getCautionNote() : null)
                .actionBasis(actionBasis)
                .analyzedAt(analyzedAt)
                .build();
        followUpAiInsightRepository.save(followUpAiInsight);
        return followUpAiInsight;
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
        afterValue.put("summary", response.getSummary());
        afterValue.put("leadTemperature", response.getCustomerInsight().getLeadTemperature());
        afterValue.put("temperatureBasis", response.getCustomerInsight().getTemperatureBasis());
        afterValue.put("priorityScore", response.getCustomerInsight().getPriorityScore());
        afterValue.put("primaryReasonType", findPrimaryReasonType(response.getNonConversionReasons()));
        afterValue.put("nonConversionReasons", toNonConversionReasonTimelineValues(response.getNonConversionReasons()));
        afterValue.put("nextBestAction", toNextBestActionTimelineValue(response.getNextBestAction()));
        afterValue.put("followUpId", followUp != null ? followUp.getId() : null);

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

    private List<Map<String, Object>> toNonConversionReasonTimelineValues(
            List<AiConsultationAnalyzeResponse.NonConversionReason> reasons
    ) {
        if (reasons == null || reasons.isEmpty()) {
            return List.of();
        }
        return reasons.stream()
                .map(reason -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("reasonType", reason.getReasonType());
                    value.put("role", reason.getRole());
                    value.put("reasonBasis", reason.getReasonBasis());
                    value.put("confidence", reason.getConfidence());
                    return value;
                })
                .toList();
    }

    private Map<String, Object> toNextBestActionTimelineValue(
            AiConsultationAnalyzeResponse.NextBestAction nextBestAction
    ) {
        if (nextBestAction == null) {
            return Map.of();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", nextBestAction.getTitle());
        value.put("description", nextBestAction.getDescription());
        return value;
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

    private AiConsultationGraphSyncRequest toGraphSyncRequest(SavedAnalysisResult savedAnalysisResult) {
        Consultation consultation = savedAnalysisResult.consultation();
        Customer customer = savedAnalysisResult.customer();
        Service service = savedAnalysisResult.service();
        Store store = customer.getStore();
        InflowPathOption inflowPathOption = customer.getInflowPathOption();
        CustomerAiInsight customerAiInsight = savedAnalysisResult.customerAiInsight();

        return AiConsultationGraphSyncRequest.builder()
                .store(AiConsultationGraphSyncRequest.StoreInfo.builder()
                        .storeId(store.getId())
                        .storeType(store.getStoreType())
                        .build())
                .service(AiConsultationGraphSyncRequest.ServiceInfo.builder()
                        .serviceId(service.getId())
                        .storeId(service.getStore().getId())
                        .serviceName(service.getName())
                        .description(service.getDescription())
                        .price(service.getPrice())
                        .active(service.isActive())
                        .build())
                .customer(AiConsultationGraphSyncRequest.CustomerInfo.builder()
                        .customerId(customer.getId())
                        .storeId(store.getId())
                        .registeredServiceId(customer.getRegisteredService() != null
                                ? customer.getRegisteredService().getId()
                                : null)
                        .name(customer.getName())
                        .gender(customer.getGender())
                        .birthDate(customer.getBirthDate())
                        .phoneNum(customer.getPhoneNum())
                        .preferredContactChannel(customer.getPreferredContactChannel())
                        .status(customer.getStatus())
                        .inflowPathId(inflowPathOption.getId())
                        .inflowPathName(inflowPathOption.getName())
                        .registeredAt(customer.getRegisteredAt())
                        .firstConsultAt(customer.getFirstConsultAt())
                        .latestConsultAt(customer.getLatestConsultAt())
                        .build())
                .consultation(AiConsultationGraphSyncRequest.ConsultationInfo.builder()
                        .consultationId(consultation.getId())
                        .customerId(customer.getId())
                        .consultedServiceId(service.getId())
                        .sessionNo(consultation.getSessionNo())
                        .consultedAt(consultation.getConsultedAt())
                        .stage(consultation.getStage())
                        .sourceType(consultation.getSourceType())
                        .rawText(consultation.getRawText())
                        .summary(consultation.getSummary())
                        .aiAnalysisStatus(consultation.getAiAnalysisStatus())
                        .aiParsedAt(consultation.getAiParsedAt())
                        .build())
                .customerAiInsight(AiConsultationGraphSyncRequest.CustomerAiInsightInfo.builder()
                        .customerId(customer.getId())
                        .leadTemperature(customerAiInsight.getLeadTemperature())
                        .temperatureBasis(customerAiInsight.getTemperatureBasis())
                        .priorityScore(customerAiInsight.getPriorityScore())
                        .analyzedAt(customerAiInsight.getAnalyzedAt())
                        .build())
                .nonConversionReasons(toGraphSyncNonConversionReasons(savedAnalysisResult.nonConversionReasons()))
                .followUp(toGraphSyncFollowUp(savedAnalysisResult.followUp()))
                .followUpAiInsight(toGraphSyncFollowUpAiInsight(savedAnalysisResult.followUpAiInsight()))
                .build();
    }

    private List<AiConsultationGraphSyncRequest.NonConversionReasonInfo> toGraphSyncNonConversionReasons(
            List<NonConversionReason> nonConversionReasons
    ) {
        if (nonConversionReasons == null || nonConversionReasons.isEmpty()) {
            return List.of();
        }

        return nonConversionReasons.stream()
                .map(reason -> AiConsultationGraphSyncRequest.NonConversionReasonInfo.builder()
                        .reasonId(reason.getId())
                        .customerId(reason.getCustomer().getId())
                        .consultationId(reason.getConsultation() != null ? reason.getConsultation().getId() : null)
                        .reasonType(reason.getReasonType())
                        .role(reason.getRole())
                        .reasonBasis(reason.getReasonBasis())
                        .confidence(reason.getConfidence())
                        .build())
                .toList();
    }

    private AiConsultationGraphSyncRequest.FollowUpInfo toGraphSyncFollowUp(FollowUp followUp) {
        if (followUp == null) {
            return null;
        }

        return AiConsultationGraphSyncRequest.FollowUpInfo.builder()
                .followUpId(followUp.getId())
                .customerId(followUp.getCustomer().getId())
                .consultationId(followUp.getConsultation().getId())
                .recommendContactDate(followUp.getRecommendContactDate())
                .status(followUp.getStatus())
                .contactRound(followUp.getContactRound())
                .hasReply(followUp.isHasReply())
                .repliedAt(followUp.getRepliedAt())
                .snoozedUntil(followUp.getSnoozedUntil())
                .memo(followUp.getMemo())
                .build();
    }

    private AiConsultationGraphSyncRequest.FollowUpAiInsightInfo toGraphSyncFollowUpAiInsight(
            FollowUpAiInsight followUpAiInsight
    ) {
        if (followUpAiInsight == null) {
            return null;
        }

        return AiConsultationGraphSyncRequest.FollowUpAiInsightInfo.builder()
                .followUpId(followUpAiInsight.getFollowUp().getId())
                .persuasionPoint(followUpAiInsight.getPersuasionPoint())
                .cautionNote(followUpAiInsight.getCautionNote())
                .actionBasis(followUpAiInsight.getActionBasis())
                .analyzedAt(followUpAiInsight.getAnalyzedAt())
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private record SavedAnalysisResult(
            Consultation consultation,
            Customer customer,
            Service service,
            CustomerAiInsight customerAiInsight,
            List<NonConversionReason> nonConversionReasons,
            FollowUp followUp,
            FollowUpAiInsight followUpAiInsight,
            AiConsultationGraphSyncRequest graphSyncRequest
    ) {
        private SavedAnalysisResult withGraphSyncRequest(AiConsultationGraphSyncRequest graphSyncRequest) {
            return new SavedAnalysisResult(
                    consultation,
                    customer,
                    service,
                    customerAiInsight,
                    nonConversionReasons,
                    followUp,
                    followUpAiInsight,
                    graphSyncRequest
            );
        }
    }
}
