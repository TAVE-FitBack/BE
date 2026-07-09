package com.fitback.domain.customer.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CustomerManagementSummaryResponse {

    private String month;
    private int registrationRate;
    private long newConsultationCount;
    private long newRegistrationCount;
    private long nonRegisteredCount;
    private List<ServiceConsultationRate> serviceConsultationRates;
    private List<InflowPathRate> inflowPathRates;

    @Getter
    @Builder
    public static class ServiceConsultationRate {
        private UUID serviceId;
        private String serviceName;
        private long count;
        private int rate;
    }

    @Getter
    @Builder
    public static class InflowPathRate {
        private UUID inflowPathId;
        private String inflowPathName;
        private long count;
        private int rate;
    }
}
