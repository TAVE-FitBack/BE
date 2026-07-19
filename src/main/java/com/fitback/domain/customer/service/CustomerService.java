package com.fitback.domain.customer.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.dto.ConsultationMaterialFileData;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewRequest;
import com.fitback.domain.consultation.dto.request.AiNextActionRegenerateRequest;
import com.fitback.domain.consultation.dto.response.AiNextActionRegenerateResponse;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.entity.ConsultationMaterial;
import com.fitback.domain.consultation.entity.ConsultationSignal;
import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.ConsultationRegistrationStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.event.ConsultationCreatedEvent;
import com.fitback.domain.consultation.repository.ConsultationMaterialRepository;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.consultation.service.ConsultationMaterialFileService;
import com.fitback.domain.consultation.service.ConsultationSignalService;
import com.fitback.domain.customer.client.AiMessageClient;
import com.fitback.domain.customer.dto.request.AiMessageGenerateRequest;
import com.fitback.domain.customer.dto.request.CustomerAiAnalysisUpdateRequest;
import com.fitback.domain.customer.dto.request.CustomerStatusUpdateRequest;
import com.fitback.domain.customer.dto.request.FollowUpReplyUpdateRequest;
import com.fitback.domain.customer.dto.request.MessageTemplateCreateRequest;
import com.fitback.domain.customer.dto.request.MessageTemplateMarkSentRequest;
import com.fitback.domain.customer.dto.request.NextActionRegenerateRequest;
import com.fitback.domain.customer.dto.request.ReconsultationCheckPreviewRequest;
import com.fitback.domain.customer.dto.request.ReconsultationCreateRequest;
import com.fitback.domain.customer.dto.response.AiMessageGenerateResponse;
import com.fitback.domain.customer.dto.response.CustomerAiAnalysisUpdateResponse;
import com.fitback.domain.customer.dto.response.CustomerStatusUpdateResponse;
import com.fitback.domain.customer.dto.response.CustomerDetailResponse;
import com.fitback.domain.customer.dto.response.FollowUpReplyUpdateResponse;
import com.fitback.domain.customer.dto.response.MessageTemplateCreateResponse;
import com.fitback.domain.customer.dto.response.MessageTemplateMarkSentResponse;
import com.fitback.domain.customer.dto.response.MessageTemplateOptionsResponse;
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
import com.fitback.domain.customer.enums.ConversionSource;
import com.fitback.domain.customer.enums.CustomerActivityType;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.enums.MessageDeliveryStatus;
import com.fitback.domain.customer.enums.MessageTonePreset;
import com.fitback.domain.customer.enums.MessageVersionType;
import com.fitback.domain.customer.exception.CustomerErrorCode;
import com.fitback.domain.customer.repository.CustomerActivityTimelineRepository;
import com.fitback.domain.customer.repository.CustomerAiInsightRepository;
import com.fitback.domain.customer.repository.CustomerRepository;
import com.fitback.domain.customer.repository.EventQueryRepository;
import com.fitback.domain.customer.repository.FollowUpAiInsightRepository;
import com.fitback.domain.customer.repository.FollowUpRepository;
import com.fitback.domain.customer.repository.MessageTemplateRepository;
import com.fitback.domain.customer.repository.NonConversionReasonRepository;
import com.fitback.domain.inquiry.entity.Inquiry;
import com.fitback.domain.inquiry.repository.InquiryRepository;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.exception.UserErrorCode;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final ConsultationMaterialRepository consultationMaterialRepository;
    private final CustomerAiInsightRepository customerAiInsightRepository;
    private final NonConversionReasonRepository nonConversionReasonRepository;
    private final FollowUpRepository followUpRepository;
    private final FollowUpAiInsightRepository followUpAiInsightRepository;
    private final MessageTemplateRepository messageTemplateRepository;
    private final CustomerActivityTimelineRepository customerActivityTimelineRepository;
    private final InquiryRepository inquiryRepository;
    private final EventQueryRepository eventQueryRepository;
    private final ServiceRepository serviceRepository;
    private final AiConsultationClient aiConsultationClient;
    private final AiMessageClient aiMessageClient;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FollowUpConversionService followUpConversionService;
    private final ConsultationMaterialFileService consultationMaterialFileService;
    private final ConsultationSignalService consultationSignalService;

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
        List<ConsultationSignal> consultationSignals = latestConsultation == null
                ? List.of()
                : consultationSignalService.findCustomerDetailCardSignals(latestConsultation.getId());

        CustomerAiInsight aiInsight = customerAiInsightRepository
                .findById(customerId)
                .orElse(null);

        List<NonConversionReason> nonConversionReasons = nonConversionReasonRepository
                .findAllByCustomerIdOrderByUpdatedAtDesc(customerId);

        FollowUp activeFollowUp = followUpRepository.findActiveByCustomerId(customerId)
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
                .consultationSignalSnapshot(toConsultationSignalSnapshot(latestConsultation, consultationSignals))
                .aiInsight(toAiInsight(aiInsight))
                .nonConversionReasons(toNonConversionReasonInfos(nonConversionReasons))
                .activeFollowUp(toActiveFollowUp(activeFollowUp))
                .nextBestAction(toNextBestAction(followUpAiInsight))
                .latestMessageTemplate(toLatestMessageTemplate(latestMessageTemplate))
                .timeline(toTimelineItems(storeId, customerId, timeline))
                .build();
    }

    public MessageTemplateOptionsResponse getMessageTemplateOptions(UUID storeId, UUID customerId) {
        if (storeId == null) {
            throw new BusinessException(CustomerErrorCode.STORE_NOT_ASSIGNED);
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        if (!storeId.equals(customer.getStore().getId())) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_ACCESS_DENIED);
        }

        return MessageTemplateOptionsResponse.builder()
                .tonePresets(toTonePresetOptions())
                .versionTypes(toVersionTypeOptions())
                .events(toEventOptions(eventQueryRepository.findActiveEventsByStoreId(storeId)))
                .build();
    }

    @Transactional
    public FollowUpReplyUpdateResponse updateFollowUpReply(
            UUID storeId,
            UUID followUpId,
            FollowUpReplyUpdateRequest request
    ) {
        if (storeId == null) {
            throw new BusinessException(CustomerErrorCode.STORE_NOT_ASSIGNED);
        }

        FollowUp followUp = followUpRepository.findByIdAndCustomer_Store_Id(followUpId, storeId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.FOLLOW_UP_NOT_FOUND));

        boolean hasReply = Boolean.TRUE.equals(request.getHasReply());
        OffsetDateTime repliedAt = hasReply
                ? OffsetDateTime.now()
                : null;
        followUp.updateReply(hasReply, repliedAt);

        return FollowUpReplyUpdateResponse.builder()
                .followUpId(followUp.getId())
                .hasReply(followUp.isHasReply())
                .repliedAt(followUp.getRepliedAt())
                .build();
    }

    @Transactional
    public MessageTemplateCreateResponse createMessageTemplate(
            UUID storeId,
            UUID userId,
            UUID customerId,
            MessageTemplateCreateRequest request
    ) {
        if (storeId == null) {
            throw new BusinessException(CustomerErrorCode.STORE_NOT_ASSIGNED);
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        if (!storeId.equals(customer.getStore().getId())) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_ACCESS_DENIED);
        }

        User actorUser = userRepository.findByIdAndStore_Id(userId, storeId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        FollowUp followUp = followUpRepository.findById(request.getFollowUpId())
                .filter(foundFollowUp -> customerId.equals(foundFollowUp.getCustomer().getId()))
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.ACTIVE_FOLLOW_UP_NOT_FOUND));

        if (followUp.getStatus() != FollowUpStatus.PENDING) {
            throw new BusinessException(CustomerErrorCode.FOLLOW_UP_NOT_PENDING);
        }

        Consultation latestConsultation = consultationRepository
                .findFirstByCustomerIdOrderBySessionNoDesc(customerId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.CONSULTATION_NOT_FOUND));

        FollowUpAiInsight followUpAiInsight = followUpAiInsightRepository.findById(followUp.getId())
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.NEXT_ACTION_NOT_FOUND));

        CustomerAiInsight customerAiInsight = customerAiInsightRepository.findById(customerId)
                .orElse(null);

        List<NonConversionReason> nonConversionReasons = nonConversionReasonRepository
                .findAllByCustomerIdOrderByUpdatedAtDesc(customerId);

        EventQueryRepository.EventOptionRow event = request.getEventId() == null
                ? null
                : eventQueryRepository.findActiveEventByIdAndStoreId(request.getEventId(), storeId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.EVENT_NOT_FOUND));

        AiMessageGenerateResponse aiResponse = aiMessageClient.generateMessage(
                buildMessageGenerateAiRequest(
                        customer,
                        latestConsultation,
                        customerAiInsight,
                        nonConversionReasons,
                        followUpAiInsight,
                        event,
                        request
                )
        );

        OffsetDateTime now = OffsetDateTime.now();
        MessageTemplate messageTemplate = MessageTemplate.builder()
                .followUp(followUp)
                .eventId(event != null ? event.eventId() : null)
                .customer(customer)
                .content(aiResponse.getContent())
                .versionType(request.getVersionType().name())
                .tonePreset(request.getTonePreset().name())
                .deliveryStatus(MessageDeliveryStatus.DRAFT.name())
                .contactRound(followUp.getContactRound())
                .scheduledAt(null)
                .generatedAt(now)
                .updatedAt(now)
                .build();
        MessageTemplate savedMessageTemplate = messageTemplateRepository.save(messageTemplate);
        saveMessageTemplateCreatedTimeline(customer, actorUser, savedMessageTemplate);

        return MessageTemplateCreateResponse.builder()
                .messageTemplateId(savedMessageTemplate.getId())
                .customerId(customer.getId())
                .followUpId(followUp.getId())
                .content(savedMessageTemplate.getContent())
                .versionType(request.getVersionType())
                .tonePreset(request.getTonePreset())
                .deliveryStatus(MessageDeliveryStatus.DRAFT)
                .generatedAt(savedMessageTemplate.getGeneratedAt())
                .build();
    }

    @Transactional
    public MessageTemplateMarkSentResponse markMessageTemplateSent(
            UUID storeId,
            UUID userId,
            UUID messageTemplateId,
            MessageTemplateMarkSentRequest request
    ) {
        if (storeId == null) {
            throw new BusinessException(CustomerErrorCode.STORE_NOT_ASSIGNED);
        }

        MessageTemplate messageTemplate = messageTemplateRepository.findByIdAndCustomer_Store_Id(messageTemplateId, storeId)
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.MESSAGE_TEMPLATE_NOT_FOUND));

        Customer customer = messageTemplate.getCustomer();

        User actorUser = userRepository.findByIdAndStore_Id(userId, storeId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        FollowUp followUp = messageTemplate.getFollowUp();
        if (followUp == null) {
            throw new BusinessException(CustomerErrorCode.FOLLOW_UP_NOT_FOUND);
        }

        if (followUp.getStatus() != FollowUpStatus.PENDING) {
            throw new BusinessException(CustomerErrorCode.FOLLOW_UP_NOT_PENDING);
        }

        OffsetDateTime sentAt = request != null && request.getSentAt() != null
                ? request.getSentAt()
                : OffsetDateTime.now();
        String beforeDeliveryStatus = messageTemplate.getDeliveryStatus();

        messageTemplate.markSent(sentAt);
        if (customer.getStatus() == CustomerStatus.REGISTERED || customer.getStatus() == CustomerStatus.LOST) {
            followUp.markClosed();
        } else {
            markFollowUpSentOrCompleted(followUp);
        }
        saveMessageSentTimeline(customer, actorUser, messageTemplate, beforeDeliveryStatus);

        return MessageTemplateMarkSentResponse.builder()
                .messageTemplateId(messageTemplate.getId())
                .deliveryStatus(MessageDeliveryStatus.SENT)
                .sentAt(messageTemplate.getSentAt())
                .followUpId(followUp.getId())
                .followUpStatus(followUp.getStatus())
                .contactRound(followUp.getContactRound())
                .build();
    }

    private void markFollowUpSentOrCompleted(FollowUp followUp) {
        if (followUp.getContactRound() >= 3) {
            followUp.markCompleted();
            return;
        }
        followUp.markSent();
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
        return createReconsultation(storeId, customerId, request, null);
    }

    @Transactional
    public ReconsultationCreateResponse createReconsultation(
            UUID storeId,
            UUID customerId,
            ReconsultationCreateRequest request,
            List<MultipartFile> materials
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

        ConsultationRegistrationStatus registrationStatus = request.getRegistrationStatus();
        Service registeredService = resolveReconsultationRegisteredService(
                registrationStatus,
                request.getRegisteredServiceId(),
                service,
                storeId
        );

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
        consultationSignalService.saveSnapshot(savedConsultation, request.getAiCheckPreview());
        customer.updateLatestConsultAt(request.getConsultation().getConsultedAt().toLocalDate());

        CustomerStatus beforeStatus = customer.getStatus();
        CustomerStatus afterStatus = resolveReconsultationCustomerStatus(registrationStatus);
        String followUpAction = resolveFollowUpAction(afterStatus);

        if (afterStatus == CustomerStatus.REGISTERED) {
            customer.markRegistered(registeredService, request.getConsultation().getConsultedAt());
        } else {
            customer.markStatus(afterStatus);
        }

        FollowUp activeFollowUp = followUpRepository
                .findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING)
                .orElse(null);
        if (FOLLOW_UP_ACTION_CLOSED.equals(followUpAction) && activeFollowUp != null) {
            activeFollowUp.markClosed();
        }

        boolean followUpConversionCreated = false;
        if (beforeStatus != CustomerStatus.REGISTERED && afterStatus == CustomerStatus.REGISTERED) {
            followUpConversionCreated = followUpConversionService.recordConversionIfAbsent(
                    customer,
                    customer.getRegisteredAt(),
                    ConversionSource.RECONSULTATION
            );
        }

        saveReconsultationCreatedTimeline(customer, counselor, service, savedConsultation);
        if (beforeStatus != afterStatus) {
            saveCustomerStatusChangedTimeline(customer, savedConsultation, beforeStatus, afterStatus, followUpAction);
        }
        saveReconsultationMaterials(customer, savedConsultation, counselor, materials);
        eventPublisher.publishEvent(new ConsultationCreatedEvent(savedConsultation.getId()));

        return ReconsultationCreateResponse.builder()
                .customerId(customer.getId())
                .consultationId(savedConsultation.getId())
                .sessionNo(savedConsultation.getSessionNo())
                .registrationStatus(registrationStatus)
                .registeredServiceId(registeredService != null ? registeredService.getId() : null)
                .followUpAction(followUpAction)
                .followUpConversionCreated(followUpConversionCreated)
                .aiAnalysisStatus(savedConsultation.getAiAnalysisStatus())
                .build();
    }

    private void saveReconsultationMaterials(
            Customer customer,
            Consultation consultation,
            User counselor,
            List<MultipartFile> materials
    ) {
        if (materials == null || materials.isEmpty()) {
            return;
        }

        List<ConsultationMaterialFileData> materialData = consultationMaterialFileService.extractMaterials(materials);
        if (materialData.isEmpty()) {
            return;
        }

        List<ConsultationMaterial> entities = materialData.stream()
                .map(data -> ConsultationMaterial.builder()
                        .store(customer.getStore())
                        .customer(customer)
                        .consultation(consultation)
                        .inquiry(null)
                        .materialType(data.getMaterialType())
                        .title(data.getTitle())
                        .originalFileName(data.getOriginalFileName())
                        .contentType(data.getContentType())
                        .fileSize(data.getFileSize())
                        .content(data.getContent())
                        .createdBy(counselor)
                        .build())
                .toList();

        consultationMaterialRepository.saveAll(entities);
    }

    private Service resolveReconsultationRegisteredService(
            ConsultationRegistrationStatus registrationStatus,
            UUID registeredServiceId,
            Service consultedService,
            UUID storeId
    ) {
        if (registrationStatus != ConsultationRegistrationStatus.REGISTERED) {
            return null;
        }
        if (registeredServiceId == null) {
            throw new BusinessException(CustomerErrorCode.INVALID_CUSTOMER_STATUS);
        }
        if (registeredServiceId.equals(consultedService.getId())) {
            return consultedService;
        }
        return serviceRepository
                .findByIdAndStoreIdAndActiveTrue(registeredServiceId, storeId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.SERVICE_NOT_FOUND));
    }

    private CustomerStatus resolveReconsultationCustomerStatus(ConsultationRegistrationStatus registrationStatus) {
        return switch (registrationStatus) {
            case REGISTERED -> CustomerStatus.REGISTERED;
            case PENDING -> CustomerStatus.PENDING;
            case SCHEDULED -> CustomerStatus.SCHEDULED;
            case LOST -> CustomerStatus.LOST;
        };
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

        FollowUp activeFollowUp = followUpRepository.findActiveByCustomerId(customerId)
                .orElse(null);
        int nextContactRound = resolveNextContactRound(activeFollowUp);

        AiNextActionRegenerateResponse aiResponse = aiConsultationClient.regenerateNextAction(
                buildNextActionAiRequest(customer, latestConsultation, customerAiInsight, reasons)
        );

        Map<String, Object> oldFollowUpBeforeValue = activeFollowUp != null
                ? buildFollowUpTimelineValue(activeFollowUp, null)
                : null;
        if (activeFollowUp != null) {
            activeFollowUp.markSuperseded();
        }

        FollowUp newFollowUp = FollowUp.builder()
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(aiResponse.getFollowUp().getRecommendContactDate())
                .status(FollowUpStatus.PENDING)
                .contactRound(nextContactRound)
                .memo(resolveNextActionMemo(aiResponse))
                .build();
        FollowUp savedFollowUp = followUpRepository.save(newFollowUp);

        saveRegeneratedFollowUpAiInsight(savedFollowUp, aiResponse);
        customerAiInsight.updatePriorityScore(aiResponse.getPriorityScore(), OffsetDateTime.now());
        customerAiInsightRepository.save(customerAiInsight);
        saveNextActionRegeneratedTimeline(customer, latestConsultation, oldFollowUpBeforeValue, savedFollowUp, aiResponse);

        return NextActionRegenerateResponse.builder()
                .customerId(customer.getId())
                .oldFollowUpId(activeFollowUp != null ? activeFollowUp.getId() : null)
                .oldFollowUpStatus(activeFollowUp != null ? activeFollowUp.getStatus() : null)
                .newFollowUpId(savedFollowUp.getId())
                .newFollowUpStatus(savedFollowUp.getStatus())
                .contactRound(savedFollowUp.getContactRound())
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
            customer.markRegistered(registeredService, OffsetDateTime.now());
        } else {
            customer.markStatus(afterStatus);
        }

        FollowUp activeFollowUp = followUpRepository
                .findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING)
                .orElse(null);
        if (FOLLOW_UP_ACTION_CLOSED.equals(followUpAction) && activeFollowUp != null) {
            activeFollowUp.markClosed();
        }
        if (beforeStatus != CustomerStatus.REGISTERED && afterStatus == CustomerStatus.REGISTERED) {
            followUpConversionService.recordConversionIfAbsent(
                    customer,
                    customer.getRegisteredAt(),
                    ConversionSource.UNKNOWN
            );
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

    private List<MessageTemplateOptionsResponse.TonePresetOption> toTonePresetOptions() {
        return Arrays.stream(MessageTonePreset.values())
                .map(tonePreset -> MessageTemplateOptionsResponse.TonePresetOption.builder()
                        .tonePreset(tonePreset)
                        .label(tonePreset.getLabel())
                        .build())
                .toList();
    }

    private List<MessageTemplateOptionsResponse.VersionTypeOption> toVersionTypeOptions() {
        return Arrays.stream(MessageVersionType.values())
                .map(versionType -> MessageTemplateOptionsResponse.VersionTypeOption.builder()
                        .versionType(versionType)
                        .label(versionType.getLabel())
                        .build())
                .toList();
    }

    private List<MessageTemplateOptionsResponse.EventOption> toEventOptions(
            List<EventQueryRepository.EventOptionRow> events
    ) {
        return events.stream()
                .map(event -> MessageTemplateOptionsResponse.EventOption.builder()
                        .eventId(event.eventId())
                        .title(event.title())
                        .eventType(event.eventType())
                        .description(event.description())
                        .discountRate(event.discountRate())
                        .startDate(event.startDate())
                        .endDate(event.endDate())
                        .build())
                .toList();
    }

    private AiMessageGenerateRequest buildMessageGenerateAiRequest(
            Customer customer,
            Consultation latestConsultation,
            CustomerAiInsight customerAiInsight,
            List<NonConversionReason> nonConversionReasons,
            FollowUpAiInsight followUpAiInsight,
            EventQueryRepository.EventOptionRow event,
            MessageTemplateCreateRequest request
    ) {
        return AiMessageGenerateRequest.builder()
                .customer(AiMessageGenerateRequest.CustomerInfo.builder()
                        .customerId(customer.getId())
                        .name(customer.getName())
                        .preferredContactChannel(customer.getPreferredContactChannel())
                        .status(customer.getStatus())
                        .build())
                .latestConsultation(AiMessageGenerateRequest.LatestConsultationInfo.builder()
                        .consultationId(latestConsultation.getId())
                        .summary(latestConsultation.getSummary())
                        .build())
                .aiInsight(AiMessageGenerateRequest.AiInsightInfo.builder()
                        .leadTemperature(customerAiInsight != null ? customerAiInsight.getLeadTemperature() : null)
                        .priorityScore(customerAiInsight != null ? customerAiInsight.getPriorityScore() : null)
                        .build())
                .nonConversionReasons(nonConversionReasons.stream()
                        .map(reason -> AiMessageGenerateRequest.NonConversionReasonInfo.builder()
                                .reasonType(reason.getReasonType())
                                .role(reason.getRole())
                                .reasonBasis(reason.getReasonBasis())
                                .build())
                        .toList())
                .nextBestAction(AiMessageGenerateRequest.NextBestActionInfo.builder()
                        .title(getStringValue(followUpAiInsight.getActionBasis(), NEXT_ACTION_TITLE_KEY))
                        .description(getStringValue(followUpAiInsight.getActionBasis(), NEXT_ACTION_DESCRIPTION_KEY))
                        .persuasionPoint(followUpAiInsight.getPersuasionPoint())
                        .cautionNote(followUpAiInsight.getCautionNote())
                        .actionBasis(followUpAiInsight.getActionBasis())
                        .build())
                .event(toAiEventInfo(event))
                .messageOptions(AiMessageGenerateRequest.MessageOptions.builder()
                        .tonePreset(request.getTonePreset())
                        .versionType(request.getVersionType())
                        .additionalInstruction(request.getAdditionalInstruction())
                        .build())
                .build();
    }

    private AiMessageGenerateRequest.EventInfo toAiEventInfo(EventQueryRepository.EventOptionRow event) {
        if (event == null) {
            return null;
        }

        return AiMessageGenerateRequest.EventInfo.builder()
                .eventId(event.eventId())
                .title(event.title())
                .description(event.description())
                .discountRate(event.discountRate())
                .build();
    }

    private void saveMessageTemplateCreatedTimeline(
            Customer customer,
            User actorUser,
            MessageTemplate messageTemplate
    ) {
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("messageTemplateId", messageTemplate.getId());
        afterValue.put("followUpId", messageTemplate.getFollowUp().getId());
        afterValue.put("tonePreset", messageTemplate.getTonePreset());
        afterValue.put("deliveryStatus", messageTemplate.getDeliveryStatus());

        CustomerActivityTimeline timeline = CustomerActivityTimeline.builder()
                .store(customer.getStore())
                .customer(customer)
                .actorUser(actorUser)
                .activityType(CustomerActivityType.MESSAGE_TEMPLATE_CREATED)
                .title("메시지 초안이 생성되었습니다.")
                .description("다음 최적 액션을 바탕으로 고객에게 보낼 메시지 초안이 생성되었습니다.")
                .relatedType(ActivityRelatedType.MESSAGE_TEMPLATE)
                .relatedId(messageTemplate.getId())
                .afterValue(afterValue)
                .occurredAt(OffsetDateTime.now())
                .build();

        customerActivityTimelineRepository.save(timeline);
    }

    private void saveMessageSentTimeline(
            Customer customer,
            User actorUser,
            MessageTemplate messageTemplate,
            String beforeDeliveryStatus
    ) {
        Map<String, Object> beforeValue = new LinkedHashMap<>();
        beforeValue.put("deliveryStatus", beforeDeliveryStatus);

        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("deliveryStatus", messageTemplate.getDeliveryStatus());
        afterValue.put("sentAt", messageTemplate.getSentAt());

        CustomerActivityTimeline timeline = CustomerActivityTimeline.builder()
                .store(customer.getStore())
                .customer(customer)
                .actorUser(actorUser)
                .activityType(CustomerActivityType.MESSAGE_SENT)
                .title("메시지 전송이 완료되었습니다.")
                .description("사용자가 외부 채널로 메시지 전송을 완료 처리했습니다.")
                .relatedType(ActivityRelatedType.MESSAGE_TEMPLATE)
                .relatedId(messageTemplate.getId())
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .occurredAt(messageTemplate.getSentAt())
                .build();

        customerActivityTimelineRepository.save(timeline);
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

    private int resolveNextContactRound(FollowUp activeFollowUp) {
        if (activeFollowUp == null) {
            return 1;
        }
        int currentRound = activeFollowUp.getContactRound();
        if (activeFollowUp.getStatus() == FollowUpStatus.SENT) {
            if (currentRound >= 3) {
                throw new BusinessException(CustomerErrorCode.FOLLOW_UP_ROUND_LIMIT_EXCEEDED);
            }
            return currentRound + 1;
        }
        return currentRound;
    }

    private Map<String, Object> buildFollowUpTimelineValue(FollowUp followUp, String nextActionTitle) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("followUpId", followUp.getId());
        value.put("status", followUp.getStatus());
        value.put("contactRound", followUp.getContactRound());
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

    private CustomerDetailResponse.ConsultationSignalSnapshot toConsultationSignalSnapshot(
            Consultation consultation,
            List<ConsultationSignal> signals
    ) {
        List<ConsultationSignal> safeSignals = signals == null ? List.of() : signals;

        return CustomerDetailResponse.ConsultationSignalSnapshot.builder()
                .consultationId(consultation == null ? null : consultation.getId())
                .items(safeSignals.stream()
                        .map(signal -> CustomerDetailResponse.ConsultationSignalItem.builder()
                                .key(signal.getSignalKey())
                                .label(signal.getLabel())
                                .confirmed(signal.getConfirmed())
                                .value(signal.getValue())
                                .build())
                        .toList())
                .build();
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

    private List<CustomerDetailResponse.TimelineItem> toTimelineItems(
            UUID storeId,
            UUID customerId,
            List<CustomerActivityTimeline> timeline
    ) {
        TimelineRelatedIds relatedIds = collectTimelineRelatedIds(timeline);
        Map<UUID, Consultation> consultationsById = findTimelineConsultations(
                relatedIds.consultationIds(),
                customerId,
                storeId
        );
        Map<UUID, MessageTemplate> messageTemplatesById = findTimelineMessageTemplates(
                relatedIds.messageTemplateIds(),
                customerId,
                storeId
        );
        Map<UUID, FollowUp> followUpsById = findTimelineFollowUps(
                relatedIds.followUpIds(),
                customerId,
                storeId
        );
        Map<UUID, FollowUpAiInsight> followUpAiInsightsById = findTimelineFollowUpAiInsights(
                relatedIds.followUpIds()
        );
        Map<UUID, Inquiry> inquiriesById = findTimelineInquiries(relatedIds.inquiryIds(), storeId);

        return timeline.stream()
                .map(item -> {
                    Map<String, Object> detail = buildTimelineDetail(
                            item,
                            consultationsById,
                            messageTemplatesById,
                            followUpsById,
                            followUpAiInsightsById,
                            inquiriesById
                    );
                    return CustomerDetailResponse.TimelineItem.builder()
                        .timelineId(item.getId())
                        .activityType(item.getActivityType())
                        .title(item.getTitle())
                        .description(item.getDescription())
                        .summary(buildTimelineSummary(item, detail))
                        .relatedType(item.getRelatedType())
                        .relatedId(item.getRelatedId())
                        .beforeValue(item.getBeforeValue())
                        .afterValue(item.getAfterValue())
                        .occurredAt(item.getOccurredAt())
                        .detail(detail)
                        .build();
                })
                .toList();
    }

    private Map<UUID, Consultation> findTimelineConsultations(
            Set<UUID> consultationIds,
            UUID customerId,
            UUID storeId
    ) {
        if (consultationIds.isEmpty()) {
            return Map.of();
        }
        return consultationRepository
                .findAllTimelineDetailsByIdsAndCustomerIdAndStoreId(consultationIds, customerId, storeId)
                .stream()
                .collect(Collectors.toMap(Consultation::getId, Function.identity()));
    }

    private Map<UUID, MessageTemplate> findTimelineMessageTemplates(
            Set<UUID> messageTemplateIds,
            UUID customerId,
            UUID storeId
    ) {
        if (messageTemplateIds.isEmpty()) {
            return Map.of();
        }
        return messageTemplateRepository
                .findAllByIdInAndCustomerIdAndCustomer_Store_Id(messageTemplateIds, customerId, storeId)
                .stream()
                .collect(Collectors.toMap(MessageTemplate::getId, Function.identity()));
    }

    private Map<UUID, FollowUp> findTimelineFollowUps(
            Set<UUID> followUpIds,
            UUID customerId,
            UUID storeId
    ) {
        if (followUpIds.isEmpty()) {
            return Map.of();
        }
        return followUpRepository
                .findAllTimelineDetailsByIdsAndCustomerIdAndStoreId(followUpIds, customerId, storeId)
                .stream()
                .collect(Collectors.toMap(FollowUp::getId, Function.identity()));
    }

    private Map<UUID, FollowUpAiInsight> findTimelineFollowUpAiInsights(Set<UUID> followUpIds) {
        if (followUpIds.isEmpty()) {
            return Map.of();
        }
        return followUpAiInsightRepository
                .findAllByFollowUpIdIn(followUpIds)
                .stream()
                .collect(Collectors.toMap(FollowUpAiInsight::getFollowUpId, Function.identity()));
    }

    private Map<UUID, Inquiry> findTimelineInquiries(Set<UUID> inquiryIds, UUID storeId) {
        if (inquiryIds.isEmpty()) {
            return Map.of();
        }
        return inquiryRepository
                .findAllTimelineDetailsByIdsAndStoreId(inquiryIds, storeId)
                .stream()
                .collect(Collectors.toMap(Inquiry::getId, Function.identity()));
    }

    private TimelineRelatedIds collectTimelineRelatedIds(List<CustomerActivityTimeline> timeline) {
        Set<UUID> consultationIds = new HashSet<>();
        Set<UUID> messageTemplateIds = new HashSet<>();
        Set<UUID> followUpIds = new HashSet<>();
        Set<UUID> inquiryIds = new HashSet<>();

        for (CustomerActivityTimeline item : timeline) {
            UUID relatedId = item.getRelatedId();
            if (relatedId != null && item.getRelatedType() != null) {
                switch (item.getRelatedType()) {
                    case CONSULTATION -> consultationIds.add(relatedId);
                    case MESSAGE_TEMPLATE -> messageTemplateIds.add(relatedId);
                    case FOLLOW_UP -> followUpIds.add(relatedId);
                    case INQUIRY -> inquiryIds.add(relatedId);
                    case CUSTOMER -> {
                    }
                }
            }

            addUuidFromMap(consultationIds, item.getAfterValue(), "consultationId");
            addUuidFromMap(followUpIds, item.getAfterValue(), "followUpId");
            addUuidFromMap(followUpIds, item.getBeforeValue(), "followUpId");
            addUuidFromMap(messageTemplateIds, item.getAfterValue(), "messageTemplateId");
        }

        return new TimelineRelatedIds(
                consultationIds,
                messageTemplateIds,
                followUpIds,
                inquiryIds
        );
    }

    private void addUuidFromMap(Set<UUID> ids, Map<String, Object> values, String key) {
        UUID id = getUuidValue(values, key);
        if (id != null) {
            ids.add(id);
        }
    }

    private Map<String, Object> buildTimelineDetail(
            CustomerActivityTimeline item,
            Map<UUID, Consultation> consultationsById,
            Map<UUID, MessageTemplate> messageTemplatesById,
            Map<UUID, FollowUp> followUpsById,
            Map<UUID, FollowUpAiInsight> followUpAiInsightsById,
            Map<UUID, Inquiry> inquiriesById
    ) {
        UUID consultationId = resolveRelatedId(item, "consultationId");
        UUID messageTemplateId = resolveRelatedId(item, "messageTemplateId");
        UUID followUpId = resolveRelatedId(item, "followUpId");

        return switch (item.getActivityType()) {
            case CONSULTATION_CREATED, RECONSULTATION_CREATED -> buildConsultationTimelineDetail(
                    item,
                    consultationsById.get(consultationId)
            );
            case MESSAGE_TEMPLATE_CREATED, MESSAGE_SENT -> buildMessageTemplateTimelineDetail(
                    item,
                    messageTemplatesById.get(messageTemplateId)
            );
            case AI_ANALYSIS_COMPLETED, AI_ANALYSIS_FAILED -> buildAiAnalysisTimelineDetail(
                    item,
                    consultationsById.get(consultationId)
            );
            case NEXT_ACTION_CREATED, NEXT_ACTION_REGENERATED, FOLLOW_UP_CREATED, FOLLOW_UP_COMPLETED -> buildFollowUpTimelineDetail(
                    item,
                    followUpsById.get(followUpId),
                    followUpAiInsightsById.get(followUpId)
            );
            case CUSTOMER_STATUS_CHANGED -> buildStatusChangeTimelineDetail(item);
            case INQUIRY_CONVERTED_TO_CONSULTATION -> buildInquiryConversionTimelineDetail(
                    item,
                    inquiriesById.get(item.getRelatedId()),
                    consultationsById.get(getUuidValue(item.getAfterValue(), "consultationId"))
            );
            case AI_ANALYSIS_MANUALLY_UPDATED -> buildAiAnalysisTimelineDetail(item, null);
            case MESSAGE_TEMPLATE_COPIED -> buildMessageTemplateTimelineDetail(
                    item,
                    messageTemplatesById.get(messageTemplateId)
            );
        };
    }

    private UUID resolveRelatedId(CustomerActivityTimeline item, String afterValueKey) {
        if (item.getRelatedId() != null) {
            return item.getRelatedId();
        }
        return getUuidValue(item.getAfterValue(), afterValueKey);
    }

    private Map<String, Object> buildConsultationTimelineDetail(
            CustomerActivityTimeline item,
            Consultation consultation
    ) {
        Map<String, Object> detail = baseTimelineDetail("CONSULTATION", item);
        if (consultation == null) {
            return buildFallbackTimelineDetail(item, "CONSULTATION");
        }

        detail.put("body", consultation.getRawText());
        detail.put("summary", consultation.getSummary());
        detail.put("consultationId", consultation.getId());
        detail.put("sessionNo", consultation.getSessionNo());
        detail.put("consultedAt", consultation.getConsultedAt());
        detail.put("consultedServiceId", consultation.getConsultedService().getId());
        detail.put("consultedServiceName", consultation.getConsultedService().getName());
        detail.put("counselorId", consultation.getUser().getId());
        detail.put("counselorName", consultation.getUser().getNickname());
        detail.put("sourceType", consultation.getSourceType());
        detail.put("stage", consultation.getStage());
        return detail;
    }

    private Map<String, Object> buildMessageTemplateTimelineDetail(
            CustomerActivityTimeline item,
            MessageTemplate messageTemplate
    ) {
        Map<String, Object> detail = baseTimelineDetail("MESSAGE_TEMPLATE", item);
        if (messageTemplate == null) {
            return buildFallbackTimelineDetail(item, "MESSAGE_TEMPLATE");
        }

        detail.put("body", messageTemplate.getContent());
        detail.put("messageTemplateId", messageTemplate.getId());
        detail.put("followUpId", messageTemplate.getFollowUp() != null ? messageTemplate.getFollowUp().getId() : null);
        detail.put("tonePreset", messageTemplate.getTonePreset());
        detail.put("versionType", messageTemplate.getVersionType());
        detail.put("deliveryStatus", messageTemplate.getDeliveryStatus());
        detail.put("generatedAt", messageTemplate.getGeneratedAt());
        detail.put("sentAt", messageTemplate.getSentAt());
        boolean editable = item.getActivityType() == CustomerActivityType.MESSAGE_TEMPLATE_CREATED;
        detail.put("editable", editable);
        if (editable) {
            detail.put("editTargetType", "MESSAGE_TEMPLATE");
            detail.put("editTargetId", messageTemplate.getId());
        }
        return detail;
    }

    private Map<String, Object> buildAiAnalysisTimelineDetail(
            CustomerActivityTimeline item,
            Consultation consultation
    ) {
        Map<String, Object> detail = baseTimelineDetail("AI_ANALYSIS", item);
        if (consultation != null) {
            detail.put("consultationId", consultation.getId());
            detail.put("summary", consultation.getSummary());
        } else {
            detail.put("consultationId", item.getRelatedId());
        }
        putFromMap(detail, item.getAfterValue(), "summary");
        putFromMap(detail, item.getAfterValue(), "leadTemperature");
        putFromMap(detail, item.getAfterValue(), "temperatureBasis");
        putFromMap(detail, item.getAfterValue(), "priorityScore");
        putFromMap(detail, item.getAfterValue(), "primaryReasonType");
        putFromMap(detail, item.getAfterValue(), "nonConversionReasons");
        putFromMap(detail, item.getAfterValue(), "nextBestAction");
        putFromMap(detail, item.getAfterValue(), "followUpId");
        putFromMap(detail, item.getAfterValue(), "status");
        putFromMap(detail, item.getAfterValue(), "errorCode");
        return detail;
    }

    private Map<String, Object> buildFollowUpTimelineDetail(
            CustomerActivityTimeline item,
            FollowUp followUp,
            FollowUpAiInsight followUpAiInsight
    ) {
        Map<String, Object> detail = baseTimelineDetail("FOLLOW_UP", item);
        if (followUp != null) {
            detail.put("followUpId", followUp.getId());
            detail.put("consultationId", followUp.getConsultation().getId());
            detail.put("recommendContactDate", followUp.getRecommendContactDate());
            detail.put("status", followUp.getStatus());
            detail.put("contactRound", followUp.getContactRound());
            detail.put("memo", followUp.getMemo());
            detail.put("body", followUp.getMemo());
        }
        if (followUpAiInsight != null) {
            Map<String, Object> actionBasis = followUpAiInsight.getActionBasis();
            detail.put("nextActionTitle", getStringValue(actionBasis, NEXT_ACTION_TITLE_KEY));
            detail.put("nextActionDescription", getStringValue(actionBasis, NEXT_ACTION_DESCRIPTION_KEY));
            detail.put("persuasionPoint", followUpAiInsight.getPersuasionPoint());
            detail.put("cautionNote", followUpAiInsight.getCautionNote());
            detail.put("actionBasis", actionBasis);
        } else {
            putFromMap(detail, item.getAfterValue(), "nextActionTitle");
        }
        return detail;
    }

    private Map<String, Object> buildFallbackTimelineDetail(
            CustomerActivityTimeline item,
            String type
    ) {
        Map<String, Object> detail = baseTimelineDetail(type, item);
        detail.put("relatedType", item.getRelatedType());
        detail.put("relatedId", item.getRelatedId());
        return detail;
    }

    private Map<String, Object> buildStatusChangeTimelineDetail(CustomerActivityTimeline item) {
        Map<String, Object> detail = baseTimelineDetail("STATUS_CHANGE", item);
        detail.put("beforeStatus", getValue(item.getBeforeValue(), "status"));
        detail.put("afterStatus", getValue(item.getAfterValue(), "status"));
        detail.put("followUpAction", getValue(item.getAfterValue(), "followUpAction"));
        return detail;
    }

    private Map<String, Object> buildInquiryConversionTimelineDetail(
            CustomerActivityTimeline item,
            Inquiry inquiry,
            Consultation consultation
    ) {
        Map<String, Object> detail = baseTimelineDetail("INQUIRY_CONVERSION", item);
        detail.put("inquiryId", item.getRelatedId());
        putFromMap(detail, item.getAfterValue(), "customerId");
        putFromMap(detail, item.getAfterValue(), "consultationId");
        putFromMap(detail, item.getAfterValue(), "sessionNo");
        putFromMap(detail, item.getAfterValue(), "newCustomerCreated");
        if (inquiry != null) {
            detail.put("body", inquiry.getRawText());
            detail.put("inquiryStatus", inquiry.getInquiryStatus());
            detail.put("serviceName", inquiry.getService().getName());
            detail.put("counselorName", inquiry.getUser().getNickname());
        }
        if (consultation != null) {
            detail.put("consultationSummary", consultation.getSummary());
        }
        return detail;
    }

    private Map<String, Object> baseTimelineDetail(String type, CustomerActivityTimeline item) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", type);
        detail.put("heading", item.getTitle());
        detail.put("body", item.getDescription());
        detail.put("editable", false);
        return detail;
    }

    private String buildTimelineSummary(
            CustomerActivityTimeline item,
            Map<String, Object> detail
    ) {
        String summary = switch (item.getActivityType()) {
            case CONSULTATION_CREATED, RECONSULTATION_CREATED -> getStringValue(detail, "body");
            case MESSAGE_TEMPLATE_CREATED, MESSAGE_SENT, MESSAGE_TEMPLATE_COPIED -> getStringValue(detail, "body");
            case CUSTOMER_STATUS_CHANGED -> buildStatusChangeSummary(item);
            case AI_ANALYSIS_COMPLETED, AI_ANALYSIS_FAILED, AI_ANALYSIS_MANUALLY_UPDATED ->
                    buildAiAnalysisSummary(item);
            case NEXT_ACTION_CREATED, NEXT_ACTION_REGENERATED, FOLLOW_UP_CREATED, FOLLOW_UP_COMPLETED ->
                    buildFollowUpSummary(item, detail);
            case INQUIRY_CONVERTED_TO_CONSULTATION -> buildInquiryConversionSummary(item);
        };
        if (summary != null && !summary.isBlank()) {
            return abbreviate(summary);
        }
        Object body = detail.get("body");
        if (body instanceof String bodyText && !bodyText.isBlank()) {
            return abbreviate(bodyText);
        }
        return abbreviate(item.getDescription());
    }

    private String buildStatusChangeSummary(CustomerActivityTimeline item) {
        String beforeStatus = getDisplayValue(item.getBeforeValue(), "status");
        String afterStatus = getDisplayValue(item.getAfterValue(), "status");
        if (beforeStatus != null && afterStatus != null) {
            return beforeStatus + " >> " + afterStatus;
        }
        return null;
    }

    private String buildAiAnalysisSummary(CustomerActivityTimeline item) {
        String leadTemperature = getDisplayValue(item.getAfterValue(), "leadTemperature");
        String priorityScore = getDisplayValue(item.getAfterValue(), "priorityScore");
        String primaryReasonType = getDisplayValue(item.getAfterValue(), "primaryReasonType");
        String status = getDisplayValue(item.getAfterValue(), "status");
        String errorCode = getDisplayValue(item.getAfterValue(), "errorCode");

        if (leadTemperature != null || priorityScore != null || primaryReasonType != null) {
            List<String> parts = new java.util.ArrayList<>();
            if (leadTemperature != null) {
                parts.add("온도 " + leadTemperature);
            }
            if (priorityScore != null) {
                parts.add("점수 " + priorityScore);
            }
            if (primaryReasonType != null) {
                parts.add("주요 사유 " + primaryReasonType);
            }
            return String.join(" · ", parts);
        }
        if (status != null || errorCode != null) {
            return String.join(
                    " · ",
                    java.util.stream.Stream.of(status, errorCode)
                            .filter(value -> value != null && !value.isBlank())
                            .toList()
            );
        }
        return null;
    }

    private String buildFollowUpSummary(
            CustomerActivityTimeline item,
            Map<String, Object> detail
    ) {
        String nextActionTitle = getStringValue(detail, "nextActionTitle");
        if (nextActionTitle != null && !nextActionTitle.isBlank()) {
            return nextActionTitle;
        }
        String memo = getStringValue(detail, "memo");
        if (memo != null && !memo.isBlank()) {
            return memo;
        }
        return getDisplayValue(item.getAfterValue(), "nextActionTitle");
    }

    private String buildInquiryConversionSummary(CustomerActivityTimeline item) {
        Boolean newCustomerCreated = getBooleanValue(item.getAfterValue(), "newCustomerCreated");
        String sessionNo = getDisplayValue(item.getAfterValue(), "sessionNo");
        if (newCustomerCreated != null && sessionNo != null) {
            String customerType = newCustomerCreated ? "신규 고객 생성" : "기존 고객 연결";
            return customerType + " · " + sessionNo + "회차 상담 생성";
        }
        if (newCustomerCreated != null) {
            return newCustomerCreated ? "신규 고객 생성" : "기존 고객 연결";
        }
        if (sessionNo != null) {
            return sessionNo + "회차 상담 생성";
        }
        return null;
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        int maxLength = 80;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private void putFromMap(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = getValue(source, key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private Object getValue(Map<String, Object> values, String key) {
        if (values == null) {
            return null;
        }
        return values.get(key);
    }

    private String getDisplayValue(Map<String, Object> values, String key) {
        Object value = getValue(values, key);
        return value == null ? null : String.valueOf(value);
    }

    private Boolean getBooleanValue(Map<String, Object> values, String key) {
        Object value = getValue(values, key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            if ("true".equalsIgnoreCase(stringValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(stringValue)) {
                return false;
            }
        }
        return null;
    }

    private UUID getUuidValue(Map<String, Object> values, String key) {
        Object value = getValue(values, key);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return UUID.fromString(stringValue);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private record TimelineRelatedIds(
            Set<UUID> consultationIds,
            Set<UUID> messageTemplateIds,
            Set<UUID> followUpIds,
            Set<UUID> inquiryIds
    ) {
    }

    private String getStringValue(Map<String, Object> values, String key) {
        if (values == null) {
            return null;
        }

        Object value = values.get(key);
        return value instanceof String stringValue ? stringValue : null;
    }
}
