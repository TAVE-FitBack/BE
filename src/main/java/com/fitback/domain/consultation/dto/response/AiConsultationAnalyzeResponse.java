package com.fitback.domain.consultation.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiConsultationAnalyzeResponse {

    private String summary;
    private CustomerInsight customerInsight;
    private List<NonConversionReason> nonConversionReasons;
    private NextBestAction nextBestAction;
    private FollowUp followUp;
    private FollowUpInsight followUpInsight;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomerInsight {
        private String leadTemperature;
        private String temperatureBasis;
        private Integer priorityScore;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NonConversionReason {
        private String reasonType;
        private String role;
        private String reasonBasis;
        private String confidence;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NextBestAction {
        private String title;
        private String description;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FollowUp {
        private LocalDate recommendContactDate;
        private String memo;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FollowUpInsight {
        private Map<String, Object> persuasionPoint;
        private String cautionNote;
        private Map<String, Object> actionBasis;
    }
}
