package com.fitback.domain.customer.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewRequest;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.event.ConsultationCreatedEvent;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.dto.request.ReconsultationCheckPreviewRequest;
import com.fitback.domain.customer.dto.request.ReconsultationCreateRequest;
import com.fitback.domain.customer.dto.response.CustomerDetailResponse;
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
