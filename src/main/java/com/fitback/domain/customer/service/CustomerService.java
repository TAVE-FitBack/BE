package com.fitback.domain.customer.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewRequest;
import com.fitback.domain.consultation.dto.request.AiNextActionRegenerateRequest;
import com.fitback.domain.consultation.dto.response.AiNextActionRegenerateResponse;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.event.ConsultationCreatedEvent;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.dto.request.CustomerAiAnalysisUpdateRequest;
import com.fitback.domain.customer.dto.request.CustomerStatusUpdateRequest;
import com.fitback.domain.customer.dto.request.NextActionRegenerateRequest;
import com.fitback.domain.customer.dto.request.ReconsultationCheckPreviewRequest;
import com.fitback.domain.customer.dto.request.ReconsultationCreateRequest;
import com.fitback.domain.customer.dto.response.CustomerAiAnalysisUpdateResponse;
import com.fitback.domain.customer.dto.response.CustomerStatusUpdateResponse;
import com.fitback.domain.customer.dto.response.CustomerDetailResponse;
import com.fitback.domain.customer.dto.response.NextActionRegenerateResponse;
import com.fitback.domain.customer.dto.response.ReconsultationCreateResponse;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import com.fitback.domain.customer.entity.CustomerAiInsight;
import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.entity.FollowUpAiInsight;
import com.fitback.domain.customer.entity.MessageTemplate;
import com.fitback.domain.customer.entity.NonConversionReason;
import com.fitback.domain.customer.enums.ActivityRelatedType;
import com.fitback.domain.customer.enums.CustomerActivityType;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.exception.CustomerErrorCode;
import com.fitback.domain.customer.repository.CustomerActivityTimelineRepository;
import com.fitback.domain.customer.repository.CustomerAiInsightRepository;
import com.fitback.domain.customer.repository.CustomerRepository;
import com.fitback.domain.customer.repository.FollowUpAiInsightRepository;
import com.fitback.domain.customer.repository.FollowUpRepository;
import com.fitback.domain.customer.repository.MessageTemplateRepository;
import com.fitback.domain.customer.repository.NonConversionReasonRepository;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private static final String NEXT_ACTION_TITLE_KEY = "title";
    private static final String NEXT_ACTION_DESCRIPTION_KEY = "description";
    private static final String FOLLOW_UP_ACTION_CLOSED = "CLOSED";
    private static final String FOLLOW_UP_ACTION_KEEP = "KEEP";
    private static final String FOLLOW_UP_ACTION_REGENERATION_AVAILABLE = "REGENERATION_AVAILABLE";

    private final CustomerRepository customerRepository;
    private final ConsultationRepository consultationRepository;
    private final CustomerAiInsightRepository customerAiInsightRepository;
    private final NonConversionReasonRepository nonConversionReasonRepository;
    private final FollowUpRepository followUpRepository;
    private final FollowUpAiInsightRepository followUpAiInsightRepository;
    private final MessageTemplateRepository messageTemplateRepository;
    private final CustomerActivityTimelineRepository customerActivityTimelineRepository;
    private final ServiceRepository serviceRepository;
    private final AiConsultationClient aiConsultationClient;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CustomerDetailResponse getCustomerDetail(UUID storeId, UUID customerId) {
        if (storeId == null) {
            throw new BusinessException(CustomerErrorCode.STORE_NOT_ASSIGNED);
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        if (!storeId.equals(customer.getStore().getId())) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_ACCESS_DENIED);
        }

        Consultation latestConsultation = consultationRepository
                .findFirstByCustomerIdOrderBySessionNoDesc(customerId)
                .orElse(null);

        CustomerAiInsight aiInsight = customerAiInsightRepository
                .findById(customerId)
                .orElse(null);

        List<NonConversionReason> nonConversionReasons = nonConversionReasonRepository
                .findAllByCustomerIdOrderByUpdatedAtDesc(customerId);

        FollowUp activeFollowUp = followUpRepository
                .findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING)
                .orElse(null);

        FollowUpAiInsight followUpAiInsight = activeFollowUp == null
                ? null
                : followUpAiInsightRepository.findById(activeFollowUp.getId()).orElse(null);

        MessageTemplate latestMessageTemplate = activeFollowUp == null
                ? null
                : messageTemplateRepository
                .findFirstByCustomerIdAndFollowUpIdOrderByGeneratedAtDesc(customerId, activeFollowUp.getId())
                .orElse(null);

        List<CustomerActivityTimeline> timeline = customerActivityTimelineRepository
                .findAllByCustomerIdAndStoreIdOrderByOccurredAtDescCreatedAtDesc(customerId, storeId);

        return CustomerDetailResponse.builder()
                .customer(toCustomerInfo(customer))
                .latestConsultation(toLatestConsultation(latestConsultation))
                .aiAnalysisStatus(resolveAiAnalysisStatus(latestConsultation))
                .aiInsight(toAiInsight(aiInsight))
                .nonConversionReasons(toNonConversionReasonInfos(nonConversionReasons))
                .activeFollowUp(toActiveFollowUp(activeFollowUp))
                .nextBestAction(toNextBestAction(followUpAiInsight))
                .latestMessageTemplate(toLatestMessageTemplate(latestMessageTemplate))
                .timeline(toTimelineItems(timeline))
                .build();
    }

    public Map<String, Object> checkReconsultationPreview(
            UUID storeId,
            UUID customerId,
            ReconsultationCheckPreviewRequest request
    ) {
        if (storeId == null) {
            throw new BusinessException(CustomerErrorCode.STORE_NOT_ASSIGNED);
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        if (!storeId.equals(customer.getStore().getId())) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_ACCESS_DENIED);
        }

        Service service = serviceRepository
                .findByIdAndStoreIdAndActiveTrue(request.getConsultation().getConsultedServiceId(), storeId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.SERVICE_NOT_FOUND));

        AiCheckPreviewRequest aiRequest = AiCheckPreviewRequest.builder()
                .rawText(request.getConsultation().getRawText())
                .serviceName(service.getName())
                .customerInfo(AiCheckPreviewRequest.CustomerInfo.builder()
                        .name(customer.getName())
                        .gender(customer.getGender())
                        .birthDate(customer.getBirthDate())
                        .build())
                .build();

        return aiConsultationClient.checkPreview(aiRequest);
    }

    @Transactional
    public ReconsultationCreateResponse createReconsultation(
            UUID storeId,
            UUID customerId,
            ReconsultationCreateRequest request
    ) {
        if (storeId == null) {
            throw new BusinessException(CustomerErrorCode.STORE_NOT_ASSIGNED);
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        if (!storeId.equals(customer.getStore().getId())) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_ACCESS_DENIED);
        }

        Service service = serviceRepository
                .findByIdAndStoreIdAndActiveTrue(request.getConsultation().getConsultedServiceId(), storeId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.SERVICE_NOT_FOUND));

        User counselor = userRepository
                .findByIdAndStore_Id(request.getConsultation().getUserId(), storeId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.COUNSELOR_NOT_FOUND));

        int nextSessionNo = consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId)
                .map(Consultation::getSessionNo)
                .orElse(0) + 1;

        Consultation consultation = Consultation.builder()
                .customer(customer)
                .user(counselor)
                .consultedService(service)
                .consultedAt(request.getConsultation().getConsultedAt())
                .sessionNo(nextSessionNo)
                .stage(ConsultationStage.CONSULTATION)
                .sourceType(ConsultationSourceType.DIRECT)
                .rawText(request.getConsultation().getRawText())
                .aiAnalysisStatus(AiAnalysisStatus.PROCESSING)
                .build();

        Consultation savedConsultation = consultationRepository.save(consultation);
        customer.updateLatestConsultAt(request.getConsultation().getConsultedAt().toLocalDate());
        saveReconsultationCreatedTimeline(customer, counselor, service, savedConsultation);
        eventPublisher.publishEvent(new ConsultationCreatedEvent(savedConsultation.getId()));

        return ReconsultationCreateResponse.builder()
                .customerId(customer.getId())
                .consultationId(savedConsultation.getId())
                .sessionNo(savedConsultation.getSessionNo())
                .aiAnalysisStatus(savedConsultation.getAiAnalysisStatus())
                .build();
    }

    @Transactional
    public CustomerAiAnalysisUpdateResponse updateAiAnalysis(
            UUID storeId,
            UUID customerId,
            CustomerAiAnalysisUpdateRequest request
    ) {
        if (storeId == null) {
            throw new BusinessException(CustomerErrorCode.STORE_NOT_ASSIGNED);
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        if (!storeId.equals(customer.getStore().getId())) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_ACCESS_DENIED);
        }

        Consultation latestConsultation = consultationRepository
                .findFirstByCustomerIdOrderBySessionNoDesc(customerId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.CONSULTATION_NOT_FOUND));

        CustomerAiInsight customerAiInsight = customerAiInsightRepository.findById(customerId)
                .orElseGet(() -> CustomerAiInsight.builder()
                        .customer(customer)
                        .build());

        List<NonConversionReason> existingReasons = nonConversionReasonRepository
                .findAllByCustomerIdOrderByUpdatedAtDesc(customerId);
        Map<String, Object> beforeValue = buildAiAnalysisTimelineValue(
                latestConsultation.getSummary(),
                customerAiInsight.getLeadTemperature(),
                findPrimaryReasonType(existingReasons)
        );

        latestConsultation.updateSummary(request.getSummary());
        customerAiInsight.updateManualAnalysis(request.getLeadTemperature(), request.getTemperatureBasis());
        customerAiInsightRepository.save(customerAiInsight);

        nonConversionReasonRepository.deleteAllByCustomerId(customerId);
        List<NonConversionReason> newReasons = request.getNonConversionReasons().stream()
                .map(reason -> NonConversionReason.builder()
                        .customer(customer)
                        .consultation(latestConsultation)
                        .reasonType(reason.getReasonType())
                        .role(reason.getRole())
                        .reasonBasis(reason.getReasonBasis())
                        .confidence(reason.getConfidence())
                        .build())
                .toList();
        nonConversionReasonRepository.saveAll(newReasons);

        Map<String, Object> afterValue = buildAiAnalysisTimelineValue(
                request.getSummary(),
                request.getLeadTemperature(),
                findPrimaryReasonType(newReasons)
        );
        saveAiAnalysisManuallyUpdatedTimeline(customer, latestConsultation, beforeValue, afterValue);

        return CustomerAiAnalysisUpdateResponse.builder()
                .customerId(customer.getId())
                .nextActionRegenerationAvailable(true)
                .build();
    }

    @Transactional
    public NextActionRegenerateResponse regenerateNextAction(
            UUID storeId,
            UUID customerId,
            NextActionRegenerateRequest request
    ) {
        if (storeId == null) {
            throw new BusinessException(CustomerErrorCode.STORE_NOT_ASSIGNED);
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        if (!storeId.equals(customer.getStore().getId())) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_ACCESS_DENIED);
        }

        Consultation latestConsultation = consultationRepository
                .findFirstByCustomerIdOrderBySessionNoDesc(customerId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.CONSULTATION_NOT_FOUND));

        CustomerAiInsight customerAiInsight = customerAiInsightRepository.findById(customerId)
                .orElseGet(() -> CustomerAiInsight.builder()
                        .customer(customer)
                        .build());
        List<NonConversionReason> reasons = nonConversionReasonRepository
                .findAllByCustomerIdOrderByUpdatedAtDesc(customerId);

        AiNextActionRegenerateResponse aiResponse = aiConsultationClient.regenerateNextAction(
                buildNextActionAiRequest(customer, latestConsultation, customerAiInsight, reasons)
        );

        FollowUp oldFollowUp = followUpRepository
                .findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING)
                .orElse(null);
        Map<String, Object> oldFollowUpBeforeValue = oldFollowUp != null
                ? buildFollowUpTimelineValue(oldFollowUp, null)
                : null;
        if (oldFollowUp != null) {
            oldFollowUp.markSuperseded();
        }

        FollowUp newFollowUp = FollowUp.builder()
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(aiResponse.getFollowUp().getRecommendContactDate())
                .status(FollowUpStatus.PENDING)
                .memo(resolveNextActionMemo(aiResponse))
                .build();
        FollowUp savedFollowUp = followUpRepository.save(newFollowUp);

        saveRegeneratedFollowUpAiInsight(savedFollowUp, aiResponse);
        customerAiInsight.updatePriorityScore(aiResponse.getPriorityScore(), OffsetDateTime.now());
        customerAiInsightRepository.save(customerAiInsight);
        saveNextActionRegeneratedTimeline(customer, latestConsultation, oldFollowUpBeforeValue, savedFollowUp, aiResponse);

        return NextActionRegenerateResponse.builder()
                .customerId(customer.getId())
                .oldFollowUpId(oldFollowUp != null ? oldFollowUp.getId() : null)
                .oldFollowUpStatus(oldFollowUp != null ? oldFollowUp.getStatus() : null)
                .newFollowUpId(savedFollowUp.getId())
                .newFollowUpStatus(savedFollowUp.getStatus())
                .recommendContactDate(savedFollowUp.getRecommendContactDate())
                .priorityScore(aiResponse.getPriorityScore())
                .build();
    }

    @Transactional
    public CustomerStatusUpdateResponse updateCustomerStatus(
            UUID storeId,
            UUID customerId,
            CustomerStatusUpdateRequest request
    ) {
        if (storeId == null) {
            throw new BusinessException(CustomerErrorCode.STORE_NOT_ASSIGNED);
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        if (!storeId.equals(customer.getStore().getId())) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_ACCESS_DENIED);
        }

        Consultation latestConsultation = consultationRepository
                .findFirstByCustomerIdOrderBySessionNoDesc(customerId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.CONSULTATION_NOT_FOUND));

        CustomerStatus beforeStatus = customer.getStatus();
        CustomerStatus afterStatus = request.getStatus();
        String followUpAction = resolveFollowUpAction(afterStatus);

        if (afterStatus == CustomerStatus.REGISTERED) {
            if (request.getRegisteredServiceId() == null) {
                throw new BusinessException(CustomerErrorCode.INVALID_CUSTOMER_STATUS);
            }
            Service registeredService = serviceRepository
                    .findByIdAndStoreIdAndActiveTrue(request.getRegisteredServiceId(), storeId)
                    .orElseThrow(() -> new BusinessException(ConsultationErrorCode.SERVICE_NOT_FOUND));
            customer.markRegistered(registeredService);
        } else {
            customer.markStatus(afterStatus);
        }

        FollowUp activeFollowUp = followUpRepository
                .findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING)
                .orElse(null);
        if (FOLLOW_UP_ACTION_CLOSED.equals(followUpAction) && activeFollowUp != null) {
            activeFollowUp.markClosed();
        }

        saveCustomerStatusChangedTimeline(customer, latestConsultation, beforeStatus, afterStatus, followUpAction);

        return CustomerStatusUpdateResponse.builder()
                .customerId(customer.getId())
                .status(customer.getStatus())
                .followUpAction(followUpAction)
                .nextActionRegenerationAvailable(afterStatus == CustomerStatus.NO_SHOW)
                .build();
    }

    private CustomerDetailResponse.CustomerInfo toCustomerInfo(Customer customer) {
        Service registeredService = customer.getRegisteredService();

        return CustomerDetailResponse.CustomerInfo.builder()
                .customerId(customer.getId())
                .name(customer.getName())
                .gender(customer.getGender())
                .birthDate(customer.getBirthDate())
                .phoneNum(customer.getPhoneNum())
                .preferredContactChannel(customer.getPreferredContactChannel())
                .status(customer.getStatus())
                .registeredServiceId(registeredService != null ? registeredService.getId() : null)
                .registeredServiceName(registeredService != null ? registeredService.getName() : null)
                .inflowPathId(customer.getInflowPathOption().getId())
                .inflowPathName(customer.getInflowPathOption().getName())
                .firstConsultAt(customer.getFirstConsultAt())
                .latestConsultAt(customer.getLatestConsultAt())
                .build();
    }

    private void saveReconsultationCreatedTimeline(
            Customer customer,
            User counselor,
            Service service,
            Consultation consultation
    ) {
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("consultationId", consultation.getId());
        afterValue.put("sessionNo", consultation.getSessionNo());
        afterValue.put("consultedServiceId", service.getId());
        afterValue.put("consultedAt", consultation.getConsultedAt());

        CustomerActivityTimeline timeline = CustomerActivityTimeline.builder()
                .store(customer.getStore())
                .customer(customer)
                .actorUser(counselor)
                .activityType(CustomerActivityType.RECONSULTATION_CREATED)
                .title("재상담이 등록되었습니다.")
                .description(consultation.getSessionNo() + "회차 상담 메모가 저장되었습니다.")
                .relatedType(ActivityRelatedType.CONSULTATION)
                .relatedId(consultation.getId())
                .afterValue(afterValue)
                .occurredAt(OffsetDateTime.now())
                .build();

        customerActivityTimelineRepository.save(timeline);
    }

    private void saveAiAnalysisManuallyUpdatedTimeline(
            Customer customer,
            Consultation consultation,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue
    ) {
        CustomerActivityTimeline timeline = CustomerActivityTimeline.builder()
                .store(customer.getStore())
                .customer(customer)
                .actorUser(consultation.getUser())
                .activityType(CustomerActivityType.AI_ANALYSIS_MANUALLY_UPDATED)
                .title("AI 분석값이 수정되었습니다.")
                .description("사용자가 AI 상담요약, 고객온도 또는 주요 이탈요인을 수정했습니다.")
                .relatedType(ActivityRelatedType.CUSTOMER)
                .relatedId(customer.getId())
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .occurredAt(OffsetDateTime.now())
                .build();

        customerActivityTimelineRepository.save(timeline);
    }

    private AiNextActionRegenerateRequest buildNextActionAiRequest(
            Customer customer,
            Consultation latestConsultation,
            CustomerAiInsight customerAiInsight,
            List<NonConversionReason> reasons
    ) {
        return AiNextActionRegenerateRequest.builder()
                .customer(AiNextActionRegenerateRequest.CustomerInfo.builder()
                        .customerId(customer.getId())
                        .status(customer.getStatus())
                        .build())
                .latestConsultation(AiNextActionRegenerateRequest.LatestConsultationInfo.builder()
                        .consultationId(latestConsultation.getId())
                        .summary(latestConsultation.getSummary())
                        .rawText(latestConsultation.getRawText())
                        .build())
                .aiAnalysis(AiNextActionRegenerateRequest.AiAnalysisInfo.builder()
                        .leadTemperature(customerAiInsight.getLeadTemperature())
                        .temperatureBasis(customerAiInsight.getTemperatureBasis())
                        .nonConversionReasons(reasons.stream()
                                .map(reason -> AiNextActionRegenerateRequest.NonConversionReasonInfo.builder()
                                        .reasonType(reason.getReasonType())
                                        .role(reason.getRole())
                                        .reasonBasis(reason.getReasonBasis())
                                        .build())
                                .toList())
                        .build())
                .build();
    }

    private void saveRegeneratedFollowUpAiInsight(
            FollowUp followUp,
            AiNextActionRegenerateResponse aiResponse
    ) {
        AiNextActionRegenerateResponse.FollowUpInsight insight = aiResponse.getFollowUpInsight();
        Map<String, Object> persuasionPoint = insight != null && insight.getPersuasionPoint() != null
                ? insight.getPersuasionPoint()
                : Map.of();
        Map<String, Object> actionBasis = buildRegeneratedActionBasis(aiResponse);

        followUpAiInsightRepository.save(FollowUpAiInsight.builder()
                .followUp(followUp)
                .persuasionPoint(persuasionPoint)
                .cautionNote(insight != null ? insight.getCautionNote() : null)
                .actionBasis(actionBasis)
                .analyzedAt(OffsetDateTime.now())
                .build());
    }

    private Map<String, Object> buildRegeneratedActionBasis(AiNextActionRegenerateResponse aiResponse) {
        Map<String, Object> actionBasis = new LinkedHashMap<>();
        AiNextActionRegenerateResponse.FollowUpInsight insight = aiResponse.getFollowUpInsight();
        if (insight != null && insight.getActionBasis() != null) {
            actionBasis.putAll(insight.getActionBasis());
        }
        actionBasis.put(NEXT_ACTION_TITLE_KEY, aiResponse.getNextBestAction().getTitle());
        actionBasis.put(NEXT_ACTION_DESCRIPTION_KEY, aiResponse.getNextBestAction().getDescription());
        return actionBasis;
    }

    private String resolveNextActionMemo(AiNextActionRegenerateResponse aiResponse) {
        String memo = aiResponse.getFollowUp().getMemo();
        if (memo != null && !memo.isBlank()) {
            return memo;
        }
        return aiResponse.getNextBestAction().getTitle();
    }

    private void saveNextActionRegeneratedTimeline(
            Customer customer,
            Consultation latestConsultation,
            Map<String, Object> beforeValue,
            FollowUp newFollowUp,
            AiNextActionRegenerateResponse aiResponse
    ) {
        Map<String, Object> afterValue = buildFollowUpTimelineValue(newFollowUp, aiResponse.getNextBestAction().getTitle());

        CustomerActivityTimeline timeline = CustomerActivityTimeline.builder()
                .store(customer.getStore())
                .customer(customer)
                .actorUser(latestConsultation.getUser())
                .activityType(CustomerActivityType.NEXT_ACTION_REGENERATED)
                .title("다음 최적 액션이 재생성되었습니다.")
                .description("수정된 AI 분석값을 기준으로 후속 연락 일정과 실행 액션이 새로 생성되었습니다.")
                .relatedType(ActivityRelatedType.FOLLOW_UP)
                .relatedId(newFollowUp.getId())
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .occurredAt(OffsetDateTime.now())
                .build();

        customerActivityTimelineRepository.save(timeline);
    }

    private void saveCustomerStatusChangedTimeline(
            Customer customer,
            Consultation latestConsultation,
            CustomerStatus beforeStatus,
            CustomerStatus afterStatus,
            String followUpAction
    ) {
        Map<String, Object> beforeValue = new LinkedHashMap<>();
        beforeValue.put("status", beforeStatus);

        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("status", afterStatus);
        afterValue.put("followUpAction", followUpAction);

        CustomerActivityTimeline timeline = CustomerActivityTimeline.builder()
                .store(customer.getStore())
                .customer(customer)
                .actorUser(latestConsultation.getUser())
                .activityType(CustomerActivityType.CUSTOMER_STATUS_CHANGED)
                .title("고객 상태가 변경되었습니다.")
                .description("고객 상태가 " + beforeStatus + "에서 " + afterStatus + "(으)로 변경되었습니다.")
                .relatedType(ActivityRelatedType.CUSTOMER)
                .relatedId(customer.getId())
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .occurredAt(OffsetDateTime.now())
                .build();

        customerActivityTimelineRepository.save(timeline);
    }

    private String resolveFollowUpAction(CustomerStatus status) {
        return switch (status) {
            case REGISTERED, LOST -> FOLLOW_UP_ACTION_CLOSED;
            case PENDING, SCHEDULED -> FOLLOW_UP_ACTION_KEEP;
            case NO_SHOW -> FOLLOW_UP_ACTION_REGENERATION_AVAILABLE;
        };
    }

    private Map<String, Object> buildFollowUpTimelineValue(FollowUp followUp, String nextActionTitle) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("followUpId", followUp.getId());
        value.put("status", followUp.getStatus());
        value.put("recommendContactDate", followUp.getRecommendContactDate());
        value.put("nextActionTitle", nextActionTitle);
        return value;
    }

    private Map<String, Object> buildAiAnalysisTimelineValue(
            String summary,
            String leadTemperature,
            String primaryReasonType
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("summary", summary);
        value.put("leadTemperature", leadTemperature);
        value.put("primaryReasonType", primaryReasonType);
        return value;
    }

    private String findPrimaryReasonType(List<NonConversionReason> reasons) {
        if (reasons == null) {
            return null;
        }
        return reasons.stream()
                .filter(reason -> "PRIMARY".equals(reason.getRole()))
                .map(NonConversionReason::getReasonType)
                .findFirst()
                .orElse(null);
    }

    private CustomerDetailResponse.LatestConsultation toLatestConsultation(Consultation consultation) {
        if (consultation == null) {
            return null;
        }

        return CustomerDetailResponse.LatestConsultation.builder()
                .consultationId(consultation.getId())
                .sessionNo(consultation.getSessionNo())
                .consultedAt(consultation.getConsultedAt())
                .consultedServiceId(consultation.getConsultedService().getId())
                .consultedServiceName(consultation.getConsultedService().getName())
                .userId(consultation.getUser().getId())
                .counselorName(consultation.getUser().getNickname())
                .stage(consultation.getStage())
                .sourceType(consultation.getSourceType())
                .rawText(consultation.getRawText())
                .summary(consultation.getSummary())
                .build();
    }

    private AiAnalysisStatus resolveAiAnalysisStatus(Consultation consultation) {
        return consultation != null ? consultation.getAiAnalysisStatus() : null;
    }

    private CustomerDetailResponse.AiInsight toAiInsight(CustomerAiInsight aiInsight) {
        if (aiInsight == null) {
            return null;
        }

        return CustomerDetailResponse.AiInsight.builder()
                .leadTemperature(aiInsight.getLeadTemperature())
                .temperatureBasis(aiInsight.getTemperatureBasis())
                .priorityScore(aiInsight.getPriorityScore())
                .analyzedAt(aiInsight.getAnalyzedAt())
                .build();
    }

    private List<CustomerDetailResponse.NonConversionReasonInfo> toNonConversionReasonInfos(
            List<NonConversionReason> reasons
    ) {
        return reasons.stream()
                .map(reason -> CustomerDetailResponse.NonConversionReasonInfo.builder()
                        .reasonId(reason.getId())
                        .reasonType(reason.getReasonType())
                        .role(reason.getRole())
                        .reasonBasis(reason.getReasonBasis())
                        .confidence(reason.getConfidence())
                        .build())
                .toList();
    }

    private CustomerDetailResponse.ActiveFollowUp toActiveFollowUp(FollowUp followUp) {
        if (followUp == null) {
            return null;
        }

        return CustomerDetailResponse.ActiveFollowUp.builder()
                .followUpId(followUp.getId())
                .consultationId(followUp.getConsultation().getId())
                .recommendContactDate(followUp.getRecommendContactDate())
                .status(followUp.getStatus())
                .memo(followUp.getMemo())
                .build();
    }

    private CustomerDetailResponse.NextBestAction toNextBestAction(FollowUpAiInsight aiInsight) {
        if (aiInsight == null) {
            return null;
        }

        Map<String, Object> actionBasis = aiInsight.getActionBasis();

        return CustomerDetailResponse.NextBestAction.builder()
                .title(getStringValue(actionBasis, NEXT_ACTION_TITLE_KEY))
                .description(getStringValue(actionBasis, NEXT_ACTION_DESCRIPTION_KEY))
                .persuasionPoint(aiInsight.getPersuasionPoint())
                .cautionNote(aiInsight.getCautionNote())
                .actionBasis(actionBasis)
                .build();
    }

    private CustomerDetailResponse.LatestMessageTemplate toLatestMessageTemplate(MessageTemplate messageTemplate) {
        if (messageTemplate == null) {
            return null;
        }

        return CustomerDetailResponse.LatestMessageTemplate.builder()
                .messageTemplateId(messageTemplate.getId())
                .content(messageTemplate.getContent())
                .tonePreset(messageTemplate.getTonePreset())
                .versionType(messageTemplate.getVersionType())
                .deliveryStatus(messageTemplate.getDeliveryStatus())
                .generatedAt(messageTemplate.getGeneratedAt())
                .build();
    }

    private List<CustomerDetailResponse.TimelineItem> toTimelineItems(List<CustomerActivityTimeline> timeline) {
        return timeline.stream()
                .map(item -> CustomerDetailResponse.TimelineItem.builder()
                        .timelineId(item.getId())
                        .activityType(item.getActivityType())
                        .title(item.getTitle())
                        .description(item.getDescription())
                        .relatedType(item.getRelatedType())
                        .relatedId(item.getRelatedId())
                        .occurredAt(item.getOccurredAt())
                        .build())
                .toList();
    }

    private String getStringValue(Map<String, Object> values, String key) {
        if (values == null) {
            return null;
        }

        Object value = values.get(key);
        return value instanceof String stringValue ? stringValue : null;
    }
}
