package com.fitback.domain.consultation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ConsultationNewResponse {

    private List<ServiceInfo> services;
    private List<CounselorInfo> counselors;

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
    public static class CounselorInfo {
        private UUID userId;
        private String name;
    }
}
