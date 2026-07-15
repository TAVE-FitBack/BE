package com.fitback.domain.analysisreport.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AnalysisReportTotalResponse {

    private String month;
    private Summary summary;
    private List<ServiceConsultation> serviceConsultations;
    private RegistrationTrend registrationTrend;
    private List<InflowPath> inflowPaths;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Summary {
        private int newRegistrationRate;
        private long newConsultationCount;
        private long newRegistrationCount;
        private long nonRegisteredCount;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ServiceConsultation {
        private UUID serviceId;
        private String serviceName;
        private long consultedCustomerCount;
        private long totalNewConsultationCount;
        private int consultationRate;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class RegistrationTrend {
        private List<String> months;
        private int selectedMonthRate;
        private int previousMonthRate;
        private int monthOverMonthChangePoint;
        private String direction;
        private List<Series> series;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Series {
        private String key;
        private String name;
        private List<Point> points;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Point {
        private String month;
        private int rate;
        private long consultedCount;
        private long registeredCount;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class InflowPath {
        private UUID inflowPathId;
        private String inflowPathName;
        private int newConsultationCompositionRate;
        private int registeredCompositionRate;
        private long newConsultationCount;
        private long registeredCount;
    }
}
