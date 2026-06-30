package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.dto.response.ConsultationCustomerSearchResponse;
import com.fitback.domain.consultation.dto.response.ConsultationNewResponse;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.repository.CustomerRepository;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import com.fitback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultationService {

    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

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
                .findAllByStoreId(storeId)
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
}
