package com.fitback.domain.customer.service;

import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.dto.response.CustomerDetailResponse;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import com.fitback.domain.customer.entity.CustomerAiInsight;
import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.entity.FollowUpAiInsight;
import com.fitback.domain.customer.entity.MessageTemplate;
import com.fitback.domain.customer.entity.NonConversionReason;
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
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
