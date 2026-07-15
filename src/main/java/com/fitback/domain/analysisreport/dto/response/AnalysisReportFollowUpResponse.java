package com.fitback.domain.analysisreport.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class AnalysisReportFollowUpResponse {

    private String month;
    private Summary summary;
    private RegistrationChange registrationChange;
    private ConversionGraph conversionGraph;
    private List<NonConversionReasonItem> nonConversionReasons;
    private List<AiRecommendation> aiRecommendations;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Summary {
        private long targetCustomerCount;
        private long pendingFollowUpCount;
        private long completedFollowUpCount;
        private int followUpRegistrationConversionRate;
        private long registeredAfterFollowUpCount;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class RegistrationChange {
        private int beforeRate;
        private int currentRate;
        private int changePoint;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ConversionGraph {
        private long initialNonRegisteredCount;
        private List<ConversionRound> rounds;
        private long finalRegisteredCount;
        private long nonRegisteredOrLostCount;
        private long unattributedRegisteredCount;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ConversionRound {
        private int contactRound;
        private long registeredCount;
        private long sentCount;
        private long pendingCount;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class NonConversionReasonItem {
        private String reasonType;
        private String displayName;
        private long count;
        private int rate;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class AiRecommendation {
        private String title;
        private String description;
    }
}
