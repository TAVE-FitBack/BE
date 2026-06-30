package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewRequest;
import com.fitback.domain.consultation.dto.request.ConsultationCheckPreviewRequest;
import com.fitback.domain.consultation.dto.request.ConsultationCreateRequest;
import com.fitback.domain.consultation.dto.response.ConsultationCreateResponse;
import com.fitback.domain.consultation.dto.response.ConsultationCustomerSearchResponse;
import com.fitback.domain.consultation.dto.response.ConsultationNewResponse;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import com.fitback.domain.customer.entity.InterestService;
import com.fitback.domain.customer.enums.ActivityRelatedType;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.CustomerActivityType;
import com.fitback.domain.customer.enums.InflowPath;
import com.fitback.domain.customer.repository.CustomerActivityTimelineRepository;
import com.fitback.domain.customer.repository.CustomerRepository;
import com.fitback.domain.customer.repository.InterestServiceRepository;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import com.fitback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultationService {

    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final InterestServiceRepository interestServiceRepository;
    private final ConsultationRepository consultationRepository;
    private final CustomerActivityTimelineRepository customerActivityTimelineRepository;
    private final AiConsultationClient aiConsultationClient;

    public ConsultationNewResponse getNewConsultationData(UUID storeId) {
        if (storeId == null) {
            throw new BusinessException(ConsultationErrorCode.STORE_NOT_ASSIGNED);
        }

        List<ConsultationNewResponse.ServiceInfo> services = serviceRepository
                .findAllByStoreIdAndActiveTrue(storeId)
                .stream()
                .map(service -> ConsultationNewResponse.ServiceInfo.builder()
                        .serviceId(service.getId())
                        .name(service.getName())
                        .build())
                .toList();

        List<ConsultationNewResponse.CounselorInfo> counselors = userRepository
                .findAllByStore_Id(storeId)
                .stream()
                .map(user -> ConsultationNewResponse.CounselorInfo.builder()
                        .userId(user.getId())
                        .name(user.getNickname())
                        .build())
                .toList();

        return ConsultationNewResponse.builder()
                .services(services)
                .counselors(counselors)
                .build();
    }

    public Map<String, Object> checkPreview(UUID storeId, ConsultationCheckPreviewRequest request) {
        if (storeId == null) {
            throw new BusinessException(ConsultationErrorCode.STORE_NOT_ASSIGNED);
        }

        Service service = serviceRepository
                .findByIdAndStoreId(request.getConsultation().getConsultedServiceId(), storeId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.SERVICE_NOT_FOUND));

        AiCheckPreviewRequest aiRequest = AiCheckPreviewRequest.builder()
                .rawText(request.getConsultation().getRawText())
                .serviceName(service.getName())
                .customerInfo(AiCheckPreviewRequest.CustomerInfo.builder()
                        .name(request.getCustomer().getName())
                        .gender(request.getCustomer().getGender())
                        .birthDate(request.getCustomer().getBirthDate())
                        .build())
                .build();

        return aiConsultationClient.checkPreview(aiRequest);
    }

    @Transactional
    public ConsultationCreateResponse createConsultation(UUID storeId, ConsultationCreateRequest request) {
        if (storeId == null) {
            throw new BusinessException(ConsultationErrorCode.STORE_NOT_ASSIGNED);
        }

        Service service = serviceRepository
                .findByIdAndStoreId(request.getConsultation().getConsultedServiceId(), storeId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.SERVICE_NOT_FOUND));

        User counselor = userRepository
                .findByIdAndStore_Id(request.getConsultation().getUserId(), storeId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.COUNSELOR_NOT_FOUND));

        validateDuplicatePhone(storeId, request);

        Customer customer = saveCustomerForConsultation(storeId, counselor, service, request);
        applyRegistrationStatus(customer, service, request.getConsultation().getIsRegistered());
        Consultation consultation = saveConsultation(customer, counselor, service, request);
        saveConsultationCreatedTimeline(customer, counselor, service, consultation);

        return ConsultationCreateResponse.builder()
                .consultationId(consultation.getId())
                .customerId(customer.getId())
                .sessionNo(consultation.getSessionNo())
                .redirectUrl(buildConsultationRedirectUrl(customer.getId(), consultation.getId()))
                .build();
    }

    public ConsultationCustomerSearchResponse searchCustomerByPhone(UUID storeId, String phone) {
        if (storeId == null) {
            throw new BusinessException(ConsultationErrorCode.STORE_NOT_ASSIGNED);
        }

        if (!StringUtils.hasText(phone)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return customerRepository.findByPhoneNumAndStoreId(phone, storeId)
                .map(this::toCustomerSearchResponse)
                .orElseGet(ConsultationCustomerSearchResponse::notFound);
    }

    private ConsultationCustomerSearchResponse toCustomerSearchResponse(Customer customer) {
        UUID registeredServiceId = customer.getRegisteredService() != null
                ? customer.getRegisteredService().getId()
                : null;

        return ConsultationCustomerSearchResponse.builder()
                .exists(true)
                .customer(ConsultationCustomerSearchResponse.CustomerInfo.builder()
                        .customerId(customer.getId())
                        .name(customer.getName())
                        .gender(customer.getGender())
                        .birthDate(customer.getBirthDate())
                        .phoneNum(customer.getPhoneNum())
                        .registeredServiceId(registeredServiceId)
                        .status(customer.getStatus())
                        .preferredContactChannel(customer.getPreferredContactChannel())
                        .inflowPath(customer.getInflowPath())
                        .latestConsultAt(customer.getLatestConsultAt())
                        .build())
                .build();
    }

    private Customer saveCustomerForConsultation(
            UUID storeId,
            User counselor,
            Service service,
            ConsultationCreateRequest request
    ) {
        LocalDate consultedDate = request.getConsultation().getConsultedAt().toLocalDate();

        if (request.getCustomerId() == null) {
            Customer customer = Customer.builder()
                    .store(counselor.getStore())
                    .name(request.getCustomer().getName())
                    .gender(request.getCustomer().getGender())
                    .birthDate(request.getCustomer().getBirthDate())
                    .phoneNum(request.getCustomer().getPhoneNum())
                    .preferredContactChannel(request.getCustomer().getPreferredContactChannel())
                    .inflowPath(resolveInflowPath(request.getCustomer().getInflowPath()))
                    .status(resolveInitialStatus(request.getConsultation().getIsRegistered()))
                    .registeredService(request.getConsultation().getIsRegistered() ? service : null)
                    .firstConsultAt(consultedDate)
                    .latestConsultAt(consultedDate)
                    .build();

            return customerRepository.save(customer);
        }

        Customer customer = customerRepository
                .findByIdAndStoreId(request.getCustomerId(), storeId)
                .orElseThrow(() -> new BusinessException(ConsultationErrorCode.CUSTOMER_NOT_FOUND));

        customer.updateBasicInfo(
                request.getCustomer().getName(),
                request.getCustomer().getGender(),
                request.getCustomer().getBirthDate(),
                request.getCustomer().getPhoneNum(),
                request.getCustomer().getPreferredContactChannel(),
                resolveInflowPath(request.getCustomer().getInflowPath()),
                consultedDate
        );

        return customer;
    }

    private void applyRegistrationStatus(Customer customer, Service service, boolean registered) {
        if (registered) {
            customer.markRegistered(service);
            return;
        }

        customer.markUnregistered();
        interestServiceRepository.findByCustomerIdAndServiceId(customer.getId(), service.getId())
                .orElseGet(() -> interestServiceRepository.save(InterestService.builder()
                        .customer(customer)
                        .service(service)
                        .build()));
    }

    private void validateDuplicatePhone(UUID storeId, ConsultationCreateRequest request) {
        String phoneNum = request.getCustomer().getPhoneNum();

        if (request.getCustomerId() == null) {
            if (customerRepository.findByPhoneNumAndStoreId(phoneNum, storeId).isPresent()) {
                throw new BusinessException(ConsultationErrorCode.DUPLICATE_CUSTOMER_PHONE);
            }
            return;
        }

        if (customerRepository.existsByPhoneNumAndStoreIdAndIdNot(phoneNum, storeId, request.getCustomerId())) {
            throw new BusinessException(ConsultationErrorCode.DUPLICATE_CUSTOMER_PHONE);
        }
    }

    private CustomerStatus resolveInitialStatus(boolean registered) {
        return registered ? CustomerStatus.REGISTERED : CustomerStatus.UNREGISTERED;
    }

    private Consultation saveConsultation(
            Customer customer,
            User counselor,
            Service service,
            ConsultationCreateRequest request
    ) {
        int nextSessionNo = consultationRepository.findMaxSessionNoByCustomerId(customer.getId()) + 1;

        Consultation consultation = Consultation.builder()
                .customer(customer)
                .user(counselor)
                .consultedService(service)
                .consultedAt(request.getConsultation().getConsultedAt())
                .sessionNo(nextSessionNo)
                .stage(ConsultationStage.CONSULTATION)
                .sourceType(ConsultationSourceType.DIRECT)
                .rawText(request.getConsultation().getRawText())
                .build();

        return consultationRepository.save(consultation);
    }

    private void saveConsultationCreatedTimeline(
            Customer customer,
            User counselor,
            Service service,
            Consultation consultation
    ) {
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("consultationId", consultation.getId());
        afterValue.put("sessionNo", consultation.getSessionNo());
        afterValue.put("stage", consultation.getStage());
        afterValue.put("consultedServiceId", service.getId());
        afterValue.put("customerStatus", customer.getStatus());

        CustomerActivityTimeline timeline = CustomerActivityTimeline.builder()
                .store(counselor.getStore())
                .customer(customer)
                .actorUser(counselor)
                .activityType(CustomerActivityType.CONSULTATION_CREATED)
                .title("상담 기록 등록")
                .description("고객의 상담 기록이 등록되었습니다.")
                .relatedType(ActivityRelatedType.CONSULTATION)
                .relatedId(consultation.getId())
                .afterValue(afterValue)
                .occurredAt(OffsetDateTime.now())
                .build();

        customerActivityTimelineRepository.save(timeline);
    }

    private String buildConsultationRedirectUrl(UUID customerId, UUID consultationId) {
        return "/customers/" + customerId + "/consultations/" + consultationId;
    }

    private InflowPath resolveInflowPath(InflowPath inflowPath) {
        return inflowPath != null ? inflowPath : InflowPath.OTHER;
    }
}
