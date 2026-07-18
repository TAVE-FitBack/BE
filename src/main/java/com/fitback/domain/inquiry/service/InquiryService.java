package com.fitback.domain.inquiry.service;

import com.fitback.domain.store.entity.InflowPathOption;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import com.fitback.domain.customer.entity.InterestService;
import com.fitback.domain.customer.enums.ActivityRelatedType;
import com.fitback.domain.customer.enums.CustomerActivityType;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.repository.CustomerActivityTimelineRepository;
import com.fitback.domain.customer.repository.CustomerRepository;
import com.fitback.domain.store.repository.InflowPathOptionRepository;
import com.fitback.domain.customer.repository.InterestServiceRepository;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.dto.ConsultationMaterialFileData;
import com.fitback.domain.consultation.entity.ConsultationMaterial;
import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.event.ConsultationCreatedEvent;
import com.fitback.domain.consultation.repository.ConsultationMaterialRepository;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.consultation.service.ConsultationMaterialFileService;
import com.fitback.domain.inquiry.client.AiInquiryClient;
import com.fitback.domain.inquiry.dto.request.AiInquiryCheckPreviewRequest;
import com.fitback.domain.inquiry.dto.request.InquiryCheckPreviewRequest;
import com.fitback.domain.inquiry.dto.request.InquiryCreateRequest;
import com.fitback.domain.inquiry.dto.response.InquiryCreateResponse;
import com.fitback.domain.inquiry.dto.response.InquiryConvertToConsultationResponse;
import com.fitback.domain.inquiry.dto.response.InquiryNewResponse;
import com.fitback.domain.inquiry.dto.response.InquiryStatusInfo;
import com.fitback.domain.inquiry.entity.Inquiry;
import com.fitback.domain.inquiry.enums.InquiryStatus;
import com.fitback.domain.inquiry.exception.InquiryErrorCode;
import com.fitback.domain.inquiry.repository.InquiryRepository;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import com.fitback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private static final String INQUIRY_TAB_REDIRECT_URL = "/customers/manage?tab=inquiry";
    private static final String CONSULTATION_TAB_REDIRECT_URL =
            "/customers/manage?tab=consultation&customerId=";
    private static final String CUSTOMER_PHONE_UNIQUE_CONSTRAINT = "UK_CUSTOMER_STORE_PHONE";

    private final ServiceRepository serviceRepository;
    private final InflowPathOptionRepository inflowPathOptionRepository;
    private final UserRepository userRepository;
    private final InquiryRepository inquiryRepository;
    private final AiInquiryClient aiInquiryClient;
    private final CustomerRepository customerRepository;
    private final InterestServiceRepository interestServiceRepository;
    private final ConsultationRepository consultationRepository;
    private final ConsultationMaterialRepository consultationMaterialRepository;
    private final ConsultationMaterialFileService consultationMaterialFileService;
    private final CustomerActivityTimelineRepository customerActivityTimelineRepository;
    private final ApplicationEventPublisher eventPublisher;

    public InquiryNewResponse getNewInquiryData(UUID storeId) {
        if (storeId == null) {
            throw new BusinessException(InquiryErrorCode.STORE_NOT_ASSIGNED);
        }

        List<InquiryNewResponse.ServiceInfo> services = serviceRepository
                .findAllByStoreIdAndActiveTrue(storeId)
                .stream()
                .map(service -> InquiryNewResponse.ServiceInfo.builder()
                        .serviceId(service.getId())
                        .name(service.getName())
                        .build())
                .toList();

        List<InquiryNewResponse.InflowPathInfo> inflowPaths = inflowPathOptionRepository
                .findAllByStoreIdAndActiveTrueOrderByDisplayOrderAsc(storeId)
                .stream()
                .map(inflowPath -> InquiryNewResponse.InflowPathInfo.builder()
                        .inflowPathId(inflowPath.getId())
                        .name(inflowPath.getName())
                        .displayOrder(inflowPath.getDisplayOrder())
                        .build())
                .toList();

        List<InquiryNewResponse.CounselorInfo> counselors = userRepository
                .findAllByStore_Id(storeId)
                .stream()
                .map(user -> InquiryNewResponse.CounselorInfo.builder()
                        .userId(user.getId())
                        .name(user.getNickname())
                        .build())
                .toList();

        List<InquiryStatusInfo> inquiryStatuses = Arrays.stream(InquiryStatus.values())
                .filter(InquiryStatus::isInputAllowed)
                .map(InquiryStatusInfo::from)
                .toList();

        return InquiryNewResponse.builder()
                .services(services)
                .inflowPaths(inflowPaths)
                .counselors(counselors)
                .inquiryStatuses(inquiryStatuses)
                .build();
    }

    public Map<String, Object> checkPreview(UUID storeId, InquiryCheckPreviewRequest request) {
        if (storeId == null) {
            throw new BusinessException(InquiryErrorCode.STORE_NOT_ASSIGNED);
        }

        InquiryStatus inquiryStatus = request.getInquiry().getInquiryStatus();
        if (!inquiryStatus.isInputAllowed()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Service service = serviceRepository
                .findByIdAndStoreIdAndActiveTrue(request.getInquiry().getServiceId(), storeId)
                .orElseThrow(() -> new BusinessException(InquiryErrorCode.SERVICE_NOT_FOUND));

        AiInquiryCheckPreviewRequest aiRequest = AiInquiryCheckPreviewRequest.builder()
                .rawText(request.getInquiry().getRawText())
                .serviceName(service.getName())
                .inquiryStatus(inquiryStatus)
                .customerInfo(AiInquiryCheckPreviewRequest.CustomerInfo.builder()
                        .name(request.getCustomer().getName())
                        .gender(request.getCustomer().getGender())
                        .birthDate(request.getCustomer().getBirthDate())
                        .build())
                .build();

        return aiInquiryClient.checkPreview(aiRequest);
    }

    @Transactional
    public void deleteInquiry(UUID storeId, UUID inquiryId) {
        if (storeId == null) {
            throw new BusinessException(InquiryErrorCode.STORE_NOT_ASSIGNED);
        }

        Inquiry inquiry = inquiryRepository.findByIdAndStore_Id(inquiryId, storeId)
                .orElseThrow(() -> new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND));

        if (inquiry.getInquiryStatus() == InquiryStatus.CONVERTED) {
            throw new BusinessException(InquiryErrorCode.INQUIRY_ALREADY_CONVERTED);
        }

        inquiryRepository.delete(inquiry);
    }

    @Transactional
    public Inquiry loadInquiryForConversion(UUID storeId, UUID inquiryId) {
        if (storeId == null) {
            throw new BusinessException(InquiryErrorCode.STORE_NOT_ASSIGNED);
        }

        Inquiry inquiry = inquiryRepository.findByIdAndStoreIdForUpdate(inquiryId, storeId)
                .orElseThrow(() -> new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND));

        if (inquiry.getInquiryStatus() == InquiryStatus.CONVERTED) {
            throw new BusinessException(InquiryErrorCode.INQUIRY_ALREADY_CONVERTED);
        }

        serviceRepository.findByIdAndStoreIdAndActiveTrue(inquiry.getService().getId(), storeId)
                .orElseThrow(() -> new BusinessException(InquiryErrorCode.SERVICE_NOT_FOUND));

        inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(
                        inquiry.getInflowPathOption().getId(),
                        storeId
                )
                .orElseThrow(() -> new BusinessException(InquiryErrorCode.INFLOW_PATH_NOT_FOUND));

        userRepository.findByIdAndStore_Id(inquiry.getUser().getId(), storeId)
                .orElseThrow(() -> new BusinessException(InquiryErrorCode.COUNSELOR_NOT_FOUND));

        return inquiry;
    }

    @Transactional
    public InquiryConvertToConsultationResponse convertInquiry(UUID storeId, UUID inquiryId) {
        Inquiry inquiry = loadInquiryForConversion(storeId, inquiryId);
        try {
            InquiryConversionContext context = resolveCustomerConversion(inquiry);
            OffsetDateTime convertedAt = OffsetDateTime.now();

            inquiry.markConverted(context.customer(), context.consultation(), convertedAt);
            connectInquiryMaterialsToConsultation(inquiry, context);
            saveInquiryConvertedTimeline(inquiry, context, convertedAt);
            consultationRepository.flush();
            eventPublisher.publishEvent(new ConsultationCreatedEvent(context.consultation().getId()));

            return InquiryConvertToConsultationResponse.builder()
                    .inquiryId(inquiry.getId())
                    .customerId(context.customer().getId())
                    .consultationId(context.consultation().getId())
                    .sessionNo(context.consultation().getSessionNo())
                    .inquiryStatus(inquiry.getInquiryStatus())
                    .aiAnalysisStatus(context.consultation().getAiAnalysisStatus())
                    .redirectUrl(CONSULTATION_TAB_REDIRECT_URL + context.customer().getId())
                    .build();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(resolveConversionConstraintError(e));
        } catch (DataAccessException e) {
            throw new BusinessException(InquiryErrorCode.CONSULTATION_CREATE_FAILED);
        }
    }

    InquiryConversionContext resolveCustomerConversion(Inquiry inquiry) {
        Optional<Customer> existingCustomer = customerRepository.findByPhoneNumAndStoreIdForUpdate(
                inquiry.getPhoneNum(),
                inquiry.getStore().getId()
        );
        if (existingCustomer.isPresent()) {
            return createExistingCustomerConsultation(existingCustomer.get(), inquiry);
        }

        Customer customer = customerRepository.save(Customer.builder()
                .store(inquiry.getStore())
                .registeredService(null)
                .name(inquiry.getName())
                .gender(inquiry.getGender())
                .birthDate(inquiry.getBirthDate())
                .phoneNum(inquiry.getPhoneNum())
                .preferredContactChannel(inquiry.getPreferredContactChannel())
                .inflowPathOption(inquiry.getInflowPathOption())
                .status(CustomerStatus.PENDING)
                .registeredAt(null)
                .firstConsultAt(inquiry.getInquiredAt().toLocalDate())
                .latestConsultAt(inquiry.getInquiredAt().toLocalDate())
                .build());

        interestServiceRepository.save(InterestService.builder()
                .customer(customer)
                .service(inquiry.getService())
                .build());

        Consultation consultation = consultationRepository.save(Consultation.builder()
                .customer(customer)
                .user(inquiry.getUser())
                .consultedService(inquiry.getService())
                .consultedAt(inquiry.getInquiredAt())
                .sessionNo(1)
                .stage(ConsultationStage.CONSULTATION)
                .sourceType(ConsultationSourceType.INQUIRY)
                .rawText(inquiry.getRawText())
                .aiAnalysisStatus(AiAnalysisStatus.PROCESSING)
                .build());

        return InquiryConversionContext.newCustomer(customer, consultation);
    }

    private InquiryConversionContext createExistingCustomerConsultation(
            Customer customer,
            Inquiry inquiry
    ) {
        int nextSessionNo = consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customer.getId())
                .map(Consultation::getSessionNo)
                .orElse(0) + 1;

        Consultation consultation = consultationRepository.save(Consultation.builder()
                .customer(customer)
                .user(inquiry.getUser())
                .consultedService(inquiry.getService())
                .consultedAt(inquiry.getInquiredAt())
                .sessionNo(nextSessionNo)
                .stage(ConsultationStage.CONSULTATION)
                .sourceType(ConsultationSourceType.INQUIRY)
                .rawText(inquiry.getRawText())
                .aiAnalysisStatus(AiAnalysisStatus.PROCESSING)
                .build());

        customer.updateLatestConsultAt(inquiry.getInquiredAt().toLocalDate());

        boolean interestServiceExists = interestServiceRepository.existsByCustomerIdAndServiceId(
                customer.getId(),
                inquiry.getService().getId()
        );
        if (!interestServiceExists) {
            interestServiceRepository.save(InterestService.builder()
                    .customer(customer)
                    .service(inquiry.getService())
                    .build());
        }

        return InquiryConversionContext.existingCustomer(customer, consultation);
    }

    private void connectInquiryMaterialsToConsultation(
            Inquiry inquiry,
            InquiryConversionContext context
    ) {
        consultationMaterialRepository.findAllByInquiryIdOrderByCreatedAtAsc(inquiry.getId())
                .forEach(material -> material.connectConvertedConsultation(
                        context.customer(),
                        context.consultation()
                ));
    }

    private void saveInquiryConvertedTimeline(
            Inquiry inquiry,
            InquiryConversionContext context,
            OffsetDateTime convertedAt
    ) {
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("newCustomerCreated", context.customerCreated());
        afterValue.put("customerId", context.customer().getId());
        afterValue.put("consultationId", context.consultation().getId());
        afterValue.put("sessionNo", context.consultation().getSessionNo());

        String description = context.customerCreated()
                ? "문의 기록을 기반으로 신규 고객과 첫 상담 기록이 생성되었습니다."
                : "문의 기록을 기반으로 기존 고객에 새로운 상담 기록이 추가되었습니다.";

        customerActivityTimelineRepository.save(CustomerActivityTimeline.builder()
                .store(inquiry.getStore())
                .customer(context.customer())
                .actorUser(inquiry.getUser())
                .activityType(CustomerActivityType.INQUIRY_CONVERTED_TO_CONSULTATION)
                .title("문의가 상담으로 전환되었습니다.")
                .description(description)
                .relatedType(ActivityRelatedType.INQUIRY)
                .relatedId(inquiry.getId())
                .afterValue(afterValue)
                .occurredAt(convertedAt)
                .build());
    }

    private InquiryErrorCode resolveConversionConstraintError(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        if (message != null && message.toUpperCase().contains(CUSTOMER_PHONE_UNIQUE_CONSTRAINT)) {
            return InquiryErrorCode.CUSTOMER_DUPLICATE_CONFLICT;
        }
        return InquiryErrorCode.CONSULTATION_CREATE_FAILED;
    }

    @Transactional
    public InquiryCreateResponse createInquiry(UUID storeId, InquiryCreateRequest request) {
        return createInquiry(storeId, request, null);
    }

    @Transactional
    public InquiryCreateResponse createInquiry(
            UUID storeId,
            InquiryCreateRequest request,
            List<MultipartFile> materials
    ) {
        if (storeId == null) {
            throw new BusinessException(InquiryErrorCode.STORE_NOT_ASSIGNED);
        }

        Service service = serviceRepository
                .findByIdAndStoreIdAndActiveTrue(request.getInquiry().getServiceId(), storeId)
                .orElseThrow(() -> new BusinessException(InquiryErrorCode.SERVICE_NOT_FOUND));

        InflowPathOption inflowPathOption = inflowPathOptionRepository
                .findByIdAndStoreIdAndActiveTrue(request.getCustomer().getInflowPathId(), storeId)
                .orElseThrow(() -> new BusinessException(InquiryErrorCode.INFLOW_PATH_NOT_FOUND));

        User counselor = userRepository
                .findByIdAndStore_Id(request.getInquiry().getUserId(), storeId)
                .orElseThrow(() -> new BusinessException(InquiryErrorCode.COUNSELOR_NOT_FOUND));

        validateInquiryStatus(request.getInquiry());

        Inquiry inquiry = Inquiry.builder()
                .store(counselor.getStore())
                .customer(null)
                .service(service)
                .user(counselor)
                .name(request.getCustomer().getName())
                .gender(request.getCustomer().getGender())
                .birthDate(request.getCustomer().getBirthDate())
                .phoneNum(request.getCustomer().getPhoneNum())
                .preferredContactChannel(request.getCustomer().getPreferredContactChannel())
                .inflowPathOption(inflowPathOption)
                .inquiryStatus(request.getInquiry().getInquiryStatus())
                .inquiredAt(request.getInquiry().getInquiredAt())
                .visitScheduledAt(resolveVisitScheduledAt(request.getInquiry()))
                .rawText(request.getInquiry().getRawText())
                .convertedCustomer(null)
                .convertedConsultation(null)
                .convertedAt(null)
                .build();

        Inquiry savedInquiry = inquiryRepository.save(inquiry);
        saveInquiryMaterials(savedInquiry, counselor, materials);

        return InquiryCreateResponse.builder()
                .inquiryId(savedInquiry.getId())
                .redirectUrl(INQUIRY_TAB_REDIRECT_URL)
                .build();
    }

    private void saveInquiryMaterials(Inquiry inquiry, User counselor, List<MultipartFile> materials) {
        if (materials == null || materials.isEmpty()) {
            return;
        }

        List<ConsultationMaterialFileData> materialData = consultationMaterialFileService.extractMaterials(materials);
        if (materialData.isEmpty()) {
            return;
        }

        List<ConsultationMaterial> consultationMaterials = materialData.stream()
                .map(data -> ConsultationMaterial.builder()
                        .store(inquiry.getStore())
                        .customer(null)
                        .consultation(null)
                        .inquiry(inquiry)
                        .materialType(data.getMaterialType())
                        .title(data.getTitle())
                        .originalFileName(data.getOriginalFileName())
                        .contentType(data.getContentType())
                        .fileSize(data.getFileSize())
                        .content(data.getContent())
                        .createdBy(counselor)
                        .build())
                .toList();

        consultationMaterialRepository.saveAll(consultationMaterials);
    }

    private void validateInquiryStatus(InquiryCreateRequest.InquiryInfo inquiry) {
        if (!inquiry.getInquiryStatus().isInputAllowed()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (inquiry.getInquiryStatus() == InquiryStatus.VISIT_SCHEDULED
                && inquiry.getVisitScheduledAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private OffsetDateTime resolveVisitScheduledAt(InquiryCreateRequest.InquiryInfo inquiry) {
        if (inquiry.getInquiryStatus() != InquiryStatus.VISIT_SCHEDULED) {
            return null;
        }

        return inquiry.getVisitScheduledAt();
    }
}
