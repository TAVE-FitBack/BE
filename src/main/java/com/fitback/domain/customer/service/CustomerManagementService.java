package com.fitback.domain.customer.service;

import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.customer.dto.request.ConsultationListQuery;
import com.fitback.domain.customer.dto.request.InquiryListQuery;
import com.fitback.domain.customer.dto.response.CustomerManagementConsultationListResponse;
import com.fitback.domain.customer.dto.response.CustomerManagementInquiryListResponse;
import com.fitback.domain.customer.dto.response.CustomerManagementSummaryResponse;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.exception.CustomerManagementErrorCode;
import com.fitback.domain.customer.repository.InflowPathOptionRepository;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ConsultationPageRows;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ConsultationRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ConsultationSearchCondition;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.InflowPathCountRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.InquiryPageRows;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.InquiryRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.InquirySearchCondition;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.NonConversionReasonRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ServiceCountRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.SummaryCounts;
import com.fitback.domain.customer.support.CustomerManagementQueryValidator;
import com.fitback.domain.customer.support.CustomerManagementQueryValidator.MonthRange;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.inquiry.enums.InquiryStatus;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerManagementService {

    private static final int INQUIRY_MEMO_MAX_LENGTH = 100;
    private static final Set<String> LEAD_TEMPERATURES =
            Set.of("HOT", "WARM", "HOLD", "COLD", "LOST");
    private static final Map<String, String> REASON_DISPLAY_NAMES = Map.of(
            "PRICE_BURDEN", "이용료 부담",
            "SCHEDULE_CONFLICT", "일정 문제"
    );

    private final CustomerManagementQueryRepository queryRepository;
    private final ServiceRepository serviceRepository;
    private final InflowPathOptionRepository inflowPathOptionRepository;
    private final UserRepository userRepository;

    public CustomerManagementSummaryResponse getSummary(UUID storeId, String month) {
        if (storeId == null) {
            throw new BusinessException(CustomerManagementErrorCode.STORE_NOT_ASSIGNED);
        }

        MonthRange range = CustomerManagementQueryValidator.resolveMonthRange(month);
        SummaryCounts counts = queryRepository.findSummaryCounts(storeId, range);

        List<CustomerManagementSummaryResponse.ServiceConsultationRate> serviceRates =
                queryRepository.findServiceCounts(storeId, range).stream()
                        .map(row -> toServiceRate(row, counts.consultedCustomerCount()))
                        .toList();

        List<CustomerManagementSummaryResponse.InflowPathRate> inflowPathRates =
                queryRepository.findInflowPathCounts(storeId, range).stream()
                        .map(row -> toInflowPathRate(row, counts.consultedCustomerCount()))
                        .toList();

        return CustomerManagementSummaryResponse.builder()
                .month(range.month().toString())
                .registrationRate(calculateRate(
                        counts.registeredConsultedCustomerCount(),
                        counts.consultedCustomerCount()
                ))
                .newConsultationCount(counts.newConsultationCount())
                .newRegistrationCount(counts.newRegistrationCount())
                .nonRegisteredCount(counts.nonRegisteredCount())
                .serviceConsultationRates(serviceRates)
                .inflowPathRates(inflowPathRates)
                .build();
    }

    public CustomerManagementConsultationListResponse getConsultations(
            UUID storeId,
            ConsultationListQuery query
    ) {
        validateStoreId(storeId);
        CustomerManagementQueryValidator.validatePage(query.getPage(), query.getSize());
        MonthRange range = CustomerManagementQueryValidator.resolveMonthRange(query.getMonth());
        ConsultationSearchCondition condition = buildConsultationCondition(storeId, query);

        ConsultationPageRows pageRows = queryRepository.findConsultations(
                storeId,
                range,
                condition,
                query.getPage(),
                query.getSize()
        );

        Map<UUID, List<CustomerManagementConsultationListResponse.NonConversionReasonInfo>> reasonsByCustomer =
                findReasonsByCustomer(pageRows.content());
        List<CustomerManagementConsultationListResponse.ConsultationItem> content = pageRows.content().stream()
                .map(row -> toConsultationItem(
                        row,
                        reasonsByCustomer.getOrDefault(row.customerId(), List.of())
                ))
                .toList();

        int totalPages = pageRows.totalElements() == 0
                ? 0
                : (int) Math.ceil((double) pageRows.totalElements() / query.getSize());

        return CustomerManagementConsultationListResponse.builder()
                .content(content)
                .page(query.getPage())
                .size(query.getSize())
                .totalElements(pageRows.totalElements())
                .totalPages(totalPages)
                .hasNext(query.getPage() + 1 < totalPages)
                .build();
    }

    public CustomerManagementInquiryListResponse getInquiries(
            UUID storeId,
            InquiryListQuery query
    ) {
        validateStoreId(storeId);
        CustomerManagementQueryValidator.validatePage(query.getPage(), query.getSize());
        MonthRange range = CustomerManagementQueryValidator.resolveMonthRange(query.getMonth());
        InquirySearchCondition condition = buildInquiryCondition(storeId, query);

        InquiryPageRows pageRows = queryRepository.findInquiries(
                storeId,
                range,
                condition,
                query.getPage(),
                query.getSize()
        );
        List<CustomerManagementInquiryListResponse.InquiryItem> content = pageRows.content().stream()
                .map(this::toInquiryItem)
                .toList();
        int totalPages = pageRows.totalElements() == 0
                ? 0
                : (int) Math.ceil((double) pageRows.totalElements() / query.getSize());

        return CustomerManagementInquiryListResponse.builder()
                .content(content)
                .page(query.getPage())
                .size(query.getSize())
                .totalElements(pageRows.totalElements())
                .totalPages(totalPages)
                .hasNext(query.getPage() + 1 < totalPages)
                .build();
    }

    private ConsultationSearchCondition buildConsultationCondition(
            UUID storeId,
            ConsultationListQuery query
    ) {
        String keyword = CustomerManagementQueryValidator.normalizeKeyword(query.getKeyword());
        String gender = validateEnumFilter(query.getGender(), Gender.class);
        String stage = validateEnumFilter(query.getStage(), ConsultationStage.class);
        String status = validateEnumFilter(query.getStatus(), CustomerStatus.class);
        String leadTemperature = normalizeUppercase(query.getLeadTemperature());
        if (leadTemperature != null && !LEAD_TEMPERATURES.contains(leadTemperature)) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);
        }
        String reasonType = normalizeUppercase(query.getReasonType());
        if (reasonType != null && !reasonType.matches("[A-Z][A-Z0-9_]{0,49}")) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);
        }

        validateFilterReferences(storeId, query);
        return new ConsultationSearchCondition(
                keyword,
                gender,
                query.getServiceId(),
                query.getInflowPathId(),
                stage,
                reasonType,
                status,
                leadTemperature,
                query.getCounselorId()
        );
    }

    private InquirySearchCondition buildInquiryCondition(UUID storeId, InquiryListQuery query) {
        String keyword = CustomerManagementQueryValidator.normalizeKeyword(query.getKeyword());
        String gender = validateEnumFilter(query.getGender(), Gender.class);
        String inquiryStatus = validateEnumFilter(query.getInquiryStatus(), InquiryStatus.class);
        if (InquiryStatus.CONVERTED.name().equals(inquiryStatus)) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);
        }

        validateInquiryFilterReferences(storeId, query);
        return new InquirySearchCondition(
                keyword,
                gender,
                query.getServiceId(),
                query.getInflowPathId(),
                inquiryStatus,
                query.getCounselorId()
        );
    }

    private void validateFilterReferences(UUID storeId, ConsultationListQuery query) {
        if (query.getServiceId() != null
                && serviceRepository.findByIdAndStoreIdAndActiveTrue(query.getServiceId(), storeId).isEmpty()) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);
        }
        if (query.getInflowPathId() != null
                && inflowPathOptionRepository
                .findByIdAndStoreIdAndActiveTrue(query.getInflowPathId(), storeId)
                .isEmpty()) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);
        }
        if (query.getCounselorId() != null
                && userRepository.findByIdAndStore_Id(query.getCounselorId(), storeId).isEmpty()) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);
        }
    }

    private void validateInquiryFilterReferences(UUID storeId, InquiryListQuery query) {
        if (query.getServiceId() != null
                && serviceRepository.findByIdAndStoreIdAndActiveTrue(query.getServiceId(), storeId).isEmpty()) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);
        }
        if (query.getInflowPathId() != null
                && inflowPathOptionRepository
                .findByIdAndStoreIdAndActiveTrue(query.getInflowPathId(), storeId)
                .isEmpty()) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);
        }
        if (query.getCounselorId() != null
                && userRepository.findByIdAndStore_Id(query.getCounselorId(), storeId).isEmpty()) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);
        }
    }

    private <E extends Enum<E>> String validateEnumFilter(String value, Class<E> enumClass) {
        String normalized = normalizeUppercase(value);
        if (normalized == null) {
            return null;
        }
        boolean valid = Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .anyMatch(normalized::equals);
        if (!valid) {
            throw new BusinessException(CustomerManagementErrorCode.INVALID_FILTER_CONDITION);
        }
        return normalized;
    }

    private String normalizeUppercase(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Map<UUID, List<CustomerManagementConsultationListResponse.NonConversionReasonInfo>>
    findReasonsByCustomer(List<ConsultationRow> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> customerIds = rows.stream()
                .map(ConsultationRow::customerId)
                .collect(Collectors.toSet());
        return queryRepository.findNonConversionReasons(customerIds).stream()
                .collect(Collectors.groupingBy(
                        NonConversionReasonRow::customerId,
                        Collectors.mapping(
                                this::toReasonInfo,
                                Collectors.toList()
                        )
                ));
    }

    private CustomerManagementConsultationListResponse.ConsultationItem toConsultationItem(
            ConsultationRow row,
            List<CustomerManagementConsultationListResponse.NonConversionReasonInfo> reasons
    ) {
        return CustomerManagementConsultationListResponse.ConsultationItem.builder()
                .customerId(row.customerId())
                .name(row.name())
                .phoneNum(row.phoneNum())
                .gender(Gender.valueOf(row.gender()))
                .birthDate(row.birthDate())
                .serviceId(row.serviceId())
                .serviceName(row.serviceName())
                .inflowPathId(row.inflowPathId())
                .inflowPathName(row.inflowPathName())
                .managementStage(ConsultationStage.valueOf(row.stage()))
                .latestMemo(row.summary())
                .nonConversionReasons(reasons)
                .leadTemperature(row.leadTemperature())
                .customerStatus(CustomerStatus.valueOf(row.customerStatus()))
                .latestConsultAt(row.latestConsultAt())
                .counselorId(row.counselorId())
                .counselorName(row.counselorName())
                .build();
    }

    private CustomerManagementConsultationListResponse.NonConversionReasonInfo toReasonInfo(
            NonConversionReasonRow row
    ) {
        return CustomerManagementConsultationListResponse.NonConversionReasonInfo.builder()
                .reasonType(row.reasonType())
                .displayName(REASON_DISPLAY_NAMES.getOrDefault(row.reasonType(), row.reasonType()))
                .build();
    }

    private CustomerManagementInquiryListResponse.InquiryItem toInquiryItem(InquiryRow row) {
        InquiryStatus status = InquiryStatus.valueOf(row.inquiryStatus());
        boolean converted = row.convertedCustomerId() != null || row.convertedConsultationId() != null;
        return CustomerManagementInquiryListResponse.InquiryItem.builder()
                .inquiryId(row.inquiryId())
                .name(row.name())
                .phoneNum(row.phoneNum())
                .gender(Gender.valueOf(row.gender()))
                .birthDate(row.birthDate())
                .serviceId(row.serviceId())
                .serviceName(row.serviceName())
                .inflowPathId(row.inflowPathId())
                .inflowPathName(row.inflowPathName())
                .memo(toInquiryMemo(row.rawText()))
                .inquiryStatus(status)
                .inquiryStatusName(status.getLabel())
                .inquiredAt(row.inquiredAt())
                .visitScheduledAt(row.visitScheduledAt())
                .counselorId(row.counselorId())
                .counselorName(row.counselorName())
                .converted(converted)
                .convertedCustomerId(row.convertedCustomerId())
                .convertedConsultationId(row.convertedConsultationId())
                .build();
    }

    private String toInquiryMemo(String rawText) {
        int codePointCount = rawText.codePointCount(0, rawText.length());
        if (codePointCount <= INQUIRY_MEMO_MAX_LENGTH) {
            return rawText;
        }
        int endIndex = rawText.offsetByCodePoints(0, INQUIRY_MEMO_MAX_LENGTH);
        return rawText.substring(0, endIndex) + "…";
    }

    private void validateStoreId(UUID storeId) {
        if (storeId == null) {
            throw new BusinessException(CustomerManagementErrorCode.STORE_NOT_ASSIGNED);
        }
    }

    private CustomerManagementSummaryResponse.ServiceConsultationRate toServiceRate(
            ServiceCountRow row,
            long totalCustomerCount
    ) {
        return CustomerManagementSummaryResponse.ServiceConsultationRate.builder()
                .serviceId(row.serviceId())
                .serviceName(row.serviceName())
                .count(row.customerCount())
                .rate(calculateRate(row.customerCount(), totalCustomerCount))
                .build();
    }

    private CustomerManagementSummaryResponse.InflowPathRate toInflowPathRate(
            InflowPathCountRow row,
            long totalCustomerCount
    ) {
        return CustomerManagementSummaryResponse.InflowPathRate.builder()
                .inflowPathId(row.inflowPathId())
                .inflowPathName(row.inflowPathName())
                .count(row.customerCount())
                .rate(calculateRate(row.customerCount(), totalCustomerCount))
                .build();
    }

    private int calculateRate(long count, long totalCount) {
        if (totalCount == 0) {
            return 0;
        }
        return (int) Math.round(count * 100.0 / totalCount);
    }
}
