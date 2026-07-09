package com.fitback.domain.customer.service;

import com.fitback.domain.customer.dto.response.CustomerManagementSummaryResponse;
import com.fitback.domain.customer.exception.CustomerManagementErrorCode;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.InflowPathCountRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.ServiceCountRow;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.SummaryCounts;
import com.fitback.domain.customer.support.CustomerManagementQueryValidator;
import com.fitback.domain.customer.support.CustomerManagementQueryValidator.MonthRange;
import com.fitback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerManagementService {

    private final CustomerManagementQueryRepository queryRepository;

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
