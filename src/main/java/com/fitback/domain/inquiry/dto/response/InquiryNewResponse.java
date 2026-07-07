package com.fitback.domain.inquiry.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class InquiryNewResponse {

    private List<ServiceInfo> services;
    private List<InflowPathInfo> inflowPaths;
    private List<CounselorInfo> counselors;
    private List<InquiryStatusInfo> inquiryStatuses;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ServiceInfo {
        private UUID serviceId;
        private String name;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class InflowPathInfo {
        private UUID inflowPathId;
        private String name;
        private int displayOrder;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CounselorInfo {
        private UUID userId;
        private String name;
    }
}
