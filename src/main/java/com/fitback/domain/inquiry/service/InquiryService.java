package com.fitback.domain.inquiry.service;

import com.fitback.domain.customer.repository.InflowPathOptionRepository;
import com.fitback.domain.inquiry.client.AiInquiryClient;
import com.fitback.domain.inquiry.dto.request.AiInquiryCheckPreviewRequest;
import com.fitback.domain.inquiry.dto.request.InquiryCheckPreviewRequest;
import com.fitback.domain.inquiry.dto.response.InquiryNewResponse;
import com.fitback.domain.inquiry.dto.response.InquiryStatusInfo;
import com.fitback.domain.inquiry.enums.InquiryStatus;
import com.fitback.domain.inquiry.exception.InquiryErrorCode;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import com.fitback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private final ServiceRepository serviceRepository;
    private final InflowPathOptionRepository inflowPathOptionRepository;
    private final UserRepository userRepository;
    private final AiInquiryClient aiInquiryClient;

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
}
